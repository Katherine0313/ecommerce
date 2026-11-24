<template>
  <div>
    <!-- 工具栏：标题 / 分类 / 状态筛选 + 分类管理 + 新建 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="请输入文章标题" allow-clear @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.categoryId" style="width: 160px" placeholder="文章分类" allow-clear
                    :options="categoryOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.status" style="width: 120px" placeholder="状态" allow-clear
                    :options="statusOptions" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
        </a-form-item>
      </a-form>
      <div class="head-btns">
        <a-button v-permission="'business:article:add'" @click="openCategoryManager">分类管理</a-button>
        <a-button v-permission="'business:article:add'" type="primary" @click="openCreate">新建文章</a-button>
      </div>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'cover'">
          <img v-if="record.cover" :src="imageUrl(record.cover)" class="cover-image" />
          <span v-else class="muted">无图</span>
        </template>
        <template v-else-if="column.key === 'category'">
          {{ record.categoryName || '未分类' }}
        </template>
        <template v-else-if="column.key === 'introduction'">
          <span class="intro">{{ record.introduction || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'recommend'">
          <a-tag v-if="record.recommendHot === 1" color="orange">热门</a-tag>
          <a-tag v-if="record.recommendBanner === 1" color="blue">轮播</a-tag>
          <span v-if="record.recommendHot !== 1 && record.recommendBanner !== 1" class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.statusDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="action-buttons">
            <a-button v-permission="'business:article:edit'" size="small" type="primary"
                      @click="openEdit(record)">编辑</a-button>
            <a-popconfirm v-if="record.status !== 1" title="确定上架该文章？" @confirm="handleToggle(record, 1)">
              <a-button v-permission="'business:article:edit'" size="small">上架</a-button>
            </a-popconfirm>
            <a-popconfirm title="确定删除该文章？删除后不可恢复。" @confirm="handleDelete(record)">
              <a-button v-permission="'business:article:remove'" size="small" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新建 / 编辑 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑文章' : '新建文章'" :width="900"
             :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 4 }">
        <a-form-item label="文章分类">
          <a-select v-model:value="form.categoryId" style="width: 240px" :options="categoryOptions"
                    placeholder="不选则为未分类" allow-clear />
        </a-form-item>
        <a-form-item label="标题" required>
          <a-input v-model:value="form.title" :maxlength="64" placeholder="如「关于我们」" />
        </a-form-item>
        <a-form-item label="作者">
          <a-input v-model:value="form.author" :maxlength="32" placeholder="留空则不显示" />
        </a-form-item>
        <a-form-item label="封面图">
          <a-upload :action="uploadAction" :headers="uploadHeaders" :before-upload="beforeUpload"
                    v-model:file-list="fileList" :maxCount="1" @change="imageUpload">
            <a-button>
              <UploadOutlined />
              点击上传
            </a-button>
          </a-upload>
          <span class="unit">建议尺寸 {{ articleSpecLabel }}，上传后按该尺寸居中裁切</span>
        </a-form-item>
        <a-form-item label="正文">
          <Editor v-model="form.content" placeholder="请输入文章正文..." />
        </a-form-item>
        <a-form-item label="简介">
          <a-textarea v-model:value="form.introduction" :maxlength="200" :rows="2"
                      placeholder="列表页展示的摘要" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" style="width: 200px" />
          <span class="unit">数字越小越靠前</span>
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model:value="form.status">
            <a-radio :value="1">上架</a-radio>
            <a-radio :value="0">下架</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="推荐位">
          <a-space>
            <a-switch v-model:checked="form.recommendHot" /> <span class="unit">热门</span>
            <a-switch v-model:checked="form.recommendBanner" /> <span class="unit">轮播</span>
          </a-space>
        </a-form-item>
        <a-form-item label="关联商品">
          <a-select v-model:value="form.spuId" style="width: 320px" :options="productOptions"
                    placeholder="可选，关联后小程序可跳到该商品" allow-clear show-search
                    option-filter-prop="label" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分类管理 -->
    <ArticleCategoryModal v-model:visible="categoryVisible" @change="loadCategories" />
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { SearchOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  addArticle,
  deleteArticle,
  getArticleCategoryList,
  getArticleDetail,
  getArticleList,
  updateArticle,
  updateArticleStatus,
} from '@/api/article.js'
import { getGoodsList } from '@/api/products.js'
import { compressImage } from '@/utils/compressImage'
import { getImageSpec, specLabel } from '@/utils/image-spec'
import { getToken } from '@/utils/token'
// 富文本编辑器复用商品详情编辑器，正文图走 type=body 上传
import Editor from '@/pages/goods/add-goods/Editor.vue'
import ArticleCategoryModal from './ArticleCategoryModal.vue'

const list = ref([])
const saving = ref(false)
const formVisible = ref(false)
const editingId = ref(null)
const query = ref({ keyword: '', categoryId: undefined, status: undefined })
const fileList = ref([])
const categories = ref([])
const products = ref([])
const categoryVisible = ref(false)

const statusOptions = [
  { value: 1, label: '上架' },
  { value: 0, label: '下架' },
]

const categoryOptions = computed(() =>
  categories.value.map((c) => ({ value: c.id, label: c.name }))
)

const productOptions = computed(() =>
  products.value.map((p) => ({ value: p.id, label: p.name }))
)

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '封面', key: 'cover', width: 90 },
  { title: '标题', dataIndex: 'title', key: 'title' },
  { title: '分类', key: 'category'},
  { title: '作者', dataIndex: 'author', key: 'author' },
  { title: '简介', key: 'introduction' },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder' },
  { title: '推荐', key: 'recommend' },
  { title: '状态', key: 'status' },
  { title: '浏览量', dataIndex: 'browseCount', key: 'browseCount'},
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime'},
  { title: '操作', key: 'action', width: 200 },
]

