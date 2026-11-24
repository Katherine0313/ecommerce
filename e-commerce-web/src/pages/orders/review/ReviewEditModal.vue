<template>
  <a-modal
      :visible="visible"
      :title="isEdit ? '编辑虚拟评价' : '新增虚拟评价'"
      :confirm-loading="submitting"
      ok-text="保存"
      cancel-text="取消"
      :width="620"
      @update:visible="(v) => emit('update:visible', v)"
      @ok="handleSubmit"
  >
    <a-form layout="vertical">
      <a-form-item label="商品" required>
        <!-- 搜索选择商品：商品名/图由后端从商品表取，前端只传 productId -->
        <a-select
            v-model:value="form.productId"
            show-search
            :filter-option="false"
            placeholder="输入商品名搜索并选择"
            :loading="productLoading"
            @search="searchProduct"
            @focus="searchProduct('')"
        >
          <a-select-option v-for="item in productOptions" :key="item.id" :value="item.id">
            {{ item.title || item.name }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item label="评分" required>
        <a-rate v-model:value="form.rating" />
      </a-form-item>

      <a-form-item label="评价标签">
        <div class="tag-grid">
          <span
              v-for="tag in REVIEW_TAGS"
              :key="tag.value"
              class="tag-item"
              :class="{
                'tag-item--active': form.tags.includes(tag.value),
                'tag-item--negative': tag.negative,
                'tag-item--disabled': !form.tags.includes(tag.value) && form.tags.length >= MAX_TAGS,
              }"
              @click="toggleTag(tag.value)"
          >
            {{ tag.value }}
          </span>
        </div>
        <div class="tip">最多选 {{ MAX_TAGS }} 个，已选 {{ form.tags.length }}/{{ MAX_TAGS }}</div>
      </a-form-item>

      <a-form-item label="评价内容" required>
        <a-textarea v-model:value="form.content" :rows="4" :maxlength="500" show-count
                    placeholder="请输入评价内容（至少 5 个字）" />
      </a-form-item>

      <a-form-item label="评价图片">
        <a-upload
            :action="uploadAction"
            :headers="uploadHeaders"
            :before-upload="beforeUpload"
            v-model:file-list="fileList"
            accept="image/*"
            list-type="picture-card"
            @change="handleUploadChange"
        >
          <div v-if="fileList.length < 6">
            <UploadOutlined />
            <div class="ant-upload-text">上传</div>
          </div>
        </a-upload>
        <div class="tip">最多 6 张，建议 {{ specLabel(reviewSpec) }}（上传前已自动压缩）</div>
      </a-form-item>

      <a-alert type="warning" show-icon
               message="虚拟评价不挂真实订单，前台会带「系统评价」标记；买家真实评价不可编辑。"
      />
    </a-form>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import { getToken } from '@/utils/token'
import { compressImage } from '@/utils/compressImage'
import { getImageSpec, specLabel } from '@/utils/image-spec'
import { getGoodsList } from '@/api/products.js'
import { addVirtualReview, getReviewDetail, updateVirtualReview } from '@/api/review.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  // 有 id 是编辑，没有是新增
  reviewId: { type: Number, default: null },
})
const emit = defineEmits(['update:visible', 'success'])

const submitting = ref(false)
const productLoading = ref(false)
const productOptions = ref([])
const fileList = ref([])

const form = ref({
  productId: undefined,
  rating: 5,
  tags: [],
  content: '',
})

// 评价晒图规格：缩略图很小，但可点开看大图
const reviewSpec = getImageSpec('review')
const uploadAction = import.meta.env.VITE_API_BASE_URL + '/api/admin/upload?type=review'
const uploadHeaders = { Authorization: 'Bearer ' + getToken() }
const beforeUpload = (file) => compressImage(file, reviewSpec)

const isEdit = computed(() => !!props.reviewId)

// 固定 8 个评价标签（与后端 ReviewTagEnum 一致），正向 4 个 + 负向 4 个，全部平铺展示
const MAX_TAGS = 4
const REVIEW_TAGS = [
  { value: '质量很好' },
  { value: '性价比高' },
  { value: '物流很快' },
  { value: '服务周到' },
  { value: '有待改进', negative: true },
  { value: '物流慢', negative: true },
  { value: '包装破损', negative: true },
  { value: '与描述不符', negative: true },
]
const TAG_VALUES = REVIEW_TAGS.map((t) => t.value)

