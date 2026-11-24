<template>
  <div>
    <!-- 4 张统计卡（渐变背景） -->
    <a-row :gutter="16">
      <a-col :xs="24" :sm="12" :md="6" v-for="(card, idx) in statCards" :key="idx">
        <div :class="['stat-card', card.bgClass]">
          <div class="stat-text">
            <div class="stat-title">{{ card.title }}</div>
            <div class="stat-value">{{ formatNum(card.value) }}</div>
            <div v-if="card.change" class="stat-change">{{ card.change }}</div>
          </div>
          <div class="stat-icon">
            <component :is="card.iconComponent" />
          </div>
        </div>
      </a-col>
    </a-row>

    <!-- 筛选 Tab + 折线面积图 -->
    <a-card :bordered="false" style="margin-top: 16px">
      <template #title>
        <div class="card-header">
          <span>出入库趋势</span>
          <a-radio-group v-model:value="trendDays" size="small" button-style="solid" @change="onTrendDaysChange">
            <a-radio-button :value="7">7 天</a-radio-button>
            <a-radio-button :value="30">30 天</a-radio-button>
            <a-radio-button :value="90">90 天</a-radio-button>
          </a-radio-group>
        </div>
      </template>
      <div ref="trendRef" style="width: 100%; height: 320px"></div>
      <a-empty v-if="!trendData.length" description="暂无库存流水数据" />
    </a-card>

    <!-- 饼图 + SKU 分布 -->
    <a-row :gutter="16" style="margin-top: 16px">
      <a-col :xs="24" :md="10">
        <a-card title="业务类型占比" :bordered="false">
          <div ref="pieRef" style="width: 100%; height: 300px"></div>
          <a-empty v-if="!typeDistData.length" description="暂无数据" />
        </a-card>
      </a-col>
      <a-col :xs="24" :md="14">
        <a-card title="SKU 库存分布 TOP 15" :bordered="false">
          <div ref="skuDistRef" style="width: 100%; height: 300px"></div>
          <a-empty v-if="!skuDistData.length" description="暂无数据" />
        </a-card>
      </a-col>
    </a-row>

    <!-- 低库存 SKU（带风险级别） -->
    <a-card title="低库存 SKU TOP 20" :bordered="false" style="margin-top: 16px">
      <a-table
          :dataSource="lowStockList"
          :loading="lowLoading"
          :pagination="false"
          :rowKey="(r) => r.skuId"
          size="middle"
          bordered
      >
        <a-table-column title="风险级别" width="90" align="center">
          <template #default="{ record }">
            <a-tag :color="riskColor(record.riskLevel)">{{ record.riskLabel }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="商品名" dataIndex="productName" />
        <a-table-column title="SKU 规格" dataIndex="specText" />
        <a-table-column title="当前库存" dataIndex="stock" align="right" />
        <a-table-column title="安全下限" dataIndex="minStock" align="right" />
        <a-table-column title="当前/下限" align="right">
          <template #default="{ record }">
            <span>{{ record.stockRate != null ? (record.stockRate * 100).toFixed(0) + '%' : '—' }}</span>
          </template>
        </a-table-column>
        <a-table-column title="今日出库" dataIndex="todayOutQty" align="right" />
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { InboxOutlined, DownloadOutlined, UploadOutlined, WarningOutlined, DatabaseOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDashboard, getTrend, getLowStockSku, setSkuAlert, getTypeDist, getSkuDist } from '@/api/stock.js'
import { message } from 'ant-design-vue'

echarts.use([LineChart, PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

// ============ 看板 ============
const dashboard = ref({})
const statCards = computed(() => [
  {
    title: '总库存件数',
    value: dashboard.value.totalStock ?? 0,
    iconComponent: DatabaseOutlined,
    bgClass: 'bg-blue',
  },
  {
    title: 'SKU 总数',
    value: dashboard.value.skuTotal ?? 0,
    iconComponent: InboxOutlined,
    bgClass: 'bg-green',
  },
  {
    title: '今日入库',
    value: dashboard.value.todayIn ?? 0,
    change: '+今日',
    iconComponent: DownloadOutlined,
    bgClass: 'bg-orange',
  },
  {
    title: '今日出库',
    value: dashboard.value.todayOut ?? 0,
    change: '+今日',
    iconComponent: UploadOutlined,
    bgClass: 'bg-red',
  },
])

const formatNum = (n) => {
  n = Number(n) || 0
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return n.toLocaleString()
}

// ============ 趋势图（折线 + 面积填充） ============
const trendDays = ref(30)
const trendRef = ref(null)
let trendChart = null
const trendData = ref([])

const onTrendDaysChange = () => {
  getTrend(trendDays.value).then(res => {
    trendData.value = res.data?.data || []
    refreshTrendChart()
  }).catch(() => {})
}

const initTrendChart = () => {
  if (!trendRef.value) return
  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'line' } },
    legend: { data: ['入库', '出库'] },
    grid: { top: 40, left: 50, right: 20, bottom: 40, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: trendData.value.map(d => d.date) },
    yAxis: { type: 'value' },
    series: [
      {
        name: '入库',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#52c41a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(82,196,26,0.35)' },
            { offset: 1, color: 'rgba(82,196,26,0.02)' },
          ]),
        },
        data: trendData.value.map(d => d.in),
      },
      {
        name: '出库',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color: '#fa8c16' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(250,140,22,0.35)' },
            { offset: 1, color: 'rgba(250,140,22,0.02)' },
          ]),
        },
        data: trendData.value.map(d => d.out),
      },
    ],
  })
}

