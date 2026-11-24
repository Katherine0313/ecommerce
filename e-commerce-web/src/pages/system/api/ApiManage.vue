<template>
  <div class="api-page">
    <!-- 工具栏：服务过滤、关键字搜索、同步 -->
    <div class="head">
      <a-space wrap>
        <a-select v-model:value="query.service" style="width: 200px" placeholder="全部服务" allow-clear
                  :options="serviceOptions" @change="loadTree" />
        <a-input v-model:value="query.keyword" style="width: 240px" placeholder="接口名 / 路径 / 说明"
                 allow-clear @pressEnter="loadTree" />
        <a-button type="primary" @click="loadTree">
          <SearchOutlined />
          搜索
        </a-button>
        <a-button v-permission="'system:api:sync'" type="primary" ghost :loading="syncing" @click="handleSync">
          <SyncOutlined />
          从 OpenAPI 同步
        </a-button>
      </a-space>
      <span class="hint">同步会覆盖方法/路径/参数等机器字段，接口名与返回示例等人工字段不会被覆盖</span>
    </div>

    <a-alert v-if="lastSync" class="sync-alert" type="info" show-icon closable @close="lastSync = null">
      <template #message>
        同步完成：新增 {{ lastSync.added }}，更新 {{ lastSync.updated }}，缺失 {{ lastSync.missing }}，
        共 {{ lastSync.total }} 个接口
        <span class="hint">（{{ lastSync.syncedAt }}）</span>
      </template>
      <template v-if="lastSync.errors && lastSync.errors.length" #description>
        <div v-for="(err, index) in lastSync.errors" :key="index" class="mono">{{ err }}</div>
      </template>
    </a-alert>

    <div class="body">
      <!-- 左侧：分组 -> 接口 -->
      <div class="tree-panel">
        <a-spin :spinning="treeLoading">
          <a-tree :tree-data="treeData" :expanded-keys="expandedKeys" :selected-keys="selectedKeys"
                  block-node @expand="handleExpand" @select="handleSelect">
            <template #title="{ title, dataRef }">
              <span class="node-title">
                <template v-if="dataRef.method">
                  <span class="method" :style="{ color: methodColor(dataRef.method), borderColor: methodColor(dataRef.method) }">
                    {{ dataRef.method }}
                  </span>
                  <span class="ellipsis" :title="dataRef.path">{{ title }}</span>
                  <a-tag v-if="dataRef.missing" color="red" class="missing-tag">已失效</a-tag>
                </template>
                <template v-else>
                  <span class="group-title">{{ title }}</span>
                  <span class="hint">（{{ dataRef.count }}）</span>
                </template>
              </span>
            </template>
          </a-tree>
        </a-spin>
        <a-empty v-if="!treeLoading && !treeData.length" description="暂无接口，请先执行同步" />
      </div>

      <!-- 右侧：详情 + 人工字段编辑 -->
      <div class="detail-panel">
        <a-empty v-if="!current" description="从左侧选择接口查看详情" />
        <template v-else>
          <div class="detail-head">
            <a-space wrap>
              <a-tag :color="methodColor(current.httpMethod)">{{ current.httpMethod }}</a-tag>
              <span class="mono path">{{ current.path }}</span>
              <a-tag v-if="current.missing" color="red">已失效</a-tag>
              <a-tag v-if="current.isPublic === 1" color="green">公共接口</a-tag>
            </a-space>
            <a-space>
              <a-button v-permission="'system:api:edit'" type="primary" :loading="saving" @click="handleSave">
                保存人工字段
              </a-button>
              <a-button @click="resetForm">重置</a-button>
            </a-space>
          </div>

          <a-tabs v-model:activeKey="detailTab">
            <a-tab-pane key="basic" tab="基本信息">
              <a-descriptions :column="2" bordered size="small">
                <a-descriptions-item label="接口名">
                  <a-input v-model:value="form.apiName" :maxlength="128" placeholder="留空则展示 OpenAPI summary"
                           style="width: 100%" />
                </a-descriptions-item>
                <a-descriptions-item label="是否公共">
                  <a-radio-group v-model:value="form.isPublic" button-style="solid" size="small">
                    <a-radio-button :value="0">否</a-radio-button>
                    <a-radio-button :value="1">是</a-radio-button>
                  </a-radio-group>
                </a-descriptions-item>
                <a-descriptions-item label="所属服务">{{ text(current.serviceName) }}</a-descriptions-item>
                <a-descriptions-item label="分组">{{ text(current.groupName) }}</a-descriptions-item>
                <a-descriptions-item label="方法">{{ text(current.httpMethod) }}</a-descriptions-item>
                <a-descriptions-item label="路径">{{ text(current.path) }}</a-descriptions-item>
                <a-descriptions-item label="所需权限">{{ text(current.requirePerm) }}</a-descriptions-item>
                <a-descriptions-item label="同步时间">{{ text(current.syncTime) }}</a-descriptions-item>
                <a-descriptions-item label="摘要" :span="2">{{ text(current.summary) }}</a-descriptions-item>
                <a-descriptions-item label="描述" :span="2">{{ text(current.description) }}</a-descriptions-item>
              </a-descriptions>
            </a-tab-pane>

            <a-tab-pane key="params" tab="参数">
              <div class="section-title">Query 参数</div>
              <pre class="code-block">{{ pretty(current.queryParams) }}</pre>
              <div class="section-title">Header 参数</div>
              <pre class="code-block">{{ pretty(current.headerParams) }}</pre>
              <div class="section-title">Body 参数</div>
              <pre class="code-block">{{ pretty(current.bodyParams) }}</pre>
              <div class="section-title">返回参数</div>
              <pre class="code-block">{{ pretty(current.responseParams) }}</pre>
            </a-tab-pane>

            <a-tab-pane key="example" tab="返回示例">
              <a-textarea v-model:value="form.responseExample" :rows="16" class="json-input"
                          placeholder='示例：{"code":0,"message":"success","data":{}}' />
              <div class="hint">必须是合法 JSON；保存时会校验，留空表示不配置</div>
            </a-tab-pane>

            <a-tab-pane key="error" tab="错误码">
              <a-textarea v-model:value="form.errorCodes" :rows="16" class="json-input"
                          placeholder='示例：[{"code":400,"message":"参数错误"}]' />
              <div class="hint">必须是合法 JSON；保存时会校验，留空表示不配置</div>
            </a-tab-pane>
          </a-tabs>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { SearchOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getApiDoc,
  getApiServices,
  getApiTree,
  syncApiDocs,
  updateApiDoc,
} from '@/api/systemApi.js'

