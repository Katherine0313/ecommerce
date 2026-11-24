<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="4">
        <a-card size="small" title="数据库连接">
          <div class="metric">{{ overview.threadsConnected ?? '-' }} / {{ overview.maxConnections ?? '-' }}</div>
          <div class="sub">运行中 {{ overview.threadsRunning ?? '-' }} · 使用率 {{ formatPercent(overview.connectionUsagePercent) }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="QPS">
          <div class="metric">{{ overview.qps ?? 0 }}</div>
          <div class="sub">缓冲池命中率 {{ formatPercent(overview.bufferHitRate) }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="今日慢 SQL">
          <div class="metric" :class="{ danger: (overview.todaySlowSql || 0) > 0 }">{{ overview.todaySlowSql ?? 0 }}</div>
          <div class="sub">阈值 {{ overview.slowThresholdMs ?? 300 }} ms · 累计 {{ overview.slowQueriesTotal ?? 0 }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="库表 / 容量">
          <div class="metric">{{ overview.tableCount ?? 0 }}</div>
          <div class="sub">{{ overview.totalSizeHuman || '-' }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="采集 / 丢弃">
          <div class="metric">{{ overview.collector?.recorded ?? 0 }}</div>
          <div class="sub" :class="{ danger: (overview.collector?.dropped || 0) > 0 }">
            丢弃 {{ overview.collector?.dropped ?? 0 }} 条（限流或队列满）
          </div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="服务端慢日志">
          <div class="metric">{{ overview.slowQueryLog ? '已开启' : '未开启' }}</div>
          <div class="sub">long_query_time {{ overview.longQueryTime || '-' }}s</div>
        </a-card>
      </a-col>
    </a-row>

    <div class="toolbar">
      <a-space>
        <a-select v-model:value="query.hours" style="width: 120px" @change="reloadAll">
          <a-select-option :value="1">近 1 小时</a-select-option>
          <a-select-option :value="6">近 6 小时</a-select-option>
          <a-select-option :value="24">近 24 小时</a-select-option>
          <a-select-option :value="168">近 7 天</a-select-option>
        </a-select>
        <span class="tip">口径：Java 侧 SQL 执行耗时（含网络往返），不含连接池等待；详情见「连接池状态」的 pending</span>
      </a-space>
      <a-space>
        <a-button :loading="loading" @click="reloadAll">刷新</a-button>
        <a-popconfirm title="将删除早于保留期的慢 SQL 记录，确定继续？" ok-text="确定清空" ok-type="danger"
                      @confirm="handleClear">
          <a-button v-permission="'monitor:db:clear'" danger>清空历史</a-button>
        </a-popconfirm>
      </a-space>
    </div>

    <a-card size="small" title="慢 SQL 趋势（条数 / 平均耗时）" class="block">
      <div ref="trendRef" class="chart"></div>
    </a-card>

    <a-row :gutter="16" class="block">
      <a-col :span="14">
        <a-card size="small" title="Top 10 慢 SQL（按指纹聚合）">
          <a-table :dataSource="top" :columns="topColumns" :pagination="false" row-key="fingerprint"
                   size="small" :loading="topLoading">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'sqlText'">
                <span class="sql-preview" :title="record.sqlText" @click="openSql(record)">
                  {{ record.sqlText }}
                </span>
              </template>
              <template v-else-if="column.key === 'avgCostMs'">
                <span :class="{ danger: record.avgCostMs >= (overview.slowThresholdMs || 300) * 2 }">
                  {{ record.avgCostMs }} ms
                </span>
              </template>
              <template v-else-if="column.key === 'lastAt'">
                {{ record.lastAt || '-' }}
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :span="10">
        <a-card size="small" title="表容量 Top 10">
          <div ref="tableRef" class="chart"></div>
        </a-card>
      </a-col>
    </a-row>

    <a-card size="small" title="连接池状态（pending > 0 说明请求在排队拿连接）" class="block">
      <a-table :dataSource="pools" :columns="poolColumns" :pagination="false" row-key="service"
               size="small" :loading="poolLoading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'pending'">
            <a-tag :color="record.pending > 0 ? 'red' : 'green'">{{ record.pending }}</a-tag>
          </template>
          <template v-else-if="column.key === 'available'">
            <a-tag :color="record.available ? 'blue' : 'default'">
              {{ record.available ? '实时' : '未暴露指标' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card size="small" title="慢 SQL 明细" class="block">
      <div class="filter">
        <a-select v-model:value="query.service" style="width: 170px" allow-clear placeholder="全部服务"
                  @change="reloadDetail(1)">
          <a-select-option v-for="item in serviceOptions" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
        <a-input-number v-model:value="query.minCost" style="width: 150px" :min="0" placeholder="最低耗时 ms" />
        <a-button type="primary" :loading="detailLoading" @click="reloadDetail(1)">查询</a-button>
      </div>

      <a-table :dataSource="detail.list" :columns="detailColumns" :pagination="pagination" row-key="id"
               size="small" :loading="detailLoading" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'costMs'">
            <span :class="{ danger: record.costMs >= (overview.slowThresholdMs || 300) * 2 }">{{ record.costMs }} ms</span>
          </template>
          <template v-else-if="column.key === 'sqlText'">
            <span class="sql-preview" :title="record.sqlText" @click="openSql(record)">{{ record.sqlText }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openSql(record)">查看</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:visible="sqlVisible" title="完整 SQL（已归一化，不含参数值）" :width="860" :footer="null">
      <div class="sql-meta">
        <span>{{ currentSql.serviceName }}</span>
        <span>{{ currentSql.mapperMethod }}</span>
        <span>{{ currentSql.sqlType }}</span>
        <span>{{ currentSql.costMs }} ms</span>
        <span>指纹 {{ currentSql.sqlFingerprint }}</span>
      </div>
      <pre class="sql-full">{{ currentSql.sqlText }}</pre>
    </a-modal>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  clearSlowSqls,
  getDatabaseOverview,
  getDatabasePools,
  getSlowSqlTop,
  getSlowSqlTrend,
  getSlowSqls,
  getTableSizes,
} from '@/api/monitor.js'

echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const serviceOptions = [
  'auth-service',
  'product-service',
  'order-service',
  'aftersale-service',
  'user-service',
  'system-service',
]

const query = reactive({ hours: 24, service: undefined, minCost: undefined })
const overview = ref({})
const loading = ref(false)

const top = ref([])
const topLoading = ref(false)
const trend = ref([])
const pools = ref([])
const poolLoading = ref(false)
const tables = ref([])
const detail = reactive({ list: [], total: 0 })
const detailLoading = ref(false)
const pagination = reactive({ current: 1, pageSize: 20, total: 0, showSizeChanger: false })

const sqlVisible = ref(false)
const currentSql = ref({})

const topColumns = [
  { title: 'SQL', key: 'sqlText', ellipsis: true },
  { title: '服务', dataIndex: 'services', key: 'services', width: 180 },
  { title: '次数', dataIndex: 'count', key: 'count', width: 70 },
  { title: '平均耗时', key: 'avgCostMs', width: 100 },
  { title: '最大耗时', dataIndex: 'maxCostMs', key: 'maxCostMs', width: 100, customRender: ({ text }) => `${text} ms` },
  { title: '最近出现', key: 'lastAt', width: 160 },
]

const poolColumns = [
  { title: '服务', dataIndex: 'service', key: 'service' },
  { title: '活跃', dataIndex: 'active', key: 'active', width: 80 },
  { title: '空闲', dataIndex: 'idle', key: 'idle', width: 80 },
  { title: '等待', key: 'pending', width: 80 },
  { title: '上限', dataIndex: 'max', key: 'max', width: 80 },
  { title: '数据源', key: 'available', width: 110 },
]

const detailColumns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 165 },
  { title: '服务', dataIndex: 'serviceName', key: 'serviceName', width: 140 },
  { title: 'Mapper 方法', dataIndex: 'mapperMethod', key: 'mapperMethod', width: 240, ellipsis: true },
  { title: '类型', dataIndex: 'sqlType', key: 'sqlType', width: 80 },
  { title: '耗时', key: 'costMs', width: 90 },
  { title: '行数', dataIndex: 'rowCount', key: 'rowCount', width: 70 },
  { title: 'SQL', key: 'sqlText', ellipsis: true },
  { title: '操作', key: 'action', width: 70 },
]

const trendRef = ref(null)
const tableRef = ref(null)
let trendChart = null
let tableChart = null
let timer = null

function formatPercent(value) {
  return `${Number(value || 0).toFixed(2)}%`
}

function loadOverview() {
  getDatabaseOverview()
    .then((res) => {
      overview.value = res.data.data || {}
    })
    .catch(() => message.error('获取数据库概览失败'))
}

function loadTrend() {
  getSlowSqlTrend({ hours: query.hours })
    .then((res) => {
      trend.value = res.data.data || []
      renderTrend()
    })
    .catch(() => {})
}

function loadTop() {
  topLoading.value = true
  getSlowSqlTop({ hours: query.hours, limit: 10 })
    .then((res) => {
      top.value = res.data.data || []
    })
    .catch(() => message.error('获取慢 SQL Top 失败'))
    .finally(() => {
      topLoading.value = false
    })
}

function loadPools() {
  poolLoading.value = true
  getDatabasePools()
    .then((res) => {
      pools.value = res.data.data || []
    })
    .catch(() => {})
    .finally(() => {
      poolLoading.value = false
    })
}

function loadTables() {
  getTableSizes({ limit: 10 })
    .then((res) => {
      tables.value = res.data.data || []
      renderTables()
    })
    .catch(() => {})
}

function reloadDetail(page) {
  pagination.current = page || 1
  detailLoading.value = true
  getSlowSqls({
    hours: query.hours,
    service: query.service || undefined,
    minCost: query.minCost || undefined,
    page: pagination.current,
    pageSize: pagination.pageSize,
  })
    .then((res) => {
      const data = res.data.data || {}
      detail.list = data.list || []
      detail.total = data.total || 0
      pagination.total = data.total || 0
    })
    .catch(() => message.error('获取慢 SQL 明细失败'))
    .finally(() => {
      detailLoading.value = false
    })
}

function handleTableChange(page) {
  reloadDetail(page.current)
}

function reloadAll() {
  loading.value = true
  loadOverview()
  loadTrend()
  loadTop()
  loadPools()
  loadTables()
  reloadDetail(1)
  setTimeout(() => {
    loading.value = false
  }, 300)
}

function handleClear() {
  clearSlowSqls({ beforeDays: overview.value.retainDays || 7 })
    .then((res) => {
      message.success(`已清理 ${res.data.data?.deleted ?? 0} 条`)
      reloadAll()
    })
    .catch((error) => message.error(error.response?.data?.message || '清空失败'))
}

function openSql(record) {
  currentSql.value = record
  sqlVisible.value = true
}

function renderTrend() {
  if (!trendRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendRef.value)
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['慢 SQL 条数', '平均耗时'] },
    grid: { top: 40, left: 50, right: 55, bottom: 40, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: trend.value.map((d) => d.hour) },
    yAxis: [
      { type: 'value', name: '条数', minInterval: 1 },
      { type: 'value', name: 'ms' },
    ],
    series: [
      {
        name: '慢 SQL 条数',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#f5222d' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245,34,45,0.35)' },
            { offset: 1, color: 'rgba(245,34,45,0.02)' },
          ]),
        },
        data: trend.value.map((d) => d.count),
      },
      {
        name: '平均耗时',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#1890ff' },
        data: trend.value.map((d) => d.avgCostMs),
      },
    ],
  })
}

