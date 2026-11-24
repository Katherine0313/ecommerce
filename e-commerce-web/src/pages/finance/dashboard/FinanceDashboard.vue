<template>
  <div class="finance-dashboard">
    <div class="page-heading">
      <div>
        <h2>财务看板</h2>
        <p>商品订单与秒杀券的系统内资金概览</p>
      </div>
      <a-tag color="blue">系统实收</a-tag>
    </div>

    <a-alert type="info" show-icon class="scope-alert">
      <template #message>
        总实收 = 商品实收 + 秒杀实收；净收款 = 总实收 - 实际退款。审核状态不影响交易实收，本页不代表微信、支付宝或银行实际到账结果。
      </template>
    </a-alert>

    <div class="filter-bar">
      <a-form layout="inline">
        <a-form-item label="收退款日期">
          <a-range-picker v-model:value="dateRange" :allow-clear="false" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.bizType" style="width: 130px" :options="bizTypeOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.channel" placeholder="全部渠道" allow-clear style="width: 130px"
                    :options="channelOptions" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="loading" @click="loadData">查询</a-button>
          <a-button class="reset-button" @click="handleReset">重置</a-button>
        </a-form-item>
      </a-form>
    </div>

    <a-spin :spinning="loading">
      <div class="metric-grid">
        <div class="metric-card">
          <div class="metric-label">系统总实收</div>
          <div class="metric-value">￥{{ money(cards.grossAmount) }}</div>
          <div class="metric-detail">商品 ￥{{ money(cards.productAmount) }} · 秒杀 ￥{{ money(cards.seckillAmount) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">实际退款</div>
          <div class="metric-value danger">￥{{ money(cards.refundAmount) }}</div>
          <div class="metric-detail">商品 ￥{{ money(cards.productRefundAmount) }} · 秒杀 ￥{{ money(cards.seckillRefundAmount) }}</div>
        </div>
        <div class="metric-card highlight">
          <div class="metric-label">净收款</div>
          <div class="metric-value">￥{{ money(cards.netAmount) }}</div>
          <div class="metric-detail">本期总实收 - 本期实际完成退款</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">已审核收款</div>
          <div class="metric-value audited">￥{{ money(cards.auditedAmount) }}</div>
          <div class="metric-detail">商品收款单审核口径，与交易实收分开</div>
        </div>
      </div>

      <div class="section-heading">
        <span>当前财务待办</span>
        <span class="section-note">全店当前状态，不随上方日期、业务和渠道筛选变化</span>
      </div>
      <div class="todo-summary-grid">
        <div class="todo-summary-card">
          <div>
            <div class="metric-label">待审核收款单</div>
            <div class="todo-count">{{ cards.pendingAuditCount || 0 }} 笔</div>
          </div>
          <div class="todo-amount">￥{{ money(cards.pendingAuditAmount) }}</div>
        </div>
        <div class="todo-summary-card">
          <div>
            <div class="metric-label">未归集收款账户</div>
            <div class="todo-count">{{ alerts.unassignedCount || 0 }} 笔</div>
          </div>
          <div class="todo-amount">￥{{ money(alerts.unassignedAmount) }}</div>
        </div>
        <div class="todo-summary-card">
          <div>
            <div class="metric-label">已支付缺收款单</div>
            <div class="todo-count">{{ alerts.missingReceiptCount || 0 }} 笔</div>
          </div>
          <a-tag v-if="alerts.missingReceiptCount" color="red">需补录</a-tag>
          <a-tag v-else color="green">正常</a-tag>
        </div>
      </div>

      <div class="analysis-grid">
        <a-card title="收退款趋势" :bordered="false" class="trend-card">
          <LineChart :series="trendSeries" />
        </a-card>
        <a-card title="实收渠道构成" :bordered="false" class="channel-card">
          <div v-if="channelRows.length" class="channel-list">
            <div v-for="(item, index) in channelRows" :key="item.channel" class="channel-item">
              <div class="channel-head">
                <span>{{ item.channelDesc }}</span>
                <strong>￥{{ money(item.amount) }}</strong>
              </div>
              <a-progress :percent="item.percent" :show-info="false"
                          :stroke-color="channelColors[index % channelColors.length]" />
              <div class="channel-meta">{{ item.orderCount || 0 }} 笔 · 占总实收 {{ item.percent.toFixed(1) }}%</div>
            </div>
          </div>
          <a-empty v-else description="暂无实收数据" />
        </a-card>
      </div>

      <a-card v-if="dashboard.canViewTodoItems" title="财务待办明细" :bordered="false" class="todo-card">
        <template #extra><span class="section-note">按收款时间升序，最多展示 8 条</span></template>
        <a-table :data-source="dashboard.todoItems" :pagination="false" :scroll="{ x: 1080 }"
                 :row-key="todoRowKey" size="middle">
          <a-table-column title="待办类型" width="120">
            <template #default="{ record }">
              <a-tag :color="todoColor(record.todoType)">{{ record.todoTypeDesc }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="订单号" data-index="orderNo" width="190" />
          <a-table-column title="业务" width="90">
            <template #default="{ record }">{{ bizTypeText(record.bizType) }}</template>
          </a-table-column>
          <a-table-column title="渠道" data-index="channelDesc" width="100" />
          <a-table-column title="金额" align="right" width="130">
            <template #default="{ record }"><span class="amount">￥{{ money(record.amount) }}</span></template>
          </a-table-column>
          <a-table-column title="收款时间" width="170">
            <template #default="{ record }">{{ formatTime(record.paidAt) }}</template>
          </a-table-column>
          <a-table-column title="停留时长" width="100">
            <template #default="{ record }">{{ record.stayHours == null ? '—' : `${record.stayHours} 小时` }}</template>
          </a-table-column>
          <a-table-column title="处理" fixed="right" width="140">
            <template #default="{ record }">
              <a-button v-if="record.todoType === 'PENDING_AUDIT'" type="link" size="small"
                        @click="goReceipt(record, { status: 0 })">查看待审单</a-button>
              <a-button v-else-if="record.todoType === 'UNASSIGNED'" type="link" size="small"
                        @click="goReceipt(record, { accountUnassigned: 1 })">查看未归集</a-button>
              <span v-else class="section-note">通过补录 SQL 处理</span>
            </template>
          </a-table-column>
          <template #emptyText><a-empty description="当前没有财务待办" /></template>
        </a-table>
      </a-card>
      <a-alert v-else type="info" show-icon message="当前角色仅展示全店汇总，单号级财务待办已按权限隐藏。" />
    </a-spin>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import LineChart from '@/components/chart/LineChart.vue'
import { getFinanceDashboard } from '@/api/finance.js'

const router = useRouter()
const loading = ref(false)
const dateRange = ref([dayjs().subtract(29, 'day'), dayjs()])
const query = ref({ bizType: 'ALL', channel: undefined })
const dashboard = ref({ cards: {}, alerts: {}, trend: [], channels: [], todoItems: [], canViewTodoItems: false })

const bizTypeOptions = [
  { value: 'ALL', label: '全部业务' },
  { value: 'PRODUCT', label: '商品订单' },
  { value: 'SECKILL', label: '秒杀券' },
]
const channelOptions = [
  { value: 'WeChat', label: '微信支付' },
  { value: 'Alipay', label: '支付宝' },
  { value: 'BankCard', label: '银行卡' },
]
const channelColors = ['#1677ff', '#722ed1', '#13c2c2', '#fa8c16', '#eb2f96']
const cards = computed(() => dashboard.value.cards || {})
const alerts = computed(() => dashboard.value.alerts || {})
const trendSeries = computed(() => {
  const rows = dashboard.value.trend || []
  return [
    { name: '系统实收', data: rows.map((row) => [row.date.slice(5), Number(row.receivedAmount || 0)]) },
    { name: '净收款', data: rows.map((row) => [row.date.slice(5), Number(row.netAmount || 0)]) },
    { name: '实际退款', type: 'bar', data: rows.map((row) => [row.date.slice(5), Number(row.refundAmount || 0)]) },
  ]
})
const channelRows = computed(() => {
  const total = Number(cards.value.grossAmount || 0)
  return (dashboard.value.channels || []).map((item) => ({
    ...item,
    percent: total > 0 ? Math.min(100, Number(item.amount || 0) / total * 100) : 0,
  }))
})

function buildParams() {
  return {
    startDate: dateRange.value[0].format('YYYY-MM-DD'),
    endDate: dateRange.value[1].format('YYYY-MM-DD'),
    bizType: query.value.bizType,
    channel: query.value.channel || undefined,
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getFinanceDashboard(buildParams())
    dashboard.value = res.data?.data || { cards: {}, alerts: {}, trend: [], channels: [], todoItems: [] }
  } catch (error) {
    console.error('财务看板加载失败', error)
  } finally {
    loading.value = false
  }
}

function handleReset() {
  dateRange.value = [dayjs().subtract(29, 'day'), dayjs()]
  query.value = { bizType: 'ALL', channel: undefined }
  loadData()
}

function goReceipt(record, extraQuery) {
  router.push({ path: '/finance/receipt-list', query: { orderNo: record.orderNo, ...extraQuery } })
}

function todoRowKey(record) {
  return `${record.todoType}-${record.receiptId || record.orderId}`
}

function todoColor(type) {
  return { PENDING_AUDIT: 'orange', UNASSIGNED: 'volcano', MISSING_RECEIPT: 'red' }[type] || 'default'
}

function bizTypeText(type) {
  return type === 'SECKILL' ? '秒杀券' : '商品订单'
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.finance-dashboard {
  min-height: 100%;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-heading h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.page-heading p {
  margin: 4px 0 0;
  color: #8c8c8c;
}

.scope-alert {
  margin-bottom: 16px;
}

.filter-bar {
  padding: 16px 16px 0;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 8px;
}

.reset-button {
  margin-left: 8px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  min-width: 0;
  padding: 20px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.metric-card.highlight {
  background: linear-gradient(135deg, #f0f7ff 0%, #fff 100%);
  border-color: #bae0ff;
}

.metric-label {
  color: #8c8c8c;
  font-size: 13px;
}

.metric-value {
  margin-top: 10px;
  color: #1677ff;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
}

.metric-value.danger {
  color: #cf1322;
}

.metric-value.audited {
  color: #389e0d;
}

.metric-detail {
  min-height: 20px;
  margin-top: 10px;
  overflow: hidden;
  color: #8c8c8c;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 22px 0 12px;
  font-size: 16px;
  font-weight: 600;
}

.section-note {
  color: #8c8c8c;
  font-size: 12px;
  font-weight: 400;
}

.todo-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.todo-summary-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.todo-count {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 600;
}

.todo-amount {
  color: #595959;
  font-weight: 600;
}

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.channel-list {
  padding-top: 4px;
}

.channel-item + .channel-item {
  margin-top: 24px;
}

.channel-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.channel-meta {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.todo-card {
  margin-top: 16px;
}

.amount {
  font-weight: 600;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .analysis-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .metric-grid,
  .todo-summary-grid {
    grid-template-columns: 1fr;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .metric-detail {
    white-space: normal;
  }
}
</style>
