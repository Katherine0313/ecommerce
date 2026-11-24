<template>
  <div>
    <!-- 统计卡：默认已审核口径，退款只读售后数据 -->
    <div class="stats">
      <div class="stat-card">
        <div class="stat-label">已审核实收</div>
        <div class="stat-value">￥{{ money(summary.auditedAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">退款金额</div>
        <div class="stat-value danger">￥{{ money(summary.refundAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已审核净额</div>
        <div class="stat-value">￥{{ money(summary.netAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待审核笔数</div>
        <div class="stat-value">{{ summary.pendingCount || 0 }}</div>
      </div>
    </div>

    <!-- 筛选 + 导出 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.receiptNo" placeholder="收款单号" allow-clear style="width: 170px"
                   @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-input v-model:value="query.orderNo" placeholder="订单号" allow-clear style="width: 170px"
                   @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-input-number v-model:value="query.userId" placeholder="买家 ID" :min="1" :precision="0"
                          style="width: 110px" @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.channel" placeholder="渠道" allow-clear style="width: 110px"
                    :options="channelOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 110px"
                    :options="statusOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.accountUnassigned" placeholder="账户归集" allow-clear style="width: 130px"
                    :options="[{ value: 1, label: '仅看未归集' }]" />
        </a-form-item>
        <a-form-item>
          <a-range-picker v-model:value="dateRange" :show-time="{ format: 'HH:mm:ss' }" style="padding: 5px"
                          :placeholder="['收款开始时间', '收款结束时间']" />
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
        <a-button v-permission="'finance:receipt:export'" :loading="exporting" @click="handleExport">
          <DownloadOutlined />
          导出
        </a-button>
      </div>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" :loading="loading" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'user'">
          <div v-if="record.userName">
            <div>{{ record.userName }}</div>
            <div class="muted">ID {{ record.userId }}</div>
          </div>
          <span v-else class="muted">ID {{ record.userId }}</span>
        </template>
        <template v-else-if="column.key === 'account'">
          <span v-if="record.accountName">{{ record.accountName }}</span>
          <span v-else class="muted">未配置</span>
        </template>
        <template v-else-if="column.key === 'amount'">
          <span class="amount">￥{{ money(record.amount) }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'orange'">{{ record.statusDesc }}</a-tag>
          <a-tag v-if="record.refunded" color="red">已退款</a-tag>
        </template>
        <template v-else-if="column.key === 'audit'">
          <div v-if="record.auditName">
            <div>{{ record.auditName }}</div>
            <div class="muted">{{ formatTime(record.auditTime) }}</div>
          </div>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="action-buttons">
            <a-button v-permission="'finance:receipt:detail'" size="small" @click="openDetail(record)">查看</a-button>
            <template v-if="record.status === 0">
              <a-button v-permission="'finance:receipt:audit'" size="small" type="primary" @click="handleAudit(record)">
                审核
              </a-button>
              <a-button v-permission="'finance:receipt:detail'" size="small" @click="openRemark(record)">备注</a-button>
            </template>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 详情抽屉：单据 + 订单摘要 + 退款标记，只读 -->
    <a-drawer v-model:visible="detailVisible" title="收款单详情" :width="520">
      <a-spin :spinning="detailLoading">
        <a-descriptions v-if="detail" :column="1" bordered size="small">
          <a-descriptions-item label="收款单号">{{ detail.receiptNo }}</a-descriptions-item>
          <a-descriptions-item label="订单号">{{ detail.orderNo }}</a-descriptions-item>
          <a-descriptions-item label="买家">
            {{ detail.userName || '—' }}（ID {{ detail.userId }}）
          </a-descriptions-item>
          <a-descriptions-item label="渠道">{{ detail.channelDesc }}</a-descriptions-item>
          <a-descriptions-item label="收款账户">{{ detail.accountName || '未配置' }}</a-descriptions-item>
          <a-descriptions-item label="收款金额">
            <span class="amount">￥{{ money(detail.amount) }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="收款时间">{{ formatTime(detail.paidAt) }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="detail.status === 1 ? 'green' : 'orange'">{{ detail.statusDesc }}</a-tag>
            <a-tag v-if="detail.refunded" color="red">已退款</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="订单状态">{{ orderStatusText(detail.orderStatus) }}</a-descriptions-item>
          <a-descriptions-item label="收货人">{{ detail.consignee || '—' }}</a-descriptions-item>
          <a-descriptions-item v-if="detail.refunded" label="退款金额">
            ￥{{ money(detail.refundAmount) }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detail.refunded" label="退款时间">
            {{ formatTime(detail.refundedAt) }}
          </a-descriptions-item>
          <a-descriptions-item label="审核人">{{ detail.auditName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="审核时间">{{ formatTime(detail.auditTime) }}</a-descriptions-item>
          <a-descriptions-item label="备注">{{ detail.remark || '—' }}</a-descriptions-item>
          <a-descriptions-item label="生成时间">{{ formatTime(detail.createTime) }}</a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-drawer>

    <!-- 备注弹窗：已审核单据不显示入口 -->
    <a-modal v-model:visible="remarkVisible" title="修改备注" :confirm-loading="remarkSaving" @ok="submitRemark">
      <a-textarea v-model:value="remarkText" :rows="3" :maxlength="255" show-count placeholder="请输入备注" />
    </a-modal>
  </div>
</template>

<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import {
  auditReceipt,
  exportReceipts,
  getReceiptDetail,
  getReceiptList,
  getReceiptSummary,
  updateReceiptRemark,
} from '@/api/finance.js'

const list = ref([])
const loading = ref(false)
const exporting = ref(false)
const summary = ref({ auditedAmount: 0, refundAmount: 0, netAmount: 0, pendingCount: 0 })

const route = useRoute()
const routeFilters = () => ({
  orderNo: typeof route.query.orderNo === 'string' ? route.query.orderNo : '',
  status: ['0', '1'].includes(route.query.status) ? Number(route.query.status) : undefined,
  accountUnassigned: route.query.accountUnassigned === '1' ? 1 : undefined,
})
const query = ref({
  receiptNo: '', orderNo: '', userId: undefined, channel: undefined, status: undefined,
  accountUnassigned: undefined, ...routeFilters(),
})
const dateRange = ref([])

const pagination = ref({
  current: 1,
  pageSize: 13,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const channelOptions = [
  { value: 'WeChat', label: '微信支付' },
  { value: 'Alipay', label: '支付宝' },
  { value: 'BankCard', label: '银行卡' },
]
const statusOptions = [
  { value: 0, label: '待审核' },
  { value: 1, label: '已审核' },
]

const columns = [
  { title: '收款单号', dataIndex: 'receiptNo', key: 'receiptNo' },
  { title: '订单号', dataIndex: 'orderNo', key: 'orderNo' },
  { title: '买家', key: 'user' },
  { title: '渠道', dataIndex: 'channelDesc', key: 'channel' },
  { title: '收款账户', key: 'account' },
  { title: '金额', key: 'amount', align: 'right' },
  { title: '收款时间', dataIndex: 'paidAt', key: 'paidAt' },
  { title: '状态', key: 'status' },
  { title: '审核人', key: 'audit' },
  { title: '操作', key: 'action', fixed: 'right', width: 200 },
]

// 详情 / 备注
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const remarkVisible = ref(false)
const remarkSaving = ref(false)
const remarkTarget = ref(null)
const remarkText = ref('')

/** 列表 / 统计 / 导出共用筛选参数（空值不传） */
function buildFilters() {
  const params = {}
  if (query.value.receiptNo) params.receiptNo = query.value.receiptNo
  if (query.value.orderNo) params.orderNo = query.value.orderNo
  if (query.value.userId !== undefined && query.value.userId !== null) params.userId = query.value.userId
  if (query.value.channel) params.channel = query.value.channel
  if (query.value.status !== undefined && query.value.status !== null) params.status = query.value.status
  if (query.value.accountUnassigned === 1) params.accountUnassigned = true
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0].format('YYYY-MM-DD HH:mm:ss')
    params.endDate = dateRange.value[1].format('YYYY-MM-DD HH:mm:ss')
  }
  return params
}

function loadData() {
  loading.value = true
  const params = { ...buildFilters(), page: pagination.value.current, pageSize: pagination.value.pageSize }
  getReceiptList(params)
      .then((res) => {
        const data = res.data?.data || {}
        list.value = data.list || []
        pagination.value.total = data.total || 0
      })
      .catch((err) => console.log(err))
      .finally(() => {
        loading.value = false
      })
  loadSummary()
}

function loadSummary() {
  getReceiptSummary(buildFilters())
      .then((res) => {
        summary.value = res.data?.data || { auditedAmount: 0, refundAmount: 0, netAmount: 0, pendingCount: 0 }
      })
      .catch((err) => console.log(err))
}

function handleSearch() {
  pagination.value.current = 1
  loadData()
}

function handleReset() {
  query.value = {
    receiptNo: '', orderNo: '', userId: undefined, channel: undefined, status: undefined,
    accountUnassigned: undefined,
  }
  dateRange.value = []
  handleSearch()
}

function handleTableChange(pager) {
  pagination.value.current = pager.current
  pagination.value.pageSize = pager.pageSize
  loadData()
}

function openDetail(record) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  getReceiptDetail(record.id)
      .then((res) => {
        detail.value = res.data?.data || null
      })
      .catch((err) => console.log(err))
      .finally(() => {
        detailLoading.value = false
      })
}

function openRemark(record) {
  remarkTarget.value = record
  remarkText.value = record.remark || ''
  remarkVisible.value = true
}

function submitRemark() {
  remarkSaving.value = true
  updateReceiptRemark(remarkTarget.value.id, remarkText.value)
      .then(() => {
        message.success('备注已保存')
        remarkVisible.value = false
        loadData()
      })
      .catch((err) => console.log(err))
      .finally(() => {
        remarkSaving.value = false
      })
}

function handleAudit(record) {
  Modal.confirm({
    title: '确认审核该收款单？',
    content: `单号 ${record.receiptNo}，金额 ￥${money(record.amount)}。审核后即为终态，不可撤销。`,
    okText: '确认审核',
    cancelText: '取消',
    onOk: () =>
      auditReceipt(record.id).then(() => {
        message.success('审核成功')
        loadData()
      }),
  })
}

async function handleExport() {
  if (exporting.value) return
  exporting.value = true
  try {
    const res = await exportReceipts(buildFilters())
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `收款单列表_${dayjs().format('YYYYMMDD')}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (err) {
    console.error('导出收款单失败', err)
    message.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

function money(value) {
  const num = Number(value || 0)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

const ORDER_STATUS = {
  1: '待付款',
  3: '待发货',
  4: '已发货',
  5: '已完成',
  6: '已取消',
  7: '已退款',
}

function orderStatusText(status) {
  return ORDER_STATUS[status] || '—'
}

onMounted(() => {
  loadData()
})
onActivated(() => {
  query.value = { ...query.value, ...routeFilters() }
  pagination.value.current = 1
  loadData()
})
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-top: 20px;
}

.stat-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px 20px;
}

.stat-label {
  color: #999;
  font-size: 13px;
}

.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
}

.stat-value.danger {
  color: #cf1322;
}

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

.amount {
  font-weight: 600;
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

@media (max-width: 991px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .head {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
