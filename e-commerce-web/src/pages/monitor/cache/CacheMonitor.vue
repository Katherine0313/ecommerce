<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="4">
        <a-card size="small" title="Redis 版本">
          <div class="metric">{{ overview.version || '-' }}</div>
          <div class="sub">{{ overview.mode || '-' }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="连接数">
          <div class="metric">{{ overview.connectedClients || 0 }}</div>
          <div class="sub">阻塞 {{ overview.blockedClients || 0 }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="内存占用">
          <div class="metric">{{ overview.usedMemoryHuman || '-' }}</div>
          <div class="sub">峰值 {{ overview.usedMemoryPeakHuman || '-' }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="命中率">
          <div class="metric">{{ formatPercent(overview.hitRate) }}</div>
          <div class="sub">命中 {{ overview.keyspaceHits || 0 }} / 未命中 {{ overview.keyspaceMisses || 0 }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="QPS">
          <div class="metric">{{ overview.opsPerSec || 0 }}</div>
          <div class="sub">运行 {{ formatUptime(overview.uptimeSeconds) }}</div>
        </a-card>
      </a-col>
      <a-col :span="4">
        <a-card size="small" title="过期 / 淘汰">
          <div class="metric">{{ overview.expiredKeys || 0 }} / {{ overview.evictedKeys || 0 }}</div>
          <div class="sub">
            <a-tag v-for="db in overview.dbs || []" :key="db.name" color="blue">
              {{ db.name }}: {{ db.keys }}
            </a-tag>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card size="small" title="Key 浏览（SCAN 游标分页，不阻塞 Redis）" class="block">
      <div class="filter">
        <a-auto-complete
            v-model:value="query.prefix"
            :options="prefixOptions"
            placeholder="前缀，如 auth: / monitor:task:last:"
            allow-clear
            style="width: 260px"
            @pressEnter="scan(true)"
            @select="scan(true)"
        />
        <a-input v-model:value="query.keyword" placeholder="包含关键字（可选）" allow-clear
                 style="width: 220px" @pressEnter="scan(true)" />
        <a-button type="primary" :loading="loading" @click="scan(true)">扫描</a-button>
        <a-space class="quick">
          <a-tag v-for="item in quickPrefixes" :key="item" color="blue" style="cursor: pointer"
                 @click="applyPrefix(item)">
            {{ item }}
          </a-tag>
        </a-space>
      </div>

      <a-table :dataSource="list" :columns="columns" :pagination="false" row-key="token" size="small"
               :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'key'">
            <span :class="{ masked: !record.fullKey }">{{ record.fullKey || record.key }}</span>
          </template>
          <template v-else-if="column.key === 'ttl'">
            <span v-if="record.ttl === -1">永久</span>
            <span v-else-if="record.ttl === -2" class="sub">已不存在</span>
            <span v-else>{{ record.ttl }} 秒</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button v-if="!record.fullKey" v-permission="'monitor:cache:key:view'" type="link" size="small"
                      @click="handleReveal(record)">查看</a-button>
            <a-popconfirm
                title="删除后不可恢复；删除 auth:online:* 等同于踢人，确定继续？"
                ok-text="确定删除"
                ok-type="danger"
                @confirm="handleDelete(record)"
            >
              <a-button v-permission="'monitor:cache:delete'" type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>

      <div class="foot">
        <a-button v-if="!finished" :loading="loadingMore" @click="scan(false)">加载更多</a-button>
        <span class="sub">当前展示 {{ list.length }} 个 key{{ finished ? '（已扫描完）' : '' }}</span>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { deleteCacheKey, getCacheKeys, getCacheOverview, revealCacheKey } from '@/api/monitor.js'

const overview = ref({})
const list = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const cursor = ref(0)
const finished = ref(false)
const query = ref({ prefix: '', keyword: '' })

const quickPrefixes = ['auth:captcha:', 'auth:login:fail:', 'auth:online:', 'monitor:task:last:']

// 下拉建议：内置常用前缀 + 从已扫描到的 key 中自动提取的命名空间前缀（首段冒号）。
// 这样用户不用记前缀，点一下就能看到目前库里都有哪些缓存命名空间。
const prefixOptions = computed(() => {
  const set = new Set(quickPrefixes)
  list.value.forEach((item) => {
    const key = item.fullKey || item.key
    if (!key) return
    const colon = key.indexOf(':')
    if (colon > 0) set.add(key.slice(0, colon + 1))
  })
  return Array.from(set).map((v) => ({ value: v }))
})

const columns = [
  { title: 'Key', dataIndex: 'key', key: 'key' },
  { title: '类型', dataIndex: 'type', key: 'type' },
  { title: 'TTL', key: 'ttl' },
  { title: '操作', key: 'action' },
]

function loadOverview() {
  getCacheOverview()
    .then((res) => {
      overview.value = res.data.data || {}
    })
    .catch(() => message.error('获取 Redis 概览失败'))
}

function scan(reset) {
  if (reset) {
    cursor.value = 0
    list.value = []
    finished.value = false
    loading.value = true
  } else {
    loadingMore.value = true
  }
  getCacheKeys({
    prefix: query.value.prefix || undefined,
    keyword: query.value.keyword || undefined,
    cursor: reset ? 0 : cursor.value,
    pageSize: 100,
  })
    .then((res) => {
      const data = res.data.data || {}
      const incoming = data.list || []
      list.value = reset ? incoming : list.value.concat(incoming)
      cursor.value = data.cursor || 0
      finished.value = !!data.finished
    })
    .catch(() => message.error('扫描缓存失败'))
    .finally(() => {
      loading.value = false
      loadingMore.value = false
    })
}

function applyPrefix(prefix) {
  query.value.prefix = prefix
  scan(true)
}

function handleReveal(record) {
  revealCacheKey(record.token)
    .then((res) => {
      record.fullKey = res.data.data || ''
    })
    .catch((error) => {
      message.error(error.response?.data?.message || '查看完整 key 失败')
    })
}

function handleDelete(record) {
  deleteCacheKey(record.token)
    .then(() => {
      message.success('删除成功')
      list.value = list.value.filter((item) => item.token !== record.token)
    })
    .catch((error) => {
      message.error(error.response?.data?.message || '删除失败')
    })
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

onMounted(() => {
  loadOverview()
  scan(true)
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
.masked {
  color: #888;
}
.block {
  margin-top: 16px;
}
.filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.quick {
  margin-left: 8px;
}
.foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
</style>
