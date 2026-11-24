<template>
  <div>
    <!-- 工具栏：标题 / 位置 / 状态筛选 + 新建 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="请输入 Banner 标题" allow-clear @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.position" style="width: 180px" placeholder="广告位置" allow-clear
                    :options="positionOptions" />
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
      <a-button v-permission="'business:banner:add'" type="primary" @click="openCreate">新建 Banner</a-button>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'image'">
          <img v-if="record.image" :src="imageUrl(record.image)" class="banner-image" />
          <span v-else class="muted">无图</span>
          <a-tag v-if="record.images && record.images.length > 1">{{ record.images.length }} 张</a-tag>
        </template>
        <template v-else-if="column.key === 'position'">
          {{ record.positionDesc }}
        </template>
        <template v-else-if="column.key === 'link'">
          {{ linkText(record) }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.statusDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="action-buttons">
            <a-button v-permission="'business:banner:edit'" size="small" type="primary"
                      @click="openEdit(record)">编辑</a-button>
            <a-popconfirm v-if="record.status !== 1" title="确定启用该 Banner？" @confirm="handleToggle(record, 1)">
              <a-button v-permission="'business:banner:edit'" size="small">启用</a-button>
            </a-popconfirm>
            <a-popconfirm title="确定删除该 Banner？删除后不再展示。" @confirm="handleDelete(record)">
              <a-button v-permission="'business:banner:remove'" size="small" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新建 / 编辑 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑 Banner' : '新建 Banner'" :width="720"
             :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="标题" required>
          <a-input v-model:value="form.title" :maxlength="64" placeholder="如「双十一大促」" />
        </a-form-item>
        <a-form-item label="Banner 图" required>
          <a-upload :action="uploadAction" :headers="uploadHeaders" :before-upload="beforeUpload"
                    v-model:file-list="fileList" :maxCount="4" @change="imageUpload">
            <a-button>
              <UploadOutlined />
              点击上传
            </a-button>
          </a-upload>
          <span class="unit">最多 4 张，建议尺寸 {{ bannerSpecLabel }}，上传后按该尺寸居中裁切</span>
        </a-form-item>
        <a-form-item label="广告位置" required>
          <a-select v-model:value="form.position" :options="positionOptions" placeholder="请选择广告位置" />
        </a-form-item>
        <a-form-item label="跳转类型">
          <a-radio-group v-model:value="form.linkType">
            <a-radio :value="0">不跳转</a-radio>
            <a-radio :value="1">商品</a-radio>
            <a-radio :value="2">文章</a-radio>
            <a-radio :value="3">外部链接</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="form.linkType === 1" label="跳转商品" required>
          <a-input-number v-model:value="form.linkValue" :min="1" :precision="0" style="width: 200px" />
          <span class="unit">填商品 ID</span>
        </a-form-item>
        <a-form-item v-if="form.linkType === 2" label="跳转文章" required>
          <a-input-number v-model:value="form.linkValue" :min="1" :precision="0" style="width: 200px" />
          <span class="unit">填文章 ID</span>
        </a-form-item>
        <a-form-item v-if="form.linkType === 3" label="跳转链接" required>
          <a-input v-model:value="form.linkValue" placeholder="以 http:// 或 https:// 开头" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" style="width: 200px" />
          <span class="unit">数字越小越靠前</span>
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model:value="form.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { SearchOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  addBanner,
  deleteBanner,
  getBannerDetail,
  getBannerList,
  updateBanner,
  updateBannerStatus,
} from '@/api/banner.js'
import { compressImage } from '@/utils/compressImage'
import { getImageSpec, specLabel } from '@/utils/image-spec'
import { getToken } from '@/utils/token'

const list = ref([])
const saving = ref(false)
const formVisible = ref(false)
const editingId = ref(null)
const query = ref({ keyword: '', position: undefined, status: undefined })
const fileList = ref([])

const positionOptions = [
  { value: 1, label: '小程序-首页' },
  { value: 2, label: '小程序-分类页' },
  { value: 11, label: 'PC商城-首页' },
]
const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
]

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder' },
  { title: '图片', key: 'image' },
  { title: '标题', dataIndex: 'title', key: 'title' },
  { title: '位置', key: 'position' },
  { title: '跳转', key: 'link'},
  { title: '状态', key: 'status' },
  { title: '点击量', dataIndex: 'browseCount', key: 'browseCount' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action', width: 200 },
]

const form = ref(emptyForm())

function emptyForm() {
  return {
    title: '',
    position: 1,
    linkType: 0,
    linkValue: '',
    sortOrder: 0,
    status: 1,
  }
}

