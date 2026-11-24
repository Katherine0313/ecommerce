<template>
  <div>
    <!-- 工具栏：关键字（编码/名称） + 状态筛选 + 新建 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="快递公司编码/名称" allow-clear style="width: 200px"
                   @pressEnter="handleSearch" />
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
      <a-button v-permission="'order:express:save'" type="primary" @click="openCreate">新建快递公司</a-button>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" :loading="loading" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'logo'">
          <img v-if="record.logo" :src="imageUrl(record.logo)" class="express-logo" />
          <span v-else class="muted">无图</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.statusDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="action-buttons">
            <a-button v-permission="'order:express:save'" size="small" type="primary" @click="openEdit(record)">
              编辑
            </a-button>
            <a-popconfirm title="确定删除该快递公司？删除后不能再选它发货。" @confirm="handleDelete(record)">
              <a-button v-permission="'order:express:remove'" size="small" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新建 / 编辑 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑快递公司' : '新建快递公司'" :width="640"
             :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" style="width: 200px" />
          <span class="unit">数字越小越靠前</span>
        </a-form-item>
        <a-form-item label="编码" required>
          <a-input v-model:value="form.code" :disabled="!!editingId" placeholder="英文 key，如 shunfeng" />
          <span v-if="!editingId" class="unit">建好后不可修改，订单和包裹都按编码关联</span>
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" :maxlength="32" placeholder="如 顺丰速运" />
        </a-form-item>
        <a-form-item label="Logo">
          <a-upload :action="uploadAction" :headers="uploadHeaders" :before-upload="beforeUpload"
                    v-model:file-list="fileList" :maxCount="1" @change="imageUpload">
            <a-button>
              <UploadOutlined />
              点击上传
            </a-button>
          </a-upload>
          <span class="unit">建议尺寸 {{ expressSpecLabel }}</span>
        </a-form-item>
        <a-form-item label="启用状态">
          <a-radio-group v-model:value="form.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" placeholder="选填" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { SearchOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { addExpress, deleteExpress, getExpressList, updateExpress } from '@/api/delivery.js'
import { compressImage } from '@/utils/compressImage'
import { getImageSpec, specLabel } from '@/utils/image-spec'
import { getToken } from '@/utils/token'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const editingId = ref(null)
const query = ref({ keyword: '', status: undefined })
const fileList = ref([])

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
  { title: '编码', dataIndex: 'code', key: 'code'},
  { title: '名称', dataIndex: 'name', key: 'name'},
  { title: 'Logo', key: 'logo' },
  { title: '状态', key: 'status' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action',width:200 },
]

const form = ref(emptyForm())

function emptyForm() {
  return {
    code: '',
    name: '',
    logo: '',
    sortOrder: 0,
    status: 1,
    remark: '',
  }
}

function loadData() {
  loading.value = true
  getExpressList({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    status: query.value.status,
  })
      .then((res) => {
        const data = res.data?.data || {}
        list.value = data.list || []
        pagination.value.total = data.total || 0
      })
      .catch((err) => console.log(err))
      .finally(() => {
        loading.value = false
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
  editingId.value = record.id
  form.value = {
    code: record.code,
    name: record.name || '',
    logo: record.logo || '',
    sortOrder: record.sortOrder ?? 0,
    status: record.status,
    remark: record.remark || '',
  }
  fileList.value = record.logo ? [{ uid: '-1', name: record.logo, status: 'done', url: imageUrl(record.logo) }] : []
  formVisible.value = true
}

function handleSubmit() {
  if (!form.value.code || !form.value.code.trim()) {
    message.warning('请填写快递公司编码')
    return
  }
  if (!form.value.name || !form.value.name.trim()) {
    message.warning('请填写快递公司名称')
    return
  }
  const payload = {
    code: form.value.code.trim(),
    name: form.value.name.trim(),
    logo: form.value.logo,
    sortOrder: form.value.sortOrder ?? 0,
    status: form.value.status,
    remark: form.value.remark,
  }
  saving.value = true
  const request = editingId.value ? updateExpress(editingId.value, payload) : addExpress(payload)
  request
      .then(() => {
        message.success(editingId.value ? '快递公司已更新' : '快递公司已创建')
        formVisible.value = false
        loadData()
      })
      .catch((err) => {
        // 错误提示由 request.js 拦截器统一弹，这里不再重复弹
        console.log(err)
      })
      .finally(() => {
        saving.value = false
      })
}

function handleDelete(record) {
  deleteExpress(record.id)
      .then(() => {
        message.success('快递公司已删除')
        loadData()
      })
      .catch((err) => console.log(err))
}

// 图片相对路径补接口域名：开发环境前后端不同源，生产为空走同源相对路径
function imageUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : (import.meta.env.VITE_API_BASE_URL || '') + path
}

// ====================== 图片上传 =====================
const uploadAction = import.meta.env.VITE_API_BASE_URL + '/api/admin/upload?type=express'
const uploadHeaders = { Authorization: 'Bearer ' + getToken() }

// 快递公司 logo 是白底小方图，规格见 utils/image-spec.js
const expressSpec = computed(() => getImageSpec('express'))
const expressSpecLabel = computed(() => specLabel(expressSpec.value))

function beforeUpload(file) {
  return compressImage(file, expressSpec.value)
}

function imageUpload(info) {
  fileList.value = info.fileList
  if (info.file.status === 'done') {
    form.value.logo = info.file.response.data.url
  } else if (info.file.status === 'error') {
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

.express-logo {
  width: 48px;
  height: 48px;
  object-fit: contain;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 50px;
}

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
