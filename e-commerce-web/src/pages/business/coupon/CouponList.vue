<template>
  <div>
    <!-- 工具栏：名称 / 发放方式 / 状态筛选 + 退款审核 + 新建 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="请输入券名称" allow-clear @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.issueType" style="width: 140px" placeholder="发放方式" allow-clear
                    :options="issueTypeOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.status" style="width: 140px" placeholder="券状态" allow-clear
                    :options="statusOptions" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
        </a-form-item>
      </a-form>
      <div class="head-actions">
        <a-button v-permission="'business:coupon:refund'" @click="refundVisible = true">退款审核</a-button>
        <a-button v-permission="'business:coupon:add'" type="primary" @click="openCreate">新建优惠券</a-button>
      </div>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'issueType'">
          <a-tag :color="record.issueType === 'SECKILL' ? 'orange' : 'blue'">{{ record.issueTypeDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'value'">
          <span>{{ couponValue(record) }}</span>
        </template>
        <template v-else-if="column.key === 'threshold'">
          {{ record.thresholdAmount > 0 ? '满 ¥' + money(record.thresholdAmount) : '无门槛' }}
        </template>
        <template v-else-if="column.key === 'memberLevel'">
          {{ memberLevelText(record.minMemberLevel) }}
        </template>
        <template v-else-if="column.key === 'seckill'">
          <template v-if="record.issueType === 'SECKILL'">
            <div>秒杀价 ¥{{ money(record.seckillPrice) }}</div>
            <div class="muted">{{ record.seckillBeginTime }} ~ {{ record.seckillEndTime }}</div>
          </template>
          <template v-else>—</template>
        </template>
        <template v-else-if="column.key === 'receiveTime'">
          <template v-if="record.issueType === 'SECKILL'">—</template>
          <template v-else>{{ record.receiveStart }} ~ {{ record.receiveEnd }}</template>
        </template>
        <template v-else-if="column.key === 'quantity'">
          <template v-if="record.issueType === 'SECKILL'">
            {{ record.remainQuantity ?? 0 }} / {{ record.totalQuantity }}
          </template>
          <template v-else>
            {{ record.issuedCount || 0 }} / {{ record.totalQuantity }}
          </template>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.statusDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button size="small" @click="openDetail(record)" style="margin-right: 6px">详情</a-button>
          <a-button v-if="record.status === 'PENDING'" v-permission="'business:coupon:edit'"
                    size="small" type="primary" style="margin-right: 6px" @click="openEdit(record)">编辑</a-button>
          <a-button v-if="canGrant(record) && record.issueType !== 'SECKILL'" v-permission="'business:coupon:grant'"
                    size="small" type="primary" style="margin-right: 6px" @click="openGrant(record)">发券</a-button>
          <a-popconfirm v-if="record.status === 'PENDING' || record.status === 'ACTIVE'"
                        title="确定停止发放该券？" @confirm="handleStop(record)">
            <a-button v-permission="'business:coupon:edit'" size="small" danger
                      style="margin-right: 6px">停止</a-button>
          </a-popconfirm>
          <a-popconfirm v-if="record.status === 'PENDING'" title="确定删除该券？" @confirm="handleDelete(record)">
            <a-button v-permission="'business:coupon:remove'" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 新建 / 编辑 -->
    <CouponFormModal v-model:visible="formVisible" :coupon="editingCoupon" :products="products"
                     :categories="categories" :member-levels="memberLevels" @saved="loadData" />

    <!-- 发券 -->
    <a-modal v-model:visible="grantVisible" title="发放优惠券" :width="560" :confirm-loading="granting"
             @ok="handleGrant">
      <a-form :label-col="{ span: 5 }">
        <a-form-item label="券名称">{{ grantTarget?.name }}</a-form-item>
        <a-form-item label="发放对象">
          <a-radio-group v-model:value="grantForm.allUsers">
            <a-radio :value="false">指定用户</a-radio>
            <a-radio :value="true">全部用户</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="!grantForm.allUsers" label="用户 ID">
          <a-textarea v-model:value="grantForm.userIds" :rows="4"
                      placeholder="多个用户 ID 用逗号、空格或换行分隔" />
        </a-form-item>
        <a-form-item label="剩余库存">{{ grantTarget?.remainQuantity || 0 }} 张</a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情 / 记录 -->
    <a-modal v-model:visible="detailVisible" title="优惠券详情" :width="820" :footer="null">
      <template v-if="detail">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="券名称">{{ detail.name }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(detail.status)">{{ detail.statusDesc }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="类型">{{ detail.typeDesc }}：{{ couponValue(detail) }}</a-descriptions-item>
          <a-descriptions-item label="适用范围">{{ detail.scopeTypeDesc }} · {{ detail.targetGroupDesc }}</a-descriptions-item>
          <a-descriptions-item label="领取时间" :span="2">{{ detail.receiveStart }} ~ {{ detail.receiveEnd }}</a-descriptions-item>
          <a-descriptions-item label="有效期" :span="2">{{ validityText(detail) }}</a-descriptions-item>
          <a-descriptions-item label="发行 / 剩余">{{ detail.totalQuantity }} / {{ detail.remainQuantity }}</a-descriptions-item>
          <a-descriptions-item label="领取 / 使用">{{ detail.issuedCount || 0 }} / {{ detail.usedCount || 0 }}</a-descriptions-item>
          <a-descriptions-item label="累计优惠">¥{{ money(detail.usedDiscount) }}</a-descriptions-item>
          <a-descriptions-item label="使用说明">{{ detail.description || '—' }}</a-descriptions-item>
        </a-descriptions>

        <div class="detail-block">
          <div class="detail-title">领取 / 使用记录</div>
          <a-table :dataSource="records" :columns="recordColumns" size="small" row-key="id"
                   :pagination="recordPagination" @change="handleRecordPageChange" />
        </div>
      </template>
    </a-modal>

    <CouponSeckillRefundList v-model:visible="refundVisible" />
  </div>
</template>

<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  deleteCoupon,
  getCouponDetail,
  getCouponList,
  getCouponRecords,
  grantCoupon,
  stopCoupon,
} from '@/api/coupon.js'
import { getCategoryTree, getGoodsList } from '@/api/products.js'
import { getMemberLevels } from '@/api/member.js'
import { maskPhone } from '@/utils/mask.js'
import CouponSeckillRefundList from './CouponSeckillRefundList.vue'
import CouponFormModal from './CouponFormModal.vue'

const list = ref([])
const formVisible = ref(false)
const editingCoupon = ref(null)
const query = ref({ keyword: '', issueType: undefined, status: undefined })
const products = ref([])
const categories = ref([])
const memberLevels = ref([])

const grantVisible = ref(false)
const granting = ref(false)
const grantTarget = ref(null)
const grantForm = ref({ allUsers: false, userIds: '' })

const detailVisible = ref(false)
const refundVisible = ref(false)
const detail = ref(null)
const records = ref([])
const recordPagination = ref({ current: 1, pageSize: 10, total: 0, showSizeChanger: false })

const statusOptions = [
  { value: 'PENDING', label: '未开始' },
  { value: 'ACTIVE', label: '发放中' },
  { value: 'ENDED', label: '已结束' },
  { value: 'STOPPED', label: '已停止' },
]
const issueTypeOptions = [
  { value: 'RECEIVE', label: '普通券' },
  { value: 'SECKILL', label: '秒杀券' },
]
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '券名称', dataIndex: 'name', key: 'name' },
  { title: '发放方式', key: 'issueType', width: 100 },
  { title: '优惠', key: 'value' },
  { title: '门槛', key: 'threshold' },
  { title: '范围', dataIndex: 'scopeTypeDesc', key: 'scopeTypeDesc'},
  { title: '会员门槛', key: 'memberLevel' },
  { title: '秒杀活动', key: 'seckill', width: 200 },
  { title: '领取时间', key: 'receiveTime', width: 170 },
  { title: '库存 / 领取', key: 'quantity' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 300 },
]
const recordColumns = [
  { title: '券码', dataIndex: 'code', key: 'code', width: 190 },
  { title: '用户', dataIndex: 'userName', key: 'userName' },
  { title: '手机号', dataIndex: 'phone', key: 'phone', width: 130, customRender: ({ text }) => maskPhone(text) },
  { title: '状态', dataIndex: 'statusDesc', key: 'statusDesc', width: 90 },
  { title: '领取时间', dataIndex: 'receiveTime', key: 'receiveTime', width: 170 },
  { title: '使用时间', dataIndex: 'usedTime', key: 'usedTime', width: 170 },
]

