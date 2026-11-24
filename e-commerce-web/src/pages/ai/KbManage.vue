<template>
  <div>
    <!-- 工具栏：scope 切换 + 分类 / 状态筛选 + 关键词 + 新建 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-radio-group v-model:value="query.scope" button-style="solid" @change="handleScopeChange">
            <a-radio-button value="">全部</a-radio-button>
            <a-radio-button value="客服">客服</a-radio-button>
            <a-radio-button value="运营">运营</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.category" style="width: 160px" placeholder="分类" allow-clear
                    :options="categoryOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.status" style="width: 120px" placeholder="状态" allow-clear
                    :options="statusOptions" />
        </a-form-item>
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="问题 / 关键词" allow-clear
                   style="width: 200px" @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
        </a-form-item>
      </a-form>
      <div>
        <a-button v-permission="'ai:chat:use'" style="margin-right: 8px" @click="downloadTemplate">
          下载模板
        </a-button>
        <a-button v-permission="'ai:chat:use'" style="margin-right: 8px" @click="importVisible = true">
          <UploadOutlined />
          批量导入
        </a-button>
        <a-button v-permission="'ai:chat:use'" type="primary" @click="openCreate">
          <PlusOutlined />
          新建条目
        </a-button>
      </div>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
             :loading="loading" @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'question'">
          <a-tooltip :title="record.question">
            <span class="cell-question">{{ record.question }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'answer'">
          <a-tooltip :title="record.answer">
            <span class="cell-answer">{{ record.answer }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'scope'">
          <a-tag :color="record.scope === '运营' ? 'blue' : 'green'">{{ record.scope }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">
            {{ record.status === 1 ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'hitCount'">
          {{ record.hitCount || 0 }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button size="small" style="margin-right: 6px" @click="openEdit(record)">编辑</a-button>
          <a-popconfirm title="确定删除该条目？" @confirm="handleDelete(record)">
            <a-button size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 新建 / 编辑弹窗 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑知识条目' : '新建知识条目'"
             :width="720" :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 4 }">
        <a-form-item label="适用范围" required>
          <a-radio-group v-model:value="form.scope">
            <a-radio value="客服">客服</a-radio>
            <a-radio value="运营">运营</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="分类">
          <a-select v-model:value="form.category" style="width: 100%" placeholder="选择或输入分类"
                    allow-clear :options="categoryOptions" />
        </a-form-item>
        <a-form-item label="问题" required>
          <a-input v-model:value="form.question" :maxlength="255" placeholder="标准问法，如「多久能收到货？」" />
        </a-form-item>
        <a-form-item label="答案" required>
          <a-textarea v-model:value="form.answer" :rows="4" :maxlength="1000"
                      placeholder="答案原文，直接返回给用户" />
        </a-form-item>
        <a-form-item label="关键词">
          <a-input v-model:value="form.keywords" :maxlength="255"
                   placeholder="逗号分隔，如「发货,物流,多久」" />
        </a-form-item>
        <a-form-item label="优先级">
          <a-input-number v-model:value="form.priority" :min="0" :max="999" style="width: 120px" />
          <span class="unit">（同分时越大越优先）</span>
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="form.status" :checked-value="1" :un-checked-value="0"
                    checked-children="启用" un-checked-children="停用" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Excel 批量导入弹窗：默认 scope → 上传预检（不落库）→ 确认导入 -->
    <a-modal v-model:visible="importVisible" title="批量导入知识库" :width="900" :footer="null">
      <a-form layout="inline" style="margin-bottom: 12px">
        <a-form-item label="默认 scope">
          <a-radio-group v-model:value="importScope">
            <a-radio-button value="客服">客服</a-radio-button>
            <a-radio-button value="运营">运营</a-radio-button>
          </a-radio-group>
          <span class="unit">（Excel 里 scope 列留空的行归入此库；填了以单元格为准）</span>
        </a-form-item>
        <a-form-item>
          <a-upload :show-upload-list="false" accept=".xlsx" :before-upload="pickImportFile">
            <a-button type="primary" :loading="previewing">选择文件并预检</a-button>
          </a-upload>
        </a-form-item>
      </a-form>

      <template v-if="importPreview">
        <a-alert style="margin-bottom: 12px" type="info" show-icon
                 :message="`共 ${importPreview.summary.total} 行：新增 ${importPreview.summary.ok}，将更新 ${importPreview.summary.updated}，失败 ${importPreview.summary.failed}，重复跳过 ${importPreview.summary.duplicate}`" />
        <a-table :dataSource="importPreview.rows" :columns="importColumns" row-key="row"
                 size="small" :pagination="{ pageSize: 8, showTotal: (t) => `共 ${t} 行` }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'result'">
              <a-tag :color="{ ok: 'green', updated: 'blue', failed: 'red', duplicate: 'default' }[record.result]">
                {{ { ok: '新增', updated: '将更新', failed: '失败', duplicate: '重复' }[record.result] }}
              </a-tag>
            </template>
          </template>
        </a-table>
        <div style="text-align: right; margin-top: 12px">
          <a-button style="margin-right: 8px" @click="importPreview = null">重新选择</a-button>
          <a-button type="primary" :loading="importing"
                    :disabled="!importPreview.summary.ok && !importPreview.summary.updated"
                    @click="confirmImport">
            确认导入（新增 {{ importPreview.summary.ok }} / 更新 {{ importPreview.summary.updated }}）
          </a-button>
        </div>
      </template>
      <a-empty v-else description="请先下载模板按格式填写，再选择文件预检" />
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { listKb, listKbCategories, createKb, updateKb, deleteKb,
         downloadKbTemplate, previewKbImport, confirmKbImport } from '@/api/ai'

const loading = ref(false)
const list = ref([])
const categories = ref([])

const query = reactive({
  scope: '',
  category: undefined,
  status: undefined,
  keyword: '',
  page: 1,
  pageSize: 20,
})

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
})

const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
]

const categoryOptions = ref([])

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '问题', dataIndex: 'question', key: 'question' },
  { title: '答案', dataIndex: 'answer', key: 'answer' },
  { title: '分类', dataIndex: 'category', key: 'category' },
  { title: '范围', dataIndex: 'scope', key: 'scope' },
  { title: '优先级', dataIndex: 'priority', key: 'priority'},
  { title: '命中', dataIndex: 'hitCount', key: 'hitCount' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt' },
  { title: '操作', key: 'action', width: 140},
]

async function loadCategories() {
  const res = await listKbCategories(query.scope || undefined)
  categoryOptions.value = (res.data.data || []).map((c) => ({ value: c, label: c }))
}

async function loadList() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.scope) delete params.scope
    if (!params.category) delete params.category
    if (params.status === undefined || params.status === null) delete params.status
    if (!params.keyword) delete params.keyword
    const res = await listKb(params)
    const data = res.data.data
    list.value = data.list || []
    pagination.total = data.total || 0
    pagination.current = data.page || 1
    pagination.pageSize = data.pageSize || 20
  } finally {
    loading.value = false
  }
}