const form = ref(emptyForm())

function emptyForm() {
  return {
    categoryId: undefined,
    title: '',
    author: '',
    cover: '',
    content: '',
    introduction: '',
    sortOrder: 0,
    status: 1,
    recommendHot: false,
    recommendBanner: false,
    spuId: undefined,
  }
}

function loadData() {
  getArticleList({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    categoryId: query.value.categoryId,
    status: query.value.status,
  }).then((res) => {
    list.value = res.data.data.list || []
    pagination.value.total = res.data.data.total || 0
  })
}

function loadCategories() {
  getArticleCategoryList().then((res) => {
    categories.value = res.data.data || []
  })
}

function loadProducts() {
  getGoodsList({ page: 1, pageSize: 500 }).then((res) => {
    products.value = res.data.data.list || []
  })
}

function handleSearch() {
  pagination.value.current = 1
  loadData()
}

function handleTableChange(pager) {
  pagination.value.current = pager.current
  pagination.value.pageSize = pager.pageSize
  loadData()
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  fileList.value = []
  formVisible.value = true
}

function openEdit(record) {
  getArticleDetail(record.id).then((res) => {
    const data = res.data.data
    editingId.value = data.id
    form.value = {
      categoryId: data.categoryId || undefined,
      title: data.title,
      author: data.author || '',
      cover: data.cover || '',
      content: data.content || '',
      introduction: data.introduction || '',
      sortOrder: data.sortOrder ?? 0,
      status: data.status,
      recommendHot: data.recommendHot === 1,
      recommendBanner: data.recommendBanner === 1,
      spuId: data.spuId || undefined,
    }
    fileList.value = data.cover ? [{ uid: '-1', name: data.cover, status: 'done', url: imageUrl(data.cover) }] : []
    formVisible.value = true
  })
}

function handleSubmit() {
  if (!form.value.title) {
    message.warning('请输入标题')
    return
  }
  const payload = {
    categoryId: form.value.categoryId || 0,
    title: form.value.title,
    author: form.value.author,
    cover: form.value.cover,
    content: form.value.content,
    introduction: form.value.introduction,
    sortOrder: form.value.sortOrder ?? 0,
    status: form.value.status,
    recommendHot: form.value.recommendHot ? 1 : 0,
    recommendBanner: form.value.recommendBanner ? 1 : 0,
    spuId: form.value.spuId || 0,
  }
  saving.value = true
  const request = editingId.value ? updateArticle(editingId.value, payload) : addArticle(payload)
  request
    .then(() => {
      message.success(editingId.value ? '文章已更新' : '文章已创建')
      formVisible.value = false
      loadData()
    })
    .finally(() => {
      saving.value = false
    })
}

function handleToggle(record, status) {
  updateArticleStatus(record.id, status).then(() => {
    message.success(status === 1 ? '已上架' : '已下架')
    loadData()
  })
}

function handleDelete(record) {
  deleteArticle(record.id).then(() => {
    message.success('文章已删除')
    loadData()
  })
}

function openCategoryManager() {
  categoryVisible.value = true
}

// 图片相对路径补接口域名：开发环境前后端不同源，生产为空走同源相对路径
function imageUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : (import.meta.env.VITE_API_BASE_URL || '') + path
}

// ====================== 图片上传 =====================
const uploadAction = import.meta.env.VITE_API_BASE_URL + '/api/admin/upload?type=article'
const uploadHeaders = { Authorization: 'Bearer ' + getToken() }

// 文章封面按 16:9 铺满展示，规格表见 utils/image-spec.js
const articleSpec = getImageSpec('article')
const articleSpecLabel = specLabel(articleSpec)

function beforeUpload(file) {
  return compressImage(file, articleSpec)
}

function imageUpload(info) {
  fileList.value = info.fileList
  if (info.file.status === 'done') {
    form.value.cover = info.file.response.data.url
  } else if (info.file.status === 'error') {
    const msg = (info.file.response && info.file.response.message) || '图片上传失败，请重试'
    message.error(msg)
  }
}

onMounted(() => {
  loadCategories()
  loadProducts()
  loadData()
})
onActivated(() => {
  loadData()
})
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin: 20px 0;
}

.head-btns {
  display: flex;
  gap: 8px;
}

.unit {
  margin-left: 8px;
  color: #999;
}

.muted {
  color: #999;
}

.cover-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}

.intro {
  color: #666;
}

/* 操作列一排两个，避免按钮挤在一行 */
.action-buttons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.action-buttons :deep(.ant-btn) {
  width: 100%;
}

@media (max-width: 767px) {
  .head {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