function renderTables() {
  if (!tableRef.value) return
  if (!tableChart) {
    tableChart = echarts.init(tableRef.value)
  }
  const data = tables.value
  tableChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (items) => {
        const item = items[0]
        const row = data[data.length - 1 - item.dataIndex]
        return `${row.name}<br/>${row.sizeHuman} · ${Number(row.rows || 0).toLocaleString()} 行`
      },
    },
    grid: { top: 10, left: 150, right: 40, bottom: 10, containLabel: true },
    xAxis: { type: 'value', axisLabel: { formatter: (v) => `${Math.round(v / 1024 / 1024)}MB` } },
    yAxis: { type: 'category', data: data.map((d) => d.name).reverse(), axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar',
      barWidth: 14,
      data: data.map((d) => d.totalLength).reverse(),
      itemStyle: { color: '#597ef7', borderRadius: [0, 3, 3, 0] },
    }],
  })
}

onMounted(() => {
  reloadAll()
  // 指标卡与趋势 5 秒刷新，明细表只在用户操作时刷新，避免打断查看
  timer = setInterval(() => {
    loadOverview()
    loadTrend()
  }, 5000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  trendChart?.dispose()
  tableChart?.dispose()
})
</script>

<style scoped>
.metric {
  font-size: 20px;
  font-weight: 600;
}
.sub {
  color: #888;
  font-size: 12px;
}
.danger {
  color: #cf1322;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  gap: 12px;
  flex-wrap: wrap;
}
.tip {
  color: #999;
  font-size: 12px;
}
.block {
  margin-top: 16px;
}
.chart {
  height: 260px;
}
.filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.sql-preview {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  cursor: pointer;
  color: #1890ff;
}
.sql-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #666;
  font-size: 12px;
  margin-bottom: 12px;
}
.sql-full {
  background: #f6f8fa;
  border-radius: 6px;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 420px;
  overflow: auto;
}
</style>