function handleScopeChange() {
  query.category = undefined
  query.page = 1
  loadCategories()
  loadList()
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleTableChange(pag) {
  query.page = pag.current
  query.pageSize = pag.pageSize
  loadList()
}

// ---- 新建 / 编辑 ----
const formVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({
  scope: '客服',
  category: '',
  question: '',
  answer: '',
  keywords: '',
  priority: 0,
  status: 1,
})

function resetForm() {
  editingId.value = null
  form.scope = '客服'
  form.category = ''
  form.question = ''
  form.answer = ''
  form.keywords = ''
  form.priority = 0
  form.status = 1
}

function openCreate() {
  resetForm()
  formVisible.value = true
}

function openEdit(record) {
  editingId.value = record.id
  form.scope = record.scope
  form.category = record.category || ''
  form.question = record.question
  form.answer = record.answer
  form.keywords = record.keywords || ''
  form.priority = record.priority || 0
  form.status = record.status
  formVisible.value = true
}

async function handleSubmit() {
  if (!form.question.trim() || !form.answer.trim()) {
    message.warning('问题和答案不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      scope: form.scope,
      category: form.category || '',
      question: form.question.trim(),
      answer: form.answer.trim(),
      keywords: form.keywords || '',
      priority: form.priority || 0,
      status: form.status,
    }
    if (editingId.value) {
      await updateKb(editingId.value, payload)
      message.success('已更新')
    } else {
      await createKb(payload)
      message.success('已创建')
    }
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(record) {
  await deleteKb(record.id)
  message.success('已删除')
  loadList()
}

// ---- Excel 批量导入 ----
const importVisible = ref(false)
const importScope = ref('客服')
const importPreview = ref(null)
const previewing = ref(false)
const importing = ref(false)

const importColumns = [
  { title: '行号', dataIndex: 'row', key: 'row', width: 70 },
  { title: 'scope', dataIndex: 'scope', key: 'scope', width: 80 },
  { title: '分类', dataIndex: 'category', key: 'category', width: 120 },
  { title: '问题', dataIndex: 'question', key: 'question', ellipsis: true },
  { title: '结果', key: 'result', width: 90 },
  { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
]

function downloadTemplate() {
  downloadKbTemplate()
      .then(async (res) => {
        // 后端校验失败时返回的是 JSON 错误体（blob 包装），按类型分流
        if (res.data.type && res.data.type.includes('application/json')) {
          const err = JSON.parse(await res.data.text())
          message.error(err.message || '模板下载失败')
          return
        }
        const link = document.createElement('a')
        link.href = URL.createObjectURL(res.data)
        link.download = 'kb_import_template.xlsx'
        link.click()
        URL.revokeObjectURL(link.href)
      })
      .catch(() => {})
}

function pickImportFile(file) {
  const name = (file.name || '').toLowerCase()
  // 强校验：只放行 .xlsx（后端用 openpyxl 解析，不支持 .xls 旧格式）
  if (!name.endsWith('.xlsx')) {
    message.error('仅支持 .xlsx 格式的 Excel 文件，不能上传 PHP、JSP 或其他格式')
    return false
  }
  previewing.value = true
  previewKbImport(file, importScope.value)
      .then((res) => {
        importPreview.value = res.data?.data || null
      })
      .catch(() => {})
      .finally(() => { previewing.value = false })
  return false // 阻止 antd 自动上传，文件已手动发给 preview 接口
}

function confirmImport() {
  if (!importPreview.value?.importFileId) return
  importing.value = true
  confirmKbImport(importPreview.value.importFileId)
      .then((res) => {
        const d = res.data?.data || {}
        message.success(`导入完成：新增 ${d.inserted ?? 0}，更新 ${d.updated ?? 0}，失败 ${d.failed ?? 0}，重复跳过 ${d.duplicate ?? 0}`)
        importVisible.value = false
        importPreview.value = null
        loadCategories()
        loadList()
      })
      .catch(() => {})
      .finally(() => { importing.value = false })
}

onMounted(() => {
  loadCategories()
  loadList()
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}
.cell-question {
  display: inline-block;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.cell-answer {
  display: inline-block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.unit {
  margin-left: 8px;
  color: #999;
  font-size: 12px;
}
</style>
