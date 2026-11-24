<template>
  <div>
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane key="tables" tab="数据库表">
        <div class="head">
          <a-space wrap>
            <a-input v-model:value="keyword" placeholder="表名" allow-clear style="width: 220px"
                     @pressEnter="handleSearch" />
            <a-button type="primary" @click="handleSearch">
              <SearchOutlined />
              搜索
            </a-button>
            <a-button v-permission="'system:db:backup'" type="primary" ghost @click="openBackup">
              <CloudDownloadOutlined />
              发起备份
            </a-button>
            <a-button v-permission="'system:db:optimize'" :loading="optimizing" @click="handleOptimize">
              <ThunderboltOutlined />
              优化选中表
            </a-button>
            <a-button @click="loadTables">
              <ReloadOutlined />
              刷新
            </a-button>
          </a-space>
          <span class="hint">日志表默认不展示、不参与备份；行数为 InnoDB 估算值</span>
        </div>

        <!-- 备份任务进度：轮询展示，失败给出原因 -->
        <a-alert v-if="backupTask" class="task-alert"
                 :type="backupTask.status === 'FAILED' ? 'error' : 'info'"
                 show-icon closable @close="backupTask = null">
          <template #message>
            <template v-if="backupTask.status === 'RUNNING'">
              备份进行中：{{ backupTask.doneTables }} / {{ backupTask.totalTables }} 张表
              <span v-if="backupTask.currentTable" class="mono">（当前：{{ backupTask.currentTable }}）</span>
            </template>
            <template v-else-if="backupTask.status === 'SUCCESS'">
              备份完成，共 {{ backupTask.totalTables }} 张表，文件：<span class="mono">{{ backupTask.fileNameBase }}</span>
            </template>
            <template v-else>
              备份失败：{{ backupTask.error || '未知错误' }}
            </template>
          </template>
          <a-progress :percent="backupTask.percent || 0" size="small"
                      :status="backupTask.status === 'FAILED' ? 'exception' : 'active'" />
        </a-alert>

        <a-table :data-source="tables" :columns="tableColumns" :loading="tableLoading"
                 :pagination="pagination" row-key="name"
                 :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
                  @change="handleTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <span class="mono">{{ record.name }}</span>
            </template>
            <template v-else-if="column.key === 'comment'">
              {{ record.comment || '—' }}
            </template>
            <template v-else-if="column.key === 'dataLengthHuman'">
              {{ record.dataLengthHuman || '—' }}
            </template>
            <template v-else-if="column.key === 'rows'">
              约 {{ record.rows ?? 0 }}
            </template>
            <template v-else-if="column.key === 'updateTime'">
              {{ record.updateTime || '—' }}
            </template>
            <template v-else-if="column.key === 'operation'">
              <a-button type="link" size="small" @click="openColumns(record)">查看字段</a-button>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="backups" tab="备份记录">
        <div class="head">
          <span class="hint">每个分卷单独一条记录；恢复功能暂未开放，下载后可在维护窗口手工导入</span>
          <a-button @click="loadBackups">
            <ReloadOutlined />
            刷新
          </a-button>
        </div>

        <a-table :data-source="backups" :columns="backupColumns" :loading="backupLoading"
                 :pagination="false" row-key="fileName">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'fileName'">
              <span class="mono">{{ record.fileName }}</span>
            </template>
            <template v-else-if="column.key === 'sizeHuman'">
              {{ record.sizeHuman || '—' }}
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="record.status === 'SUCCESS' ? 'green' : 'red'">{{ record.status }}</a-tag>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ record.createdAt || '—' }}
            </template>
            <template v-else-if="column.key === 'operation'">
              <a-space>
                <a-button v-permission="'system:db:download'" type="link" size="small"
                          @click="handleDownload(record)">下载</a-button>
                <a-popconfirm title="删除该备份分卷？删除后不可恢复" ok-text="删除" cancel-text="取消"
                              @confirm="handleDelete(record)">
                  <a-button v-permission="'system:db:delete'" type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- 发起备份：不选表表示全部业务表 -->
    <a-modal v-model:visible="backupVisible" title="发起数据库备份" :confirm-loading="backupStarting"
             ok-text="开始备份" @ok="startBackup">
      <a-alert type="info" show-icon message="未选择表时默认备份全部业务表；日志表已自动排除。" class="modal-alert" />
      <a-select v-model:value="backupTables" mode="multiple" allow-clear style="width: 100%"
                placeholder="默认全部业务表（可多选指定表）" :options="tableOptions"
                :max-tag-count="3" show-search option-filter-prop="label" />
    </a-modal>

    <!-- 表结构抽屉 -->
    <a-drawer v-model:visible="columnsVisible" :title="`表结构 - ${currentTable?.name || ''}`" :width="760">
      <a-descriptions :column="2" bordered size="small" class="table-desc">
        <a-descriptions-item label="表名">{{ currentTable?.name || '—' }}</a-descriptions-item>
        <a-descriptions-item label="备注">{{ currentTable?.comment || '—' }}</a-descriptions-item>
        <a-descriptions-item label="引擎">{{ currentTable?.engine || '—' }}</a-descriptions-item>
        <a-descriptions-item label="大小">{{ currentTable?.dataLengthHuman || '—' }}</a-descriptions-item>
        <a-descriptions-item label="行数">约 {{ currentTable?.rows ?? 0 }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ currentTable?.updateTime || '—' }}</a-descriptions-item>
      </a-descriptions>

      <a-table :data-source="columns" :columns="columnColumns" :loading="columnsLoading"
               :pagination="false" row-key="name" size="small" >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <span class="mono">{{ record.name }}</span>
          </template>
          <template v-else-if="column.key === 'nullable'">
            <a-tag :color="record.nullable ? 'default' : 'blue'">{{ record.nullable ? 'YES' : 'NO' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'defaultValue'">
            {{ record.defaultValue ?? '—' }}
          </template>
          <template v-else-if="column.key === 'comment'">
            {{ record.comment || '—' }}
          </template>
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  CloudDownloadOutlined,
  ReloadOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import {
  createDbBackup,
  deleteDbBackup,
  downloadDbBackup,
  getDbBackupTask,
  getDbBackups,
  getDbTables,
  getTableColumns,
  optimizeTables,
} from '@/api/systemDatabase.js'
import { downloadBlob, readBlobErrorMessage } from '@/utils/download.js'

const activeTab = ref('tables')
const keyword = ref('')
const tables = ref([])
const allTables = ref([])
const tableLoading = ref(false)
const selectedRowKeys = ref([])
const selectedRows = ref([])
const optimizing = ref(false)

const backups = ref([])
const backupLoading = ref(false)

const backupVisible = ref(false)
const backupStarting = ref(false)
const backupTables = ref([])
const backupTask = ref(null)
let pollTimer = null

const columnsVisible = ref(false)
const columnsLoading = ref(false)
const columns = ref([])
const currentTable = ref(null)

const pagination = ref({
  current: 1,
  pageSize: 50,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 张表`,
})

const tableColumns = [
  { title: '表名', dataIndex: 'name', key: 'name', align: 'left',width: 180 },
  { title: '备注', dataIndex: 'comment', key: 'comment' },
  { title: '引擎', dataIndex: 'engine', key: 'engine' },
  { title: '大小', dataIndex: 'dataLengthHuman', key: 'dataLengthHuman' },
  { title: '行数', dataIndex: 'rows', key: 'rows' },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
  { title: '操作', key: 'operation',  },
]

const backupColumns = [
  { title: '备份文件', dataIndex: 'fileName', key: 'fileName' },
  { title: '分卷', dataIndex: 'part', key: 'part' },
  { title: '大小', dataIndex: 'sizeHuman', key: 'sizeHuman'},
  { title: '表数量', dataIndex: 'tableCount', key: 'tableCount' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '操作', key: 'operation' },
]

const columnColumns = [
  { title: '字段名', dataIndex: 'name', key: 'name' },
  { title: '类型', dataIndex: 'type', key: 'type'},
  { title: '可为空', dataIndex: 'nullable', key: 'nullable' },
  { title: '默认值', dataIndex: 'defaultValue', key: 'defaultValue', width: 120 },
  { title: '键', dataIndex: 'columnKey', key: 'columnKey', width: 70 },
  { title: '额外', dataIndex: 'extra', key: 'extra', width: 110 },
  { title: '备注', dataIndex: 'comment', key: 'comment' },
]

// 备份选择器：用全量列表，避免被当前搜索条件过滤
const tableOptions = computed(() =>
  allTables.value.map((item) => ({
    value: item.name,
    label: item.comment ? `${item.name}（${item.comment}）` : item.name,
  }))
)

function loadTables() {
  tableLoading.value = true
  getDbTables({
    keyword: keyword.value || undefined,
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
  })
    .then((res) => {
      const data = res.data.data || {}
      tables.value = data.list || []
      pagination.value.total = data.total || 0
    })
    .catch(() => {})
    .finally(() => {
      tableLoading.value = false
    })
}

function loadAllTables() {
  getDbTables({ page: 1, pageSize: 200 })
    .then((res) => {
      allTables.value = (res.data.data && res.data.data.list) || []
    })
    .catch(() => {})
}

function loadBackups() {
  backupLoading.value = true
  getDbBackups()
    .then((res) => {
      backups.value = res.data.data || []
    })
    .catch(() => {})
    .finally(() => {
      backupLoading.value = false
    })
}

function handleSearch() {
  pagination.value.current = 1
  loadTables()
}

function handleTableChange(pager) {
  pagination.value.current = pager.current
  pagination.value.pageSize = pager.pageSize
  loadTables()
}

function onSelectChange(keys, rows) {
  selectedRowKeys.value = keys
  selectedRows.value = rows
}

function handleTabChange(key) {
  if (key === 'backups') {
    loadBackups()
  }
}

function openColumns(record) {
  currentTable.value = record
  columns.value = []
  columnsVisible.value = true
  columnsLoading.value = true
  getTableColumns(record.name)
    .then((res) => {
      columns.value = res.data.data || []
    })
    .catch(() => {})
    .finally(() => {
      columnsLoading.value = false
    })
}

function handleOptimize() {
  const names = selectedRows.value.map((item) => item.name)
  if (!names.length) {
    message.warning('请先选择要优化的表')
    return
  }
  Modal.confirm({
    title: `确认优化选中的 ${names.length} 张表？`,
    content: 'OPTIMIZE TABLE 会重建表并锁表，请尽量在业务低峰执行；大表可能耗时较长。',
    okText: '开始优化',
    okType: 'danger',
    onOk: async () => {
      optimizing.value = true
      try {
        const res = await optimizeTables(names)
        const messages = res.data.data || []
        const failed = messages.filter((item) => item.includes('失败'))
        if (failed.length) {
          message.warning(`优化完成，${failed.length} 张表失败：${failed.slice(0, 3).join('；')}`)
        } else {
          message.success(`已完成 ${messages.length} 张表的优化`)
        }
      } finally {
        optimizing.value = false
      }
    },
  })
}

function openBackup() {
  backupTables.value = []
  backupVisible.value = true
}

async function startBackup() {
  if (backupStarting.value) return
  backupStarting.value = true
  try {
    const res = await createDbBackup(backupTables.value)
    backupVisible.value = false
    backupTask.value = res.data.data
    message.success('备份任务已启动')
    pollTask(backupTask.value.taskId)
  } catch (err) {
    // 错误提示已由 axios 拦截器统一处理；阻止弹窗自动关闭
    throw err
  } finally {
    backupStarting.value = false
  }
}

function pollTask(taskId) {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await getDbBackupTask(taskId)
      backupTask.value = res.data.data
      if (backupTask.value.status !== 'RUNNING') {
        stopPolling()
        if (backupTask.value.status === 'SUCCESS') {
          message.success('备份完成')
          loadBackups()
        } else {
          message.error(backupTask.value.error || '备份失败')
        }
      }
    } catch (e) {
      stopPolling()
    }
  }, 1500)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function handleDownload(record) {
  try {
    const res = await downloadDbBackup(record.fileName)
    const errMsg = await readBlobErrorMessage(res.data)
    if (errMsg) {
      message.error(errMsg)
      return
    }
    downloadBlob(res.data, record.fileName)
  } catch (err) {
    const errMsg = await readBlobErrorMessage(err.response?.data)
    message.error(errMsg || '下载失败，请稍后重试')
  }
}

async function handleDelete(record) {
  await deleteDbBackup(record.fileName)
  message.success('已删除')
  loadBackups()
}

onMounted(() => {
  loadTables()
  loadAllTables()
})
// 页面被缓存时回到本页刷新列表，避免看到旧数据
onActivated(() => {
  loadTables()
})
onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
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

.task-alert {
  margin-bottom: 16px;
}

.modal-alert {
  margin-bottom: 12px;
}

.mono {
  font-family: Consolas, Monaco, monospace;
}

.table-desc {
  margin-bottom: 16px;
}
</style>