function loadData() {
  getBannerList({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    position: query.value.position,
    status: query.value.status,
  }).then((res) => {
    list.value = res.data.data.list || []
    pagination.value.total = res.data.data.total || 0
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
  getBannerDetail(record.id).then((res) => {
    const data = res.data.data
    editingId.value = data.id
    form.value = {
      title: data.title,
      position: data.position,
      linkType: data.linkType,
      // linkValue 存的是字符串，商品/文章 ID 转成数字才能交给 input-number
      linkValue: data.linkType === 1 || data.linkType === 2
        ? (Number(data.linkValue) || undefined)
        : (data.linkValue || ''),
      sortOrder: data.sortOrder ?? 0,
      status: data.status,
    }
    // images 是完整列表；老数据没有 images 时回落到单图 image
    const images = Array.isArray(data.images) && data.images.length
      ? data.images
      : (data.image ? [data.image] : [])
    fileList.value = images.map((path, i) => ({
      uid: `-${i + 1}`,
      name: path,
      status: 'done',
      url: imageUrl(path),
      path,
    }))
    formVisible.value = true
  })
}

function handleSubmit() {
  if (fileList.value.some((f) => f.status === 'uploading')) {
    message.warning('图片还在上传，请稍候')
    return
  }
  const images = fileList.value.map(imagePath).filter(Boolean)
  const payload = {
    title: form.value.title,
    images,
    position: form.value.position,
    linkType: form.value.linkType,
    linkValue: form.value.linkType === 0 ? '' : String(form.value.linkValue ?? ''),
    sortOrder: form.value.sortOrder ?? 0,
    status: form.value.status,
  }
  if (!payload.title) {
    message.warning('请输入标题')
    return
  }
  if (!images.length) {
    message.warning('请上传 Banner 图')
    return
  }
  if (payload.linkType !== 0 && !payload.linkValue) {
    message.warning('请填写跳转目标')
    return
  }
  saving.value = true
  const request = editingId.value ? updateBanner(editingId.value, payload) : addBanner(payload)
  request
    .then(() => {
      message.success(editingId.value ? 'Banner 已更新' : 'Banner 已创建')
      formVisible.value = false
      loadData()
    })
    .finally(() => {
      saving.value = false
    })
}

function handleToggle(record, status) {
  updateBannerStatus(record.id, status).then(() => {
    message.success(status === 1 ? '已启用' : '已禁用')
    loadData()
  })
}

function handleDelete(record) {
  deleteBanner(record.id).then(() => {
    message.success('Banner 已删除')
    loadData()
  })
}

// 跳转说明：商品/文章显示 ID，外部链接显示 URL
function linkText(record) {
  if (record.linkType === 0) return '不跳转'
  if (record.linkType === 1) return `商品 #${record.linkValue}`
  if (record.linkType === 2) return `文章 #${record.linkValue}`
  return record.linkValue || '外部链接'
}

// 图片相对路径补接口域名：开发环境前后端不同源，生产为空走同源相对路径
function imageUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : (import.meta.env.VITE_API_BASE_URL || '') + path
}

// 取待提交的服务端相对路径：新上传的从响应里拿，编辑回显的已存在 path 字段
function imagePath(file) {
  if (file.response && file.response.data && file.response.data.url) {
    return file.response.data.url
  }
  return file.path || ''
}

// ====================== 图片上传 =====================
const uploadAction = import.meta.env.VITE_API_BASE_URL + '/api/admin/upload?type=banner'
const uploadHeaders = { Authorization: 'Bearer ' + getToken() }

// Banner 目标画幅按广告位置走：一条 banner 只服务一个位置，
// 小程序位是 375×180px、PC 位是 798×350px，规格表见 utils/image-spec.js
const bannerSpec = computed(() => getImageSpec('banner', form.value.position))
const bannerSpecLabel = computed(() => specLabel(bannerSpec.value))

function beforeUpload(file) {
  // ant-design-vue 3.2 的 maxCount 只截断列表、不拦请求，这里先挡掉超出 4 张的上传
  const count = fileList.value.filter((f) => f.status === 'done' || f.status === 'uploading').length
  if (count >= 4) {
    message.warning('最多上传 4 张图')
    return false
  }
  return compressImage(file, bannerSpec.value)
}

// 位置换了，之前传的图画幅就对不上了，提示重新传
watch(() => form.value.position, (pos, old) => {
  if (old !== undefined && fileList.value.length) {
    message.warning('切换广告位置后建议重新上传图，原图尺寸与新位置不匹配')
  }
})

function imageUpload(info) {
  // 批量选择时 maxCount 只截断展示列表，超出的文件仍会发请求；这里兜底剔掉，提交时也只取前 4 张
  let files = info.fileList
  if (files.length > 4) {
    message.warning('最多上传 4 张图，超出部分已忽略')
    files = files.slice(0, 4)
  }
  fileList.value = files
  if (info.file.status === 'error') {
    const msg = (info.file.response && info.file.response.message) || '图片上传失败，请重试'
    message.error(msg)
  }
}

onMounted(() => {
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

.unit {
  margin-left: 8px;
  color: #999;
}

.muted {
  color: #999;
}

.banner-image {
  width: 90px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}

.banner-images {
  display: flex;
  align-items: center;
  gap: 6px;
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