function loadData() {
  getCouponList({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    issueType: query.value.issueType || undefined,
    status: query.value.status || undefined,
  }).then((res) => {
    list.value = res.data.data.list || []
    pagination.value.total = res.data.data.total || 0
  })
}

function loadOptions() {
  getGoodsList({ page: 1, pageSize: 500 }).then((res) => {
    products.value = res.data.data.list || []
  })
  getCategoryTree().then((res) => {
    categories.value = res.data.data || []
  })
  getMemberLevels().then((res) => {
    memberLevels.value = res.data.data || []
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
  editingCoupon.value = null
  formVisible.value = true
}

function openEdit(record) {
  getCouponDetail(record.id).then((res) => {
    editingCoupon.value = res.data.data
    formVisible.value = true
  })
}

function openGrant(record) {
  grantTarget.value = record
  grantForm.value = { allUsers: false, userIds: '' }
  grantVisible.value = true
}

function handleGrant() {
  const userIds = grantForm.value.userIds
    .split(/[\s,，]+/)
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v) && v > 0)
  if (!grantForm.value.allUsers && !userIds.length) {
    message.warning('请输入用户 ID')
    return
  }
  granting.value = true
  grantCoupon(grantTarget.value.id, {
    allUsers: grantForm.value.allUsers,
    userIds,
  })
    .then((res) => {
      const result = res.data.data || {}
      message.success(`发放成功 ${result.granted || 0} 张，跳过 ${result.skipped || 0} 张，剩余 ${result.remainQuantity || 0} 张`)
      grantVisible.value = false
      loadData()
    })
    .finally(() => {
      granting.value = false
    })
}

