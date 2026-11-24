<template>
  <div>
    <a-tabs v-model:activeKey="tab">
      <!-- ==================== 文案 Tab ==================== -->
      <a-tab-pane key="copy" tab="文案">
        <a-card title="商品文案助手" :bordered="false">
          <a-form layout="inline">
            <a-form-item label="商品 ID（选填）">
              <a-input-number
                  v-model:value="productId"
                  :min="1"
                  placeholder="已有商品填 ID"
                  style="width: 160px"
              />
            </a-form-item>
            <a-form-item>
              <a-button :loading="productLoading" :disabled="!productId" @click="loadProduct">查询商品</a-button>
            </a-form-item>
            <a-form-item v-if="product" label="当前商品">
              <a-tag color="blue">{{ product.name }}</a-tag>
            </a-form-item>
          </a-form>

          <!-- 无商品 ID：内联模式（新增商品 / 临时写文案），不查库 -->
          <a-form v-if="!productId" layout="inline" style="margin-top: 12px">
            <a-form-item label="商品名称" required>
              <a-input v-model:value="inlineProduct.name" placeholder="先给个大致名称，AI 再润色" style="width: 220px" allow-clear />
            </a-form-item>
            <a-form-item label="分类">
              <a-input v-model:value="inlineProduct.categoryName" placeholder="选填" style="width: 140px" allow-clear />
            </a-form-item>
            <a-form-item label="卖点">
              <a-input v-model:value="inlineProduct.sellingPoints" placeholder="选填，逗号分隔" style="width: 240px" allow-clear />
            </a-form-item>
          </a-form>

          <a-form layout="inline" style="margin-top: 16px">
            <a-form-item label="生成场景">
              <a-select v-model:value="scene" style="width: 140px">
                <a-select-option value="all">全部</a-select-option>
                <a-select-option value="name">名称</a-select-option>
                <a-select-option value="subTitle">副标题</a-select-option>
                <a-select-option value="title">主标题</a-select-option>
                <a-select-option value="description">详情</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="额外要求" style="flex: 1">
              <a-input
                  v-model:value="requirement"
                  placeholder="选填，如：突出性价比、语气活泼"
                  allow-clear
                  style="width: 320px"
              />
            </a-form-item>
            <a-form-item>
              <a-button
                  type="primary"
                  v-permission="'ai:copy:generate'"
                  :loading="generating"
                  @click="generate"
              >
                生成文案
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>

        <!-- 生成结果 -->
        <a-card v-if="result" :bordered="false" style="margin-top: 16px">
          <template #title>
            生成结果<span v-if="result.productName" style="margin-left: 8px; color: #8c8c8c; font-weight: normal">
              {{ result.productName }}
            </span>
          </template>
          <template #extra>
            <a-button
                type="primary"
                v-permission="'ai:copy:generate'"
                :disabled="!editTargetId"
                @click="goEditProduct"
            >
              回填到商品
            </a-button>
          </template>
          <div class="result-list">
            <a-card v-for="f in resultFields" :key="f.key" class="result-card">
              <template #title>{{ f.label }}</template>
              <template #extra>
                <a-button size="small" @click="copyField(f)">复制</a-button>
              </template>
              <div class="field-compare">
                <div class="field-block">
                  <div class="field-label">当前值</div>
                  <div class="field-value">{{ currentValue(f.key) || '（空）' }}</div>
                </div>
                <div class="field-block">
                  <div class="field-label">生成值</div>
                  <div class="field-value generated">{{ f.value }}</div>
                </div>
              </div>
            </a-card>
          </div>
          <div class="result-tip">
            <template v-if="editTargetId">
              点击「回填到商品」将跳转到商品列表并打开该商品的编辑弹窗，由你在编辑页确认后保存。
            </template>
            <template v-else>
              内联模式（无商品 ID）只能复制文案；给已有商品回填请先输入商品 ID。
            </template>
          </div>
        </a-card>
      </a-tab-pane>

      <!-- ==================== 图片 Tab ==================== -->
      <a-tab-pane key="image" tab="图片">
        <a-card title="AI 生图" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="画面描述">
              <a-textarea
                  v-model:value="imgPrompt"
                  :rows="3"
                  placeholder="如：白色背景，俯视角度，柔和光线，突出质感。留空则按上方商品信息自动拼"
                  allow-clear
              />
            </a-form-item>
            <a-form layout="inline">
              <a-form-item label="场景">
                <a-select v-model:value="imgScene" style="width: 140px">
                  <a-select-option value="main">商品主图</a-select-option>
                  <a-select-option value="poster">营销海报</a-select-option>
                  <a-select-option value="banner">Banner</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="模式">
                <a-radio-group v-model:value="imgModel">
                  <a-radio-button value="">默认（Kolors）</a-radio-button>
                  <a-radio-button value="fast">快速</a-radio-button>
                </a-radio-group>
              </a-form-item>
              <a-form-item>
                <a-button
                    type="primary"
                    v-permission="'ai:copy:generate'"
                    :loading="imgGenerating"
                    @click="generateImg"
                >
                  生成图片
                </a-button>
              </a-form-item>
            </a-form>
          </a-form>

          <div v-if="imgResult.length" class="img-grid">
            <a-card v-for="(img, i) in imgResult" :key="i" class="img-card">
              <img :src="fullUrl(img.url)" class="img-preview" alt="AI 生成图" />
              <div class="img-actions">
                <a-button
                    size="small"
                    type="primary"
                    :disabled="!productId"
                    :loading="applyingUrl === img.url"
                    @click="applyAsMain(img.url)"
                >
                  设为主图
                </a-button>
                <a-button size="small" :href="fullUrl(img.url)" target="_blank" download>下载</a-button>
                <a-button size="small" :loading="imgGenerating" @click="generateImg">重新生成</a-button>
              </div>
            </a-card>
          </div>
          <div v-if="imgResult.length && !productId" class="result-tip">
            未填商品 ID，「设为主图」不可用；可下载后手动上传，或在添加商品页内嵌生图。
          </div>
        </a-card>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { generateCopy, generateImage, applyCopy } from '@/api/ai.js'
