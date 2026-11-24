<template>
  <div>
    <!-- 筛选 + 新增 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="账户名称 / 商户号" allow-clear style="width: 200px"
                   @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.channel" placeholder="渠道" allow-clear style="width: 120px"
                    :options="channelOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 110px"
                    :options="statusOptions" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
          <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
        </a-form-item>
      </a-form>
      <div class="head-actions">
        <a-button v-permission="'finance:account:add'" type="primary" @click="openCreate">
          <PlusOutlined />
          新增账户
        </a-button>
      </div>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" :loading="loading" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'channel'">
          {{ record.channelDesc }}
        </template>
        <template v-else-if="column.key === 'default'">
          <a-tag v-if="record.isDefault === 1" color="blue">默认</a-tag>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.statusDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="action-buttons">
            <a-button v-permission="'finance:account:edit'" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button v-permission="'finance:account:edit'" size="small"
                      @click="toggleStatus(record)">
              {{ record.status === 1 ? '停用' : '启用' }}
            </a-button>
            <a-button v-permission="'finance:account:edit'" size="small"
                      :disabled="record.isDefault === 1 || record.status !== 1"
                      @click="makeDefault(record)">
              设为默认
            </a-button>
            <a-popconfirm title="确定删除该收款账户？删除后历史单据仍可查看。" @confirm="handleDelete(record)">
              <a-button v-permission="'finance:account:remove'" size="small" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑收款账户' : '新增收款账户'" :width="620"
             :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="账户名称" required>
          <a-input v-model:value="form.accountName" :maxlength="64" placeholder="如 微信商户-主店" />
        </a-form-item>
        <a-form-item label="收款渠道" required>
          <a-select v-model:value="form.channel" :options="channelOptions" :disabled="!!editingId"
                    placeholder="请选择渠道" />
          <span v-if="!editingId" class="unit">建好后不可修改</span>
        </a-form-item>
        <a-form-item label="商户号" required>
          <a-input v-model:value="form.merchantNo" :maxlength="64" placeholder="同渠道唯一，如 WX2026000001" />
        </a-form-item>
        <a-form-item v-if="!editingId" label="设为默认">
          <a-checkbox v-model:checked="form.isDefault">同渠道默认账户（收款单优先归集到此账户）</a-checkbox>
        </a-form-item>
        <a-form-item v-if="!editingId" label="启用状态">
          <a-radio-group v-model:value="form.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="2" :maxlength="255" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  addAccount,
  deleteAccount,
  getAccountList,
  setDefaultAccount,
  updateAccount,
  updateAccountStatus,
} from '@/api/finance.js'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const editingId = ref(null)
const query = ref({ keyword: '', channel: undefined, status: undefined })

const channelOptions = [
  { value: 'WeChat', label: '微信支付' },
  { value: 'Alipay', label: '支付宝' },
  { value: 'BankCard', label: '银行卡' },
]
const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
]

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '账户名称', dataIndex: 'accountName', key: 'accountName' },
  { title: '渠道', key: 'channel' },
  { title: '商户号', dataIndex: 'merchantNo', key: 'merchantNo' },
  { title: '默认', key: 'default', width: 90 },
  { title: '状态', key: 'status', width: 100 },
  { title: '备注', key: 'remark' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', fixed: 'right', width: 300 },
]

const form = ref(emptyForm())

function emptyForm() {
  return {
    accountName: '',
    channel: undefined,
    merchantNo: '',
    isDefault: false,
    status: 1,
    remark: '',
  }
}

function loadData() {
  loading.value = true
  getAccountList({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    channel: query.value.channel,
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

function handleReset() {
  query.value = { keyword: '', channel: undefined, status: undefined }
  handleSearch()
}

function handleTableChange(pager) {
  pagination.value.current = pager.current
  pagination.value.pageSize = pager.pageSize
  loadData()
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  formVisible.value = true
}

function openEdit(record) {
  editingId.value = record.id
  form.value = {
    accountName: record.accountName || '',
    channel: record.channel,
    merchantNo: record.merchantNo || '',
    isDefault: record.isDefault === 1,
    status: record.status,
    remark: record.remark || '',
  }
  formVisible.value = true
}

function handleSubmit() {
  if (!form.value.accountName || !form.value.accountName.trim()) {
    message.warning('请填写账户名称')
    return
  }
  if (!editingId.value && !form.value.channel) {
    message.warning('请选择收款渠道')
    return
  }
  if (!form.value.merchantNo || !form.value.merchantNo.trim()) {
    message.warning('请填写商户号')
    return
  }
  const payload = {
    accountName: form.value.accountName.trim(),
    channel: form.value.channel,
    merchantNo: form.value.merchantNo.trim(),
    isDefault: form.value.isDefault ? 1 : 0,
    status: form.value.status,
    remark: form.value.remark,
  }
  saving.value = true
  const request = editingId.value ? updateAccount(editingId.value, payload) : addAccount(payload)
  request
      .then(() => {
        message.success(editingId.value ? '账户已更新' : '账户已创建')
        formVisible.value = false
        loadData()
      })
      .catch((err) => console.log(err))
      .finally(() => {
        saving.value = false
      })
}

function toggleStatus(record) {
  const next = record.status === 1 ? 0 : 1
  updateAccountStatus(record.id, next)
      .then(() => {
        message.success(next === 1 ? '账户已启用' : '账户已停用')
        loadData()
      })
      .catch((err) => console.log(err))
}

function makeDefault(record) {
  setDefaultAccount(record.id)
      .then(() => {
        message.success(`已将「${record.accountName}」设为 ${record.channelDesc} 默认账户`)
        loadData()
      })
      .catch((err) => console.log(err))
}

function handleDelete(record) {
  deleteAccount(record.id)
      .then(() => {
        message.success('账户已删除')
        loadData()
      })
      .catch((err) => console.log(err))
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

.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.unit {
  margin-left: 8px;
  color: #999;
}

.muted {
  color: #bbb;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.action-buttons :deep(.ant-btn) {
  padding: 0 8px;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .head {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