/** 点一下选中，再点一下取消；已选满 4 个时未选中的标签置灰不可点 */
function toggleTag(value) {
  const tags = form.value.tags
  const idx = tags.indexOf(value)
  if (idx > -1) {
    tags.splice(idx, 1)
    return
  }
  if (tags.length >= MAX_TAGS) {
    message.warning(`最多选择 ${MAX_TAGS} 个标签`)
    return
  }
  tags.push(value)
}

/** 搜索商品：下拉里只显示 id，前端不传商品名 */
function searchProduct(keyword) {
  productLoading.value = true
  getGoodsList({ search: keyword || undefined, page: 1, pageSize: 20 })
      .then((res) => {
        productOptions.value = (res.data.data.list || []).map((p) => ({
          id: p.id,
          name: p.name,
          title: p.title,
        }))
      })
      .finally(() => {
        productLoading.value = false
      })
}

/** 上传完成：把返回的相对路径记下来 */
function handleUploadChange(info) {
  if (info.file.status === 'done') {
    info.file.url = info.file.response.data.url
  }
}

function resetForm() {
  form.value = { productId: undefined, rating: 5, tags: [], content: '' }
  fileList.value = []
}

/** 打开时：新增清空，编辑回填 */
watch(() => props.visible, (val) => {
  if (!val) {
    resetForm()
    return
  }
  if (props.reviewId) {
    getReviewDetail(props.reviewId).then((res) => {
      const data = res.data.data
      // 标签统一用多值字段 tags
      const rawTags = Array.isArray(data.tags) ? data.tags : []
      form.value = {
        productId: data.productId,
        rating: data.rating || 5,
        tags: rawTags.filter((t) => TAG_VALUES.includes(t)).slice(0, MAX_TAGS),
        content: data.content || '',
      }
      // 图片回填成 a-upload 的 fileList 结构
      fileList.value = parseImages(data.images).map((item) => ({
        uid: item.url,
        name: item.url.split('/').pop(),
        status: 'done',
        url: imgUrl(item.url),
        response: { data: { url: item.url } },
      }))
      // 编辑时把当前商品也放进下拉，否则选中项显示不出名字
      if (data.productId) {
        productOptions.value = [
          { id: data.productId, name: data.productName, title: data.productName },
          ...productOptions.value.filter((p) => p.id !== data.productId),
        ]
      }
    })
  } else {
    resetForm()
    searchProduct('')
  }
})

function handleSubmit() {
  if (!form.value.productId) {
    message.error('请选择商品')
    return
  }
  if (!form.value.rating || form.value.rating < 1 || form.value.rating > 5) {
    message.error('请选择 1~5 星评分')
    return
  }
  if (!form.value.content || form.value.content.trim().length < 5) {
    message.error('评价内容至少 5 个字')
    return
  }
  const images = fileList.value
      .filter((f) => f.status === 'done' && f.response && f.response.data && f.response.data.url)
      .map((f) => ({ url: f.response.data.url, type: 'image' }))
  const payload = {
    productId: form.value.productId,
    rating: form.value.rating,
    tags: form.value.tags.length ? form.value.tags : null,
    content: form.value.content.trim(),
    images: images.length ? JSON.stringify(images) : null,
  }
  submitting.value = true
  const req = props.reviewId
    ? updateVirtualReview(props.reviewId, payload)
    : addVirtualReview(payload)
  req.then(() => {
    message.success(props.reviewId ? '保存成功' : '新增成功')
    emit('update:visible', false)
    emit('success')
  }).finally(() => {
    submitting.value = false
  })
}

/** 解析媒体 JSON：[{"url":"...","type":"image"}] */
function parseImages(json) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.filter((m) => m && m.url) : []
  } catch (e) {
    return []
  }
}

function imgUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : (import.meta.env.VITE_API_BASE_URL || '') + path
}
</script>

<style scoped>
.tip {
  margin-top: 6px;
  font-size: 12px;
  color: #999;
}

/* 标签平铺：两行四列，小程序/后台都能一屏放得下 */
.tag-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.tag-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 8px;
  font-size: 13px;
  color: #555;
  background: #fafafa;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
  white-space: nowrap;
}

.tag-item:hover {
  border-color: #ff6700;
  color: #ff6700;
}

/* 负向标签默认淡红，选中后仍然高亮为橙色，避免用户在列表页看着像差评 */
.tag-item--negative {
  color: #a05a5a;
  background: #fff5f5;
  border-color: #f0d8d8;
}

.tag-item--active {
  color: #fff;
  background: #ff6700;
  border-color: #ff6700;
}

.tag-item--active:hover {
  color: #fff;
}

.tag-item--disabled {
  color: #bbb;
  background: #f5f5f5;
  border-color: #eee;
  cursor: not-allowed;
}

@media (max-width: 575px) {
  .tag-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