import { getGoodsDetail } from '@/api/products.js'

const router = useRouter()
const imageHost = import.meta.env.VITE_API_BASE_URL

const tab = ref('copy')
const productId = ref(null)
const product = ref(null)
const productLoading = ref(false)
const scene = ref('all')
const requirement = ref('')
const generating = ref(false)
const result = ref(null)
// 内联模式（无商品 ID）：新增商品/临时文案
const inlineProduct = ref({ name: '', categoryName: '', sellingPoints: '' })

// 后端 fields 的 key → 展示名 + 商品当前值字段（详情文案存在商品的 body 字段）
const FIELD_DEFS = [
  { key: 'name', label: '名称', productField: 'name' },
  { key: 'subTitle', label: '副标题', productField: 'subTitle' },
  { key: 'title', label: '主标题', productField: 'title' },
  { key: 'description', label: '详情', productField: 'body' },
]

const resultFields = computed(() => {
  if (!result.value?.fields) return []
  return FIELD_DEFS
      .filter(f => result.value.fields[f.key] != null && result.value.fields[f.key] !== '')
      .map(f => ({ ...f, value: result.value.fields[f.key] }))
})

// 回填目标：生成结果带的 productId 优先，其次输入框里的 ID
const editTargetId = computed(() => result.value?.productId || productId.value || null)

const currentValue = (fieldKey) => {
  const def = FIELD_DEFS.find(f => f.key === fieldKey)
  if (!def || !product.value) return ''
  return product.value[def.productField] || ''
}

