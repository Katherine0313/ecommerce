<template>
  <div>
    <div class="head">
      <a-space>
        <a-input v-model:value="query.keyword" placeholder="任务名 / 服务 / 备注" allow-clear style="width: 240px"
                 @press-enter="load" />
        <a-select v-model:value="query.status" style="width: 130px" allow-clear placeholder="状态" @change="load">
          <a-select-option :value="1">运行中</a-select-option>
          <a-select-option :value="0">已暂停</a-select-option>
        </a-select>
        <a-button type="primary" :loading="loading" @click="load">查询</a-button>
      </a-space>
      <a-space>
        <span class="tip">配置保存后各服务 30 秒内生效；只有归属服务存在同名 Runner 的任务才会被调度</span>
        <a-button v-permission="'monitor:task:add'" type="primary" @click="openCreate">新增任务</a-button>
      </a-space>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="false" :row-key="'id'" size="middle"
             :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'taskType'">
          <a-tag :color="record.taskType === 1 ? 'blue' : 'cyan'">{{ taskTypeLabel(record.taskType) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-switch v-permission="'monitor:task:edit'" :checked="record.status === 1"
                    :loading="statusLoading === record.id" @change="(checked) => handleStatus(record, checked)" />
        </template>
        <template v-else-if="column.key === 'lastRun'">
          <template v-if="record.lastRun">
            <a-tag :color="record.lastRun.success ? 'green' : 'red'">
              {{ record.lastRun.success ? '成功' : '失败' }}
            </a-tag>
            <div class="sub">
              {{ record.lastRun.endTime || '-' }} · 耗时 {{ record.lastRun.costMs }} ms
            </div>
            <div v-if="record.lastRun.message" class="error">{{ record.lastRun.message }}</div>
          </template>
          <span v-else class="sub">暂无执行记录</span>
        </template>
        <template v-else-if="column.key === 'action'" >
          <div class="action">
            <div class="grid-cell">
              <a-button type="link" size="small" @click="openLogs(record)">日志</a-button>
            </div>
            <div class="grid-cell">
              <a-button v-permission="'monitor:task:edit'" type="link" size="small" @click="openEdit(record)">编辑</a-button>
            </div>
            <div class="grid-cell">
              <a-popconfirm v-if="record.allowManualRun" title="确定立即执行一次？执行结果会记录到操作日志"
                            @confirm="handleRun(record)">
                <a-button v-permission="'monitor:task:run'" type="link" size="small"
                          :loading="running === record.taskName">立即执行</a-button>
              </a-popconfirm>
            </div>
            <div class="grid-cell">
              <a-popconfirm title="删除后执行记录保留，但任务不再调度，确定删除？" @confirm="handleDelete(record)">
                <a-button v-permission="'monitor:task:delete'" type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </div>
          </div>
        </template>
      </template>
    </a-table>

    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑定时任务' : '新增定时任务'" :width="640"
             :confirm-loading="saving" @ok="handleSave">
      <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 17 }">
        <a-form-item label="任务名称" name="taskName">
          <a-input v-model:value="form.taskName" placeholder="例如 logCleanup，必须是服务里 Runner 的名称" />
        </a-form-item>
        <a-form-item label="归属服务" name="taskGroup">
          <a-select v-model:value="form.taskGroup" placeholder="选择任务所在服务">
            <a-select-option v-for="item in serviceOptions" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="调度类型" name="taskType">
          <a-radio-group v-model:value="form.taskType" @change="handleTaskTypeChange">
            <a-radio :value="1">cron 表达式</a-radio>
            <a-radio :value="2">固定延迟</a-radio>
            <a-radio :value="3">固定频率</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item :label="form.taskType === 1 ? 'cron 表达式' : '间隔（毫秒）'" name="scheduleValue">
          <a-input v-model:value="form.scheduleValue"
                   :placeholder="form.taskType === 1 ? '例如 0 0 3 * * ?' : '例如 60000'" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="form.status" :checkedValue="1" :unCheckedValue="0" />
          <span class="form-tip">关闭后任务暂停，仍然保留配置</span>
        </a-form-item>
        <a-form-item label="禁止并发">
          <a-switch v-model:checked="form.concurrent" :checkedValue="1" :unCheckedValue="0" />
          <span class="form-tip">开启后上一轮没跑完时跳过本轮</span>
        </a-form-item>
        <a-form-item label="备注" name="remark">
          <a-input v-model:value="form.remark" placeholder="任务说明，展示在列表里" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:visible="logVisible" :title="`执行日志 - ${logTaskName}`" width="720" placement="right">
      <a-table :dataSource="logs" :columns="logColumns" :pagination="false" row-key="id" size="small"
               :loading="logLoading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'red'">
              {{ record.status === 1 ? '成功' : '失败' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'triggerType'">
            {{ record.triggerType === 1 ? '定时' : '手动' }}
          </template>
          <template v-else-if="column.key === 'errorMsg'">
            <span class="error">{{ record.errorMsg || '-' }}</span>
          </template>
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  changeMonitorTaskStatus,
  createMonitorTask,
  deleteMonitorTask,
  getMonitorTaskLogs,
  getMonitorTasks,
  runMonitorTask,
  updateMonitorTask,
} from '@/api/monitor.js'

const loading = ref(false)
const list = ref([])
const running = ref('')
const statusLoading = ref(null)

const query = reactive({ keyword: '', status: undefined })

const columns = [
  { title: '任务名称', dataIndex: 'taskName', key: 'taskName' },
  { title: '归属服务', dataIndex: 'taskGroup', key: 'taskGroup' },
  { title: '调度类型', key: 'taskType' },
  { title: '执行周期', dataIndex: 'scheduleValue', key: 'scheduleValue' },
  { title: '状态', key: 'status' },
  { title: '最近执行', key: 'lastRun' },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '操作', key: 'action' },
]

const serviceOptions = [
  'auth-service',
  'order-service',
  'product-service',
  'aftersale-service',
  'user-service',
  'file-service',
]

const formVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  taskName: '',
  taskGroup: 'order-service',
  taskType: 1,
  scheduleValue: '0 0 3 * * ?',
  status: 1,
  concurrent: 1,
  remark: '',
})