const refreshTrendChart = () => {
  if (!trendChart) return
  trendChart.setOption({
    xAxis: { data: trendData.value.map(d => d.date) },
    series: [
      { data: trendData.value.map(d => d.in) },
      { data: trendData.value.map(d => d.out) },
    ],
  })
}

// ============ 饼图（业务类型占比） ============
const pieRef = ref(null)
let pieChart = null
const typeDistData = ref([])

const initPieChart = () => {
  if (!pieRef.value) return
  pieChart = echarts.init(pieRef.value)
  const colors = { IN: '#52c41a', OUT: '#fa8c16', ADJUST: '#1890ff' }
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 笔 / {d}%' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      label: { formatter: '{b}\n{d}%' },
      data: typeDistData.value.map(d => ({
        name: d.label,
        value: d.count,
        itemStyle: { color: colors[d.type] || '#999' },
      })),
    }],
  })
}

// ============ SKU 分布柱状图（水平） ============
const skuDistRef = ref(null)
let skuDistChart = null
const skuDistData = ref([])

const skuStockColor = (v) => {
  if (v > 500) return '#f5222d'
  if (v > 200) return '#fa8c16'
  return '#8c8c8c'
}

const initSkuDistChart = () => {
  if (!skuDistRef.value) return
  skuDistChart = echarts.init(skuDistRef.value)
  const data = skuDistData.value
  const labels = data.map(d => d.productName + (d.specText ? ' / ' + d.specText : ''))
  const values = data.map(d => d.stock)
  skuDistChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 10, left: 160, right: 30, bottom: 10, containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: labels.slice().reverse(), axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar',
      barWidth: 14,
      data: values.slice().reverse().map(v => ({
        value: v,
        itemStyle: {
          color: skuStockColor(v),
          borderRadius: [0, 3, 3, 0],
        },
      })),
      label: { show: true, position: 'right', formatter: '{c}' },
    }],
  })
}

// ============ resize 统一处理 ============
const handleResize = () => {
  trendChart?.resize()
  pieChart?.resize()
  skuDistChart?.resize()
}
window.addEventListener('resize', handleResize)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose(); trendChart = null
  pieChart?.dispose(); pieChart = null
  skuDistChart?.dispose(); skuDistChart = null
})

// ============ 低库存列表 ============
const lowStockList = ref([])
const lowLoading = ref(false)

const riskColor = (level) => ({ urgent: 'red', high: 'orange', mid: 'gold' }[level] || 'default')

// ============ 初始化 ============
const loadAll = () => {
  getDashboard().then(res => { dashboard.value = res.data?.data || {} }).catch(() => {})

  getTrend(trendDays.value).then(res => {
    trendData.value = res.data?.data || []
    nextTick(() => { if (!trendChart) initTrendChart(); else refreshTrendChart() })
  }).catch(() => {})

  getTypeDist(30).then(res => {
    typeDistData.value = res.data?.data || []
    nextTick(() => { if (!pieChart) initPieChart(); else { pieChart?.dispose(); pieChart = null; initPieChart() } })
  }).catch(() => {})

  getSkuDist(15).then(res => {
    skuDistData.value = res.data?.data || []
    nextTick(() => { if (!skuDistChart) initSkuDistChart(); else { skuDistChart?.dispose(); skuDistChart = null; initSkuDistChart() } })
  }).catch(() => {})

  loadLowStock()
}

const loadLowStock = () => {
  lowLoading.value = true
  getLowStockSku(20)
      .then(res => { lowStockList.value = res.data?.data || [] })
      .catch(() => { lowStockList.value = [] })
      .finally(() => { lowLoading.value = false })
}

onMounted(loadAll)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-card {
  position: relative;
  height: 108px;
  border-radius: 8px;
  padding: 16px 20px;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  overflow: hidden;
}
.stat-card.bg-blue   { background: linear-gradient(135deg, #1890ff, #36cfc9); }
.stat-card.bg-green  { background: linear-gradient(135deg, #52c41a, #95de64); }
.stat-card.bg-orange { background: linear-gradient(135deg, #fa8c16, #ffc53d); }
.stat-card.bg-red    { background: linear-gradient(135deg, #f5222d, #ff7875); }

.stat-title { font-size: 13px; opacity: 0.9; margin-bottom: 4px; }
.stat-value { font-size: 32px; font-weight: 600; line-height: 1.1; }
.stat-change { font-size: 12px; opacity: 0.8; margin-top: 4px; }

.stat-icon {
  font-size: 64px;
  opacity: 0.25;
  line-height: 1;
  transform: translateY(4px);
}
</style>
