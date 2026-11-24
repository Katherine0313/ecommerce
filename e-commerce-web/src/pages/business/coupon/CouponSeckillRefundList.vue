<template>
  <a-modal :visible="visible" title="秒杀券退款审核" :width="1400" :footer="null" @cancel="closeModal">
    <!-- 工具栏：状态筛选 + 搜索 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-select v-model:value="query.status" style="width: 160px" placeholder="退款状态" allow-clear
                    :options="statusOptions" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
        </a-form-item>
      </a-form>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" :scroll="{ x: 1600 }" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'refundNo'">
          <span class="mono">{{ record.refundNo }}</span>
        </template>
        <template v-else-if="column.key === 'orderNo'">
          <span class="mono">{{ record.orderNo }}</span>
        </template>
        <template v-else-if="column.key === 'user'">
          <div>{{ record.userName || '—' }}</div>
          <div class="muted">{{ record.phone || '—' }}（ID: {{ record.userId }}）</div>
        </template>
        <template v-else-if="column.key === 'amount'">
          <span class="amount">¥{{ money(record.refundAmount) }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.statusDesc || record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'reason'">
          <span>{{ record.reason || '—' }}</span>
          <div v-if="record.merchantRemark" class="muted">备注：{{ record.merchantRemark }}</div>
        </template>
        <template v-else-if="column.key === 'images'">
          <template v-if="splitImages(record.images).length">
            <a-image-group>
              <a-image v-for="(url, i) in splitImages(record.images)" :key="i"
                       :src="imgUrl(url)" :width="44" :height="44"
                       style="margin-right: 6px; object-fit: cover; border-radius: 4px" />
            </a-image-group>
          </template>
          <span v-else class="muted">无</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <template v-if="record.status === 'REVIEWING'">
            <a-popconfirm title="确认通过该退款申请？通过后将执行退款并作废对应优惠券。"
                          @confirm="handleApprove(record)">
              <a-button v-permission="'business:coupon:refund'" size="small" type="primary"
                        style="margin-right: 6px">通过</a-button>
            </a-popconfirm>
            <a-button v-permission="'business:coupon:refund'" size="small" danger
                      @click="openReject(record)">拒绝</a-button>
          </template>
          <span v-else class="muted">—</span>
        </template>
      </template>
    </a-table>

    <!-- 拒绝退款：填写商家备注 -->
    <a-modal v-model:visible="rejectVisible" title="拒绝退款申请" :width="520"
             :confirm-loading="rejecting" @ok="handleRejectSubmit">
      <a-form :label-col="{ span: 5 }">
        <a-form-item label="退款单号">
          <span class="mono">{{ rejectTarget?.refundNo }}</span>
        </a-form-item>
        <a-form-item label="用户">{{ rejectTarget?.userName }}（{{ rejectTarget?.phone }}）</a-form-item>
        <a-form-item label="退款金额">¥{{ money(rejectTarget?.refundAmount) }}</a-form-item>
        <a-form-item label="用户原因">{{ rejectTarget?.reason || '—' }}</a-form-item>
        <a-form-item label="拒绝原因" required>
          <a-textarea v-model:value="rejectForm.merchantRemark" :rows="3" :maxlength="200"
                      placeholder="请填写拒绝原因，将展示给用户" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-modal>
</template>

<script setup>
import { ref, watch } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { approveRefund, getRefundList, rejectRefund } from '@/api/coupon.js'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
})
const emit = defineEmits(['update:visible'])

const list = ref([])
const query = ref({ status: undefined })
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const statusOptions = [
  { value: 'REVIEWING', label: '待审核' },
  { value: 'APPROVING', label: '退款中' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'REJECTED', label: '已拒绝' },
]

const columns = [
  { title: '退款单号', key: 'refundNo', width: 200 },
  { title: '秒杀订单号', key: 'orderNo', width: 200 },
  { title: '用户', key: 'user', width: 180 },
  { title: '券名称', dataIndex: 'couponName', key: 'couponName' },
  { title: '退款金额', key: 'amount', width: 110 },
  { title: '状态', key: 'status', width: 100 },
  { title: '原因 / 备注', key: 'reason' },
  { title: '凭证图', key: 'images', width: 160 },
  { title: '申请时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '退款时间', dataIndex: 'refundTime', key: 'refundTime', width: 170 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' },
]

// 拒绝弹窗
const rejectVisible = ref(false)
const rejecting = ref(false)
const rejectTarget = ref(null)
const rejectForm = ref({ merchantRemark: '' })

function loadData() {
  getRefundList({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    status: query.value.status || undefined,
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

function handleApprove(record) {
  approveRefund(record.id)
    .then(() => {
      message.success('已通过退款申请')
      loadData()
    })
    .catch(() => {
      // 拦截器已统一提示，此处不重复弹
    })
}

function openReject(record) {
  rejectTarget.value = record
  rejectForm.value = { merchantRemark: '' }
  rejectVisible.value = true
}

function handleRejectSubmit() {
  if (!rejectForm.value.merchantRemark || !rejectForm.value.merchantRemark.trim()) {
    message.warning('请填写拒绝原因')
    return
  }
  rejecting.value = true
  rejectRefund(rejectTarget.value.id, rejectForm.value.merchantRemark.trim())
    .then(() => {
      message.success('已拒绝退款申请')
      rejectVisible.value = false
      loadData()
    })
    .finally(() => {
      rejecting.value = false
    })
}

function closeModal() {
  emit('update:visible', false)
}

function splitImages(images) {
  if (!images) return []
  return String(images)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

// 相对路径补上网关地址（生产同源，VITE_API_BASE_URL 为空串即可）
function imgUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : (import.meta.env.VITE_API_BASE_URL || '') + path
}

function money(value) {
  if (value === null || value === undefined || value === '') return '-'
  return Number(value).toFixed(2)
}

function statusColor(status) {
  return {
    REVIEWING: 'orange',
    APPROVING: 'blue',
    REFUNDED: 'green',
    REJECTED: 'red',
  }[status] || 'default'
}

watch(() => props.visible, (visible) => {
  if (visible) loadData()
}, { immediate: true })
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin: 20px 0;
}

.mono {
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
}

.muted {
  color: #999;
  font-size: 12px;
}

.amount {
  color: #cf1322;
  font-weight: 600;
}

@media (max-width: 767px) {
  .head {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