function openDetail(record) {
  getCouponDetail(record.id).then((res) => {
    detail.value = res.data.data
    recordPagination.value.current = 1
    detailVisible.value = true
    loadRecords(record.id)
  })
}

function loadRecords(templateId) {
  getCouponRecords(templateId, {
    page: recordPagination.value.current,
    pageSize: recordPagination.value.pageSize,
  }).then((res) => {
    records.value = res.data.data.list || []
    recordPagination.value.total = res.data.data.total || 0
  })
}

function handleRecordPageChange(pager) {
  recordPagination.value.current = pager.current
  loadRecords(detail.value.id)
}

function handleStop(record) {
  stopCoupon(record.id).then(() => {
    message.success('已停止发放')
    loadData()
  })
}

function handleDelete(record) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后不可恢复，确定要删除该优惠券吗？',
    okText: '确定删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => deleteCoupon(record.id).then(() => {
      message.success('优惠券已删除')
      loadData()
    }),
  })
}

function canGrant(record) {
  // 后台发券也必须在领取时间内，未开始 / 已结束都不允许
  return record.status === 'ACTIVE'
}

function couponValue(record) {
  if (record.type === 'DISCOUNT') {
    const rate = Number(record.discountRate || 0)
    const cap = record.maxDiscount ? `，最高减 ¥${money(record.maxDiscount)}` : ''
    return `${(rate * 10).toFixed(1)} 折${cap}`
  }
  if (record.issueType === 'SECKILL' && record.seckillPrice !== null && record.seckillPrice !== undefined) {
    // 秒杀券实际优惠 = 券面额 - 秒杀支付价
    return `减 ¥${money(Number(record.faceValue || 0) - Number(record.seckillPrice))}`
  }
  return `减 ¥${money(record.faceValue)}`
}

function validityText(record) {
  if (record.validType === 'RECEIVE_DAYS') {
    return `领取后 ${record.validDays} 天内有效`
  }
  return `${record.validStart} ~ ${record.validEnd}`
}

function statusColor(status) {
  return { PENDING: 'blue', ACTIVE: 'green', ENDED: 'default', STOPPED: 'red' }[status] || 'default'
}

function money(value) {
  if (value === null || value === undefined || value === '') return '-'
  return Number(value).toFixed(2)
}

function memberLevelText(levelId) {
  if (!levelId) return '不限'
  const level = memberLevels.value.find((item) => item.id === levelId)
  return level ? level.name : `等级 ${levelId}`
}

onMounted(() => {
  loadOptions()
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
  gap: 12px;
  align-items: center;
  flex-shrink: 0;
}

.muted {
  color: #999;
  font-size: 12px;
}

.detail-block {
  margin-top: 16px;
}

.detail-title {
  margin-bottom: 8px;
  font-weight: 600;
}

@media (max-width: 767px) {
  .head {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