const rules = {
  taskName: [{ required: true, message: '请输入任务名称' }],
  taskGroup: [{ required: true, message: '请选择归属服务' }],
  taskType: [{ required: true, message: '请选择调度类型' }],
  scheduleValue: [{ required: true, message: '请输入 cron 表达式或间隔毫秒数' }],
}

const logVisible = ref(false)
const logLoading = ref(false)
const logs = ref([])
const logTaskName = ref('')

const logColumns = [
  { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt' },
  { title: '触发', key: 'triggerType' },
  { title: '结果', key: 'status' },
  { title: '耗时', dataIndex: 'costMs', key: 'costMs', customRender: ({ text }) => `${text || 0} ms` },
  { title: '失败原因', key: 'errorMsg' },
]

function taskTypeLabel(type) {
  return type === 1 ? 'cron' : type === 2 ? '固定延迟' : '固定频率'
}

function load() {
  loading.value = true
  getMonitorTasks({ keyword: query.keyword || undefined, status: query.status })
    .then((res) => {
      list.value = res.data.data || []
    })
    .catch(() => message.error('获取定时任务失败'))
    .finally(() => {
      loading.value = false
    })
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    taskName: '',
    taskGroup: 'order-service',
    taskType: 1,
    scheduleValue: '0 0 3 * * ?',
    status: 1,
    concurrent: 1,
    remark: '',
  })
  formVisible.value = true
}

function openEdit(record) {
  editingId.value = record.id
  Object.assign(form, {
    taskName: record.taskName,
    taskGroup: record.taskGroup,
    taskType: record.taskType,
    scheduleValue: record.scheduleValue,
    status: record.status,
    concurrent: record.concurrent,
    remark: record.remark || '',
  })
  formVisible.value = true
}

function handleTaskTypeChange() {
  form.scheduleValue = form.taskType === 1 ? '0 0 3 * * ?' : '60000'
}

function handleSave() {
  formRef.value
    .validate()
    .then(() => {
      saving.value = true
      const payload = { ...form }
      const action = editingId.value
        ? updateMonitorTask(editingId.value, payload)
        : createMonitorTask(payload)
      return action
    })
    .then(() => {
      message.success('保存成功，各服务 30 秒内生效')
      formVisible.value = false
      load()
    })
    .catch((error) => {
      if (error?.response) message.error(error.response.data?.message || '保存失败')
    })
    .finally(() => {
      saving.value = false
    })
}

function handleStatus(record, checked) {
  statusLoading.value = record.id
  changeMonitorTaskStatus(record.id, checked ? 1 : 0)
    .then(() => {
      message.success(checked ? '已启用' : '已暂停')
      load()
    })
    .catch((error) => message.error(error.response?.data?.message || '操作失败'))
    .finally(() => {
      statusLoading.value = null
    })
}

function handleDelete(record) {
  deleteMonitorTask(record.id)
    .then(() => {
      message.success('已删除')
      load()
    })
    .catch((error) => message.error(error.response?.data?.message || '删除失败'))
}

function handleRun(record) {
  running.value = record.taskName
  runMonitorTask(record.taskName)
    .then((res) => {
      message.success(res.data.data?.message || '已触发执行')
      setTimeout(load, 1500)
    })
    .catch((error) => message.error(error.response?.data?.message || '执行失败'))
    .finally(() => {
      running.value = ''
    })
}

function openLogs(record) {
  logTaskName.value = record.taskName
  logVisible.value = true
  logLoading.value = true
  logs.value = []
  getMonitorTaskLogs(record.id, { limit: 50 })
    .then((res) => {
      logs.value = res.data.data || []
    })
    .catch(() => message.error('获取执行日志失败'))
    .finally(() => {
      logLoading.value = false
    })
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}
.tip {
  color: #999;
  font-size: 13px;
}
.sub {
  color: #888;
  font-size: 12px;
}
.error {
  color: #cf1322;
  font-size: 12px;
  margin-top: 2px;
}
.form-tip {
  color: #999;
  font-size: 12px;
  margin-left: 8px;
}
.action{
  display:grid;
  grid-template-columns: 50px 50px 80px 60px;
  gap: 4px;
  align-items: center;
  justify-content: center;
}
.grid-cell {
  /* 四个固定格子：某个按钮不存在（无权限 / 不支持手动执行）时格子仍在，后面的按钮不会左移 */
  display: block;
  text-align: center;
}
</style>