const query = ref({ service: undefined, keyword: '' })
const services = ref([])
const treeData = ref([])
const treeLoading = ref(false)
const expandedKeys = ref([])
const selectedKeys = ref([])
const syncing = ref(false)
const lastSync = ref(null)

const current = ref(null)
const detailTab = ref('basic')
const saving = ref(false)
const form = ref({ apiName: '', isPublic: 0, responseExample: '', errorCodes: '' })

const serviceOptions = computed(() => services.value.map((item) => ({ value: item, label: item })))

// 方法色块：沿用 Swagger 配色，便于快速区分读写操作
const METHOD_COLORS = {
  GET: '#61affe',
  POST: '#49cc90',
  PUT: '#fca130',
  DELETE: '#f93e3e',
  PATCH: '#50e3c2',
}

function methodColor(method) {
  return METHOD_COLORS[String(method || '').toUpperCase()] || '#999'
}

function text(value) {
  return value === null || value === undefined || value === '' ? '—' : value
}

// JSON 字段格式化展示；非 JSON 原样返回
function pretty(value) {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (e) {
    return value
  }
}

function loadServices() {
  getApiServices()
    .then((res) => {
      services.value = res.data.data || []
    })
    .catch(() => {})
}

function loadTree() {
  treeLoading.value = true
  getApiTree({
    service: query.value.service || undefined,
    keyword: query.value.keyword || undefined,
  })
    .then((res) => {
      treeData.value = res.data.data || []
      // 默认展开全部分组，接口数量不多时体验更好
      expandedKeys.value = treeData.value.map((item) => item.key)
    })
    .catch(() => {})
    .finally(() => {
      treeLoading.value = false
    })
}

function handleExpand(keys) {
  expandedKeys.value = keys
}

function handleSelect(keys) {
  const key = keys[0]
  if (!key || !key.startsWith('api:')) return
  selectedKeys.value = [key]
  loadDetail(key.substring(4))
}

function loadDetail(id) {
  getApiDoc(id)
    .then((res) => {
      current.value = res.data.data || null
      resetForm()
    })
    .catch(() => {
      current.value = null
    })
}

function resetForm() {
  const doc = current.value || {}
  form.value = {
    apiName: doc.apiName || '',
    isPublic: doc.isPublic === 1 ? 1 : 0,
    responseExample: doc.responseExample || '',
    errorCodes: doc.errorCodes || '',
  }
}

async function handleSync() {
  if (syncing.value) return
  syncing.value = true
  try {
    const res = await syncApiDocs()
    lastSync.value = res.data.data
    message.success(`同步完成，共 ${lastSync.value.total} 个接口`)
    loadServices()
    loadTree()
  } finally {
    syncing.value = false
  }
}

async function handleSave() {
  if (!current.value || saving.value) return
  saving.value = true
  try {
    const res = await updateApiDoc(current.value.id, {
      apiName: form.value.apiName || null,
      isPublic: form.value.isPublic,
      responseExample: form.value.responseExample || null,
      errorCodes: form.value.errorCodes || null,
    })
    current.value = res.data.data || current.value
    resetForm()
    message.success('已保存')
    loadTree()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadServices()
  loadTree()
})
// 页面缓存返回时刷新服务与树，保证同步结果可见
onActivated(() => {
  loadServices()
  loadTree()
})
</script>

<style scoped>
.api-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 16px 0;
}

.hint {
  color: #999;
  font-size: 12px;
}

.sync-alert {
  margin-bottom: 16px;
}

.body {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.tree-panel {
  width: 340px;
  flex: 0 0 340px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 12px 8px;
  overflow: auto;
  max-height: calc(100vh - 260px);
}

.detail-panel {
  flex: 1;
  min-width: 0;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 16px;
  overflow: auto;
  max-height: calc(100vh - 260px);
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.path {
  font-size: 13px;
  word-break: break-all;
}

.node-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
}

.group-title {
  font-weight: 600;
}

.method {
  display: inline-block;
  min-width: 52px;
  padding: 0 6px;
  border: 1px solid;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  line-height: 18px;
}

.missing-tag {
  margin-left: 4px;
  transform: scale(0.85);
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}

.section-title {
  margin: 12px 0 6px;
  font-weight: 600;
}

.code-block {
  margin: 0;
  padding: 10px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}

.json-input {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.mono {
  font-family: Consolas, Monaco, monospace;
}
</style>
