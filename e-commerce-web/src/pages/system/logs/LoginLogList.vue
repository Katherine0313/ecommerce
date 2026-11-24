<template>
  <div>
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.username" placeholder="登录账号" allow-clear
                   @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.loginType" style="width: 120px" placeholder="类型" allow-clear
                    :options="loginTypeOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.resultCode" style="width: 170px" placeholder="结果" allow-clear
                    :options="resultCodeOptions" />
        </a-form-item>
        <a-form-item>
          <a-input v-model:value="query.ip" placeholder="IP 地址" allow-clear
                   @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-range-picker v-model:value="query.range" style="width: 320px" show-time
                          format="YYYY-MM-DD HH:mm:ss"
                          :placeholder="['开始时间', '结束时间']" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
        </a-form-item>
      </a-form>
      <div class="head-actions">
        <a-select v-model:value="exportFormat" style="width: 110px" :options="formatOptions" />
        <a-button v-permission="'system:log:login:export'" :loading="exporting" @click="handleExport">
          <DownloadOutlined />
          导出
        </a-button>
      </div>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'loginType'">
          <a-tag :color="record.loginType === 'LOGOUT' ? 'default' : 'blue'">
            {{ loginTypeLabel(record.loginType) }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'resultCode'">
          <!-- 非 SUCCESS 一律标红，方便一眼看出异常登录 -->
          <a-tag :color="record.resultCode === 'SUCCESS' ? 'green' : 'red'">
            {{ resultCodeLabel(record.resultCode) }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'userAgent'">
          <a-tooltip :title="record.userAgent || ''">
            <span class="ellipsis">{{ record.userAgent || '—' }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'reason'">
          {{ record.reason || '—' }}
        </template>
        <template v-else-if="column.key === 'operation'">
          <!-- 详情是只读查看，不加权限标识 -->
          <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>

    <!-- 日志详情：只读展示完整字段 -->
    <a-drawer v-model:visible="detailVisible" title="登录日志详情" :width="640">
      <a-descriptions :column="1" bordered size="small">
        <a-descriptions-item label="日志 ID">{{ text(current.id) }}</a-descriptions-item>
        <a-descriptions-item label="发生时间">{{ text(current.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="登录账号">{{ text(current.username) }}</a-descriptions-item>
        <a-descriptions-item label="管理员 ID">{{ text(current.adminId) }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ loginTypeLabel(current.loginType) }}</a-descriptions-item>
        <a-descriptions-item label="结果">
          <a-tag :color="current.resultCode === 'SUCCESS' ? 'green' : 'red'">
            {{ resultCodeLabel(current.resultCode) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="IP">{{ text(current.ip) }}</a-descriptions-item>
        <a-descriptions-item label="User-Agent">{{ text(current.userAgent) }}</a-descriptions-item>
        <a-descriptions-item label="失败原因">{{ text(current.reason) }}</a-descriptions-item>
      </a-descriptions>
    </a-drawer>
  </div>
</template>

<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { exportLoginLogs, getLoginLogs } from '@/api/system.js'
import { downloadBlob, readBlobErrorMessage } from '@/utils/download.js'

// 登录类型：LOGIN / LOGOUT
const loginTypeOptions = [
  { value: 'LOGIN', label: '登录' },
  { value: 'LOGOUT', label: '登出' },
]

// 结果码：与后端枚举一致，只给固定选项，不让用户手输
const resultCodeOptions = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'BAD_CREDENTIALS', label: '账号或密码错误' },
  { value: 'USER_DISABLED', label: '账号已停用' },
  { value: 'CAPTCHA_ERROR', label: '验证码错误' },
  { value: 'LOCKED', label: '账号已锁定' },
  { value: 'PARAM_MISSING', label: '参数缺失' },
]

const formatOptions = [
  { value: 'xlsx', label: 'Excel' },
  { value: 'csv', label: 'CSV' },
]

const list = ref([])
const exporting = ref(false)
const exportFormat = ref('xlsx')
const detailVisible = ref(false)
const current = ref({})

// 默认展示近 7 天：后端不传时间时也默认近 7 天，前端同步给出初始值，避免界面与实际查询不一致
const query = ref({
  username: '',
  loginType: undefined,
  resultCode: undefined,
  ip: '',
  range: [dayjs().subtract(7, 'day').startOf('day'), dayjs()],
})

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '类型', dataIndex: 'loginType', key: 'loginType' },
  { title: '结果', dataIndex: 'resultCode', key: 'resultCode' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: 'User-Agent', dataIndex: 'userAgent', key: 'userAgent' },
  { title: '原因', dataIndex: 'reason', key: 'reason' },
  { title: '操作', key: 'operation' },
]

// 空值统一显示「—」
function text(value) {
  return value === null || value === undefined || value === '' ? '—' : value
}

function loginTypeLabel(type) {
  if (!type) return '—'
  return loginTypeOptions.find((item) => item.value === type)?.label || type
}

function resultCodeLabel(code) {
  if (!code) return '—'
  return resultCodeOptions.find((item) => item.value === code)?.label || code
}

function buildParams() {
  const range = query.value.range || []
  return {
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    username: query.value.username || undefined,
    loginType: query.value.loginType || undefined,
    resultCode: query.value.resultCode || undefined,
    ip: query.value.ip || undefined,
    startTime: range[0] ? range[0].format('YYYY-MM-DD HH:mm:ss') : undefined,
    endTime: range[1] ? range[1].format('YYYY-MM-DD HH:mm:ss') : undefined,
  }
}

function loadData() {
  getLoginLogs(buildParams()).then((res) => {
    const data = res.data.data || {}
    list.value = data.list || []
    pagination.value.total = data.total || 0
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

function openDetail(record) {
  current.value = record
  detailVisible.value = true
}

// 导出与列表用同一套筛选条件，导出不分页
async function handleExport() {
  if (exporting.value) return
  exporting.value = true
  try {
    const params = buildParams()
    delete params.page
    delete params.pageSize
    const format = exportFormat.value
    const res = await exportLoginLogs({ ...params, format })
    // 出错时后端返回的是 JSON 而不是文件，读出来提示真实原因
    const errMsg = await readBlobErrorMessage(res.data)
    if (errMsg) {
      message.error(errMsg)
      return
    }
    const range = query.value.range || []
    const suffix = range[0] && range[1]
      ? `${range[0].format('YYYYMMDD')}_${range[1].format('YYYYMMDD')}`
      : dayjs().format('YYYYMMDD')
    downloadBlob(res.data, `登录日志_${suffix}.${format}`)
    message.success('导出成功')
  } catch (err) {
    console.error('导出登录日志失败', err)
    // 导出接口是文件流，出错时错误信息也在流里，读出来展示后端真实原因
    const errMsg = await readBlobErrorMessage(err.response?.data)
    message.error(errMsg || '导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  loadData()
})
// 页面被 keep-alive 缓存时，回到本页要刷新，否则看到的还是上次的日志
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
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* User-Agent 可能很长，超出部分省略号，鼠标悬停看全文 */
.ellipsis {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
</style>