// 查询商品回显名称（同时拿当前值用于对比）
const loadProduct = () => {
  if (!productId.value) {
    message.warning('请先输入商品 ID')
    return
  }
  productLoading.value = true
  product.value = null
  getGoodsDetail(productId.value)
      .then(res => {
        const data = res.data?.data
        if (!data) {
          message.warning('未找到该商品')
          return
        }
        product.value = data
      })
      .catch(() => {})
      .finally(() => { productLoading.value = false })
}

const generate = () => {
  const payload = {
    scene: scene.value,
    requirement: requirement.value || undefined,
  }
  if (productId.value) {
    payload.productId = productId.value
  } else {
    if (!inlineProduct.value.name.trim()) {
      message.warning('内联模式请先填商品名称（大致名称即可）')
      return
    }
    payload.product = { ...inlineProduct.value }
  }
  generating.value = true
  generateCopy(payload)
      .then(res => {
        const data = res.data?.data
        if (!data) {
          message.warning('生成结果为空')
          return
        }
        result.value = data
        // 生成接口返回了商品名，即使没点「查询商品」也能回显
        if (!product.value && data.productName) {
          product.value = { id: data.productId, name: data.productName }
        }
      })
      .catch(() => {})
      .finally(() => { generating.value = false })
}

const copyField = (f) => {
  navigator.clipboard.writeText(f.value)
      .then(() => message.success(`${f.label}已复制`))
      .catch(() => message.warning('复制失败，请手动复制'))
}

// 回填：不在本页直接调更新接口，跳商品列表页打开编辑弹窗，由编辑页完成保存
const goEditProduct = () => {
  router.push({ path: '/goods/list', query: { editId: editTargetId.value } })
}

// ==================== 图片 Tab ====================
const imgPrompt = ref('')
const imgScene = ref('main')
const imgModel = ref('')
const imgGenerating = ref(false)
const imgResult = ref([])
const applyingUrl = ref('')

const fullUrl = (url) => imageHost + url

const generateImg = () => {
  if (!imgPrompt.value.trim() && !productId.value && !inlineProduct.value.name.trim() && !product.value) {
    message.warning('请填写画面描述，或先输入商品 ID / 名称')
    return
  }
  imgGenerating.value = true
  const payload = {
    prompt: imgPrompt.value,
    scene: imgScene.value,
    model: imgModel.value || undefined,
    count: 1,
  }
  if (productId.value) {
    payload.productId = productId.value
  } else if (inlineProduct.value.name.trim() || product.value) {
    payload.product = {
      name: product.value?.name || inlineProduct.value.name,
      sellingPoints: inlineProduct.value.sellingPoints,
    }
  }
  generateImage(payload)
      .then(res => {
        imgResult.value = res.data?.data?.images || []
        if (!imgResult.value.length) {
          message.warning('未生成图片，请调整描述后重试')
        }
      })
      .catch(() => {})
      .finally(() => { imgGenerating.value = false })
}

// 设为主图：走 AI 回填链路（Java 内部接口白名单），直接落库 products.image
const applyAsMain = (url) => {
  if (!productId.value) return
  applyingUrl.value = url
  applyCopy({ productId: productId.value, fields: { image: url } })
      .then(() => message.success('已设为主图'))
      .catch(() => {})
      .finally(() => { applyingUrl.value = '' })
}
</script>

<style scoped>
.result-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.field-compare {
  display: flex;
  gap: 16px;
}
.field-block {
  flex: 1;
  min-width: 0;
}
.field-label {
  color: #8c8c8c;
  font-size: 12px;
  margin-bottom: 4px;
}
.field-value {
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
  /* 纯文本 + 换行，不渲染 Markdown */
  white-space: pre-wrap;
  word-break: break-word;
}
.field-value.generated {
  background: #f6ffed;
}
.result-tip {
  margin-top: 16px;
  color: #8c8c8c;
  font-size: 12px;
}
.img-grid {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 16px;
}
.img-card {
  width: 260px;
}
.img-preview {
  width: 100%;
  border-radius: 4px;
  display: block;
}
.img-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
</style>
