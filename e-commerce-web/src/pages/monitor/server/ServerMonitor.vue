<template>
  <div>
    <div class="head">
      <span class="tip">每 5 秒自动刷新，页面切到后台时暂停</span>
      <a-button type="primary" :loading="loading" @click="load">刷新</a-button>
    </div>

    <a-spin :spinning="loading">
      <div v-if="alerts.length" class="alerts">
        <a-alert v-for="alert in alerts" :key="alert.type" show-icon
                 :type="alert.level === 'critical' ? 'error' : 'warning'"
                 :message="`${alert.name} ${formatPercent(alert.value)}，已超过阈值 ${formatPercent(alert.threshold)}（自 ${alert.since || '-'} 起）`" />
      </div>

      <a-row :gutter="16">
        <a-col :span="6">
          <a-card size="small" title="主机 CPU">
            <div class="metric">{{ formatPercent(host.cpuUsagePercent) }}</div>
            <a-progress :percent="clamp(host.cpuUsagePercent)" :show-info="false" status="active" />
            <div class="sub">
              {{ host.cpuCores || 0 }} 核 · 用户 {{ formatPercent(host.cpuDetail?.userPercent) }} ·
              系统 {{ formatPercent(host.cpuDetail?.systemPercent) }} ·
              等待 {{ formatPercent(host.cpuDetail?.iowaitPercent) }}
            </div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" title="主机内存">
            <div class="metric">{{ formatPercent(host.memoryUsagePercent) }}</div>
            <a-progress :percent="clamp(host.memoryUsagePercent)" :show-info="false" />
            <div class="sub">{{ formatBytes(host.memoryUsed) }} / {{ formatBytes(host.memoryTotal) }}</div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" title="JVM 堆内存">
            <div class="metric">{{ formatPercent(jvm.heapUsagePercent) }}</div>
            <a-progress :percent="clamp(jvm.heapUsagePercent)" :show-info="false" />
            <div class="sub">{{ formatBytes(jvm.heapUsed) }} / {{ formatBytes(jvm.heapMax) }}</div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" title="运行时长">
            <div class="metric">{{ formatUptime(host.uptimeSeconds) }}</div>
            <div class="sub">线程 {{ jvm.threadCount || 0 }} · GC {{ jvm.gcCount || 0 }} 次 / {{ jvm.gcTimeMs || 0 }} ms</div>
          </a-card>
        </a-col>
      </a-row>

      <a-card size="small" title="主机信息" class="block">
        <a-descriptions :column="3" size="small">
          <a-descriptions-item label="主机名">{{ host.hostname || '-' }}</a-descriptions-item>
          <a-descriptions-item label="系统">{{ host.osName }} {{ host.osVersion }}</a-descriptions-item>
          <a-descriptions-item label="架构">{{ host.arch }}</a-descriptions-item>
          <a-descriptions-item label="CPU">{{ host.cpuModel || '-' }}</a-descriptions-item>
          <a-descriptions-item label="负载">{{ (host.loadAverage || []).join(' / ') || '-' }}</a-descriptions-item>
          <a-descriptions-item label="进程数">{{ host.processCount || 0 }}</a-descriptions-item>
          <a-descriptions-item label="Swap">
            {{ formatBytes(host.swapUsed) }} / {{ formatBytes(host.swapTotal) }}
          </a-descriptions-item>
          <a-descriptions-item label="网络累计收发">
            收 {{ formatBytes(host.network?.rxBytes) }} / 发 {{ formatBytes(host.network?.txBytes) }}
          </a-descriptions-item>
          <a-descriptions-item label="JDK">{{ jvm.javaVersion || '-' }}</a-descriptions-item>
        </a-descriptions>
      </a-card>

      <a-card size="small" title="磁盘" class="block">
        <a-table :dataSource="host.disks || []" :columns="diskColumns" :pagination="false" row-key="mount" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'usage'">
              <a-progress :percent="clamp(record.usagePercent)" size="small" />
            </template>
          </template>
        </a-table>
      </a-card>

      <a-card size="small" title="容器状态" class="block">
        <template #extra>
          <a-tag v-if="!dockerAvailable" color="orange">当前环境未连接 Docker（本地开发可忽略）</a-tag>
        </template>
        <a-table :dataSource="containers" :columns="containerColumns" :pagination="false" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'state'">
              <a-tag :color="record.state === 'running' ? (record.health === 'unhealthy' ? 'red' : 'green') : 'default'">
                {{ record.state === 'running' ? (record.health === 'unhealthy' ? 'unhealthy' : '运行中') : '已停止' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'cpu'">
              {{ formatPercent(record.cpuPercent) }}
            </template>
            <template v-else-if="column.key === 'memory'">
              {{ formatBytes(record.memoryUsed) }} / {{ formatBytes(record.memoryLimit) }}
              ({{ formatPercent(record.memoryPercent) }})
            </template>
            <template v-else-if="column.key === 'network'">
              收 {{ formatBytes(record.networkRx) }} / 发 {{ formatBytes(record.networkTx) }}
            </template>
          </template>
        </a-table>
      </a-card>

      <a-card size="small" title="中间件" class="block">
        <a-space>
          <a-tag v-for="item in services" :key="item.name" :color="item.status === 'UP' ? 'green' : 'red'">
            {{ item.name }}: {{ item.status }}
          </a-tag>
        </a-space>
      </a-card>

      <a-card size="small" title="Java 服务指标（Actuator）" class="block">
        <template #extra>
          <span class="tip">30 秒缓存；接口耗时为服务启动以来的累计值</span>
        </template>
        <a-table :dataSource="serviceMetrics" :columns="serviceColumns" :pagination="false" row-key="name"
                 size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === 'UP' ? 'green' : 'red'">{{ record.status }}</a-tag>
            </template>
            <template v-else-if="column.key === 'heap'">
              {{ formatBytes(record.heapUsed) }} / {{ formatBytes(record.heapMax) }}
              ({{ formatPercent(record.heapUsagePercent) }})
            </template>
            <template v-else-if="column.key === 'cpu'">
              {{ formatPercent(record.cpuUsagePercent) }}
            </template>
            <template v-else-if="column.key === 'http'">
              {{ record.httpCount || 0 }} 次 · 平均 {{ record.httpAvgMs || 0 }} ms · 最大 {{ record.httpMaxMs || 0 }} ms
            </template>
            <template v-else-if="column.key === 'gc'">
              {{ record.gcCount || 0 }} 次 / {{ record.gcTimeMs || 0 }} ms
            </template>
          </template>
        </a-table>
      </a-card>
    </a-spin>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getServerOverview } from '@/api/monitor.js'

const loading = ref(false)
const host = ref({})
const jvm = ref({})
const containers = ref([])
const services = ref([])
const alerts = ref([])
const serviceMetrics = ref([])
const dockerAvailable = ref(false)
let timer = null

const diskColumns = [
  { title: '挂载点', dataIndex: 'mount', key: 'mount' },
  { title: '文件系统', dataIndex: 'name', key: 'name' },
  { title: '总量', dataIndex: 'total', key: 'total', customRender: ({ text }) => formatBytes(text) },
  { title: '可用', dataIndex: 'usable', key: 'usable', customRender: ({ text }) => formatBytes(text) },
  { title: '使用率', key: 'usage' },
]

const serviceColumns = [
  { title: '服务', dataIndex: 'name', key: 'name' },
  { title: '状态', key: 'status' },
  { title: '堆内存', key: 'heap' },
  { title: 'CPU', key: 'cpu' },
  { title: '线程', dataIndex: 'threadCount', key: 'threadCount' },
  { title: 'GC', key: 'gc' },
  { title: 'HTTP 接口', key: 'http' },
]

const containerColumns = [
  { title: '容器', dataIndex: 'name', key: 'name' },
  { title: '镜像', dataIndex: 'image', key: 'image' },
  { title: '状态', key: 'state' },
  { title: 'CPU', key: 'cpu' },
  { title: '内存', key: 'memory' },
  { title: '网络', key: 'network' },
]

function load() {
  loading.value = true
  getServerOverview()
    .then((res) => {
      const data = res.data.data || {}
      host.value = data.host || {}
      jvm.value = data.jvm || {}
      containers.value = data.containers || []
      services.value = data.services || []
      alerts.value = data.alerts || []
      serviceMetrics.value = data.serviceMetrics || []
      dockerAvailable.value = !!data.dockerAvailable
    })
    .catch(() => message.error('获取服务监控数据失败'))
    .finally(() => {
      loading.value = false
    })
}

function formatBytes(value) {
  const bytes = Number(value || 0)
  if (bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let index = 0
  let size = bytes
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index++
  }
  return `${size.toFixed(1)} ${units[index]}`
}

function formatPercent(value) {
  return `${Number(value || 0).toFixed(2)}%`
}

function formatUptime(seconds) {
  const total = Number(seconds || 0)
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  return days > 0 ? `${days} 天 ${hours} 小时` : `${hours} 小时`
}

function clamp(value) {
  return Math.min(100, Math.max(0, Number(value || 0)))
}

onMounted(() => {
  load()
  timer = setInterval(() => {
    if (!document.hidden) load()
  }, 15000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.tip {
  color: #999;
  font-size: 13px;
}
.metric {
  font-size: 26px;
  font-weight: 600;
  margin-bottom: 4px;
}
.sub {
  color: #888;
  font-size: 12px;
  margin-top: 6px;
  line-height: 20px;
}
.block {
  margin-top: 16px;
}
.alerts {
  margin-bottom: 16px;
}
.alerts :deep(.ant-alert) {
  margin-bottom: 8px;
}
</style>
