<template>
  <div>
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="账号 / 详情 / URI" allow-clear
                   @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.module" style="width: 120px" placeholder="模块" allow-clear
                    :options="moduleOptions" @change="handleModuleChange" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.action" style="width: 150px" placeholder="动作" allow-clear
                    :options="actionOptions" />
        </a-form-item>
        <a-form-item>
          <a-input v-model:value="query.username" placeholder="操作账号" allow-clear
                   @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.success" style="width: 110px" placeholder="结果" allow-clear
                    :options="successOptions" />
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
        <a-button v-permission="'system:log:export'" :loading="exporting" @click="handleExport">
          <DownloadOutlined />
          导出
        </a-button>
      </div>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'module'">
          <a-tag color="blue">{{ moduleLabel(record.module) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          {{ record.actionName || record.action || '—' }}
        </template>
        <template v-else-if="column.key === 'result'">
          <a-tag :color="successColor(record.success)">
            {{ successText(record.success) }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'costMs'">
          {{ record.costMs === null || record.costMs === undefined ? '—' : `${record.costMs} ms` }}
        </template>
        <template v-else-if="column.key === 'uri'">
          <a-tooltip :title="record.uri || ''">
            <span class="ellipsis">{{ record.uri || '—' }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'operation'">
          <!-- 详情是只读查看，不加权限标识 -->
          <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
        </template>
      </template>
    </a-table>

    <!-- 日志详情：只读展示完整字段 -->
    <a-drawer v-model:visible="detailVisible" title="操作日志详情" :width="640">
      <a-descriptions :column="1" bordered size="small">
        <a-descriptions-item label="日志 ID">{{ text(current.id) }}</a-descriptions-item>
        <a-descriptions-item label="发生时间">{{ text(current.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="操作账号">{{ text(current.username) }}</a-descriptions-item>
        <a-descriptions-item label="操作人 ID">{{ text(current.userId) }}</a-descriptions-item>
        <a-descriptions-item label="用户类型">{{ text(current.userType) }}</a-descriptions-item>
        <a-descriptions-item label="模块">{{ moduleLabel(current.module) }}</a-descriptions-item>
        <a-descriptions-item label="动作">{{ text(current.actionName || current.action) }}</a-descriptions-item>
        <a-descriptions-item label="业务对象 ID">{{ text(current.bizId) }}</a-descriptions-item>
        <a-descriptions-item label="结果">
          <a-tag :color="successColor(current.success)">
            {{ successText(current.success) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="耗时">
          {{ current.costMs === null || current.costMs === undefined ? '—' : `${current.costMs} ms` }}
        </a-descriptions-item>
        <a-descriptions-item label="IP">{{ text(current.ip) }}</a-descriptions-item>
        <a-descriptions-item label="请求方法">{{ text(current.method) }}</a-descriptions-item>
        <a-descriptions-item label="请求 URI">{{ text(current.uri) }}</a-descriptions-item>
        <a-descriptions-item label="变更详情">{{ maskPhones(current.detail) }}</a-descriptions-item>
        <a-descriptions-item label="请求参数">
          <pre class="code-block">{{ formattedParams }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="错误信息">{{ maskPhones(current.errorMsg) }}</a-descriptions-item>
      </a-descriptions>
    </a-drawer>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { exportOperationLogs, getOperationLogs } from '@/api/system.js'
import { downloadBlob, readBlobErrorMessage } from '@/utils/download.js'
import { maskPhone } from '@/utils/mask.js'

// 模块选项：与后端打点时的 module 值一一对应，只给固定选项，不让用户手输
const moduleOptions = [
  { value: 'aftersales', label: '售后' },
  { value: 'delivery', label: '发货物流' },
  { value: 'system', label: '系统管理' },
  { value: 'product', label: '商品' },
  { value: 'order', label: '订单' },
  { value: 'promotion', label: '营销' },
]

// 动作选项：与后端打点时的 action 值一一对应（全大写），module 用于按模块过滤下拉
const allActions = [
  { value: 'REVIEW', label: '审核', module: 'aftersales' },
  { value: 'DELETE', label: '删除', module: 'aftersales' },
  { value: 'SHIP', label: '发货', module: 'delivery' },
  { value: 'RESEND', label: '补发', module: 'delivery' },
  { value: 'UPDATE_DELIVERY', label: '修改物流', module: 'delivery' },
  { value: 'EXPRESS_SAVE', label: '新增快递公司', module: 'delivery' },
  { value: 'EXPRESS_UPDATE', label: '修改快递公司', module: 'delivery' },
  { value: 'EXPRESS_REMOVE', label: '删除快递公司', module: 'delivery' },
  { value: 'USER_ADD', label: '新增管理员', module: 'system' },
  { value: 'USER_EDIT', label: '修改管理员', module: 'system' },
  { value: 'USER_REMOVE', label: '删除管理员', module: 'system' },
  { value: 'RESET_PWD', label: '重置密码', module: 'system' },
  { value: 'ROLE_ADD', label: '新增角色', module: 'system' },
  { value: 'ROLE_EDIT', label: '修改角色', module: 'system' },
  { value: 'ROLE_REMOVE', label: '删除角色', module: 'system' },
  { value: 'ROLE_ASSIGN', label: '分配菜单', module: 'system' },
  { value: 'MENU_ADD', label: '新增菜单', module: 'system' },
  { value: 'MENU_EDIT', label: '修改菜单', module: 'system' },
  { value: 'MENU_REMOVE', label: '删除菜单', module: 'system' },
  { value: 'LOG_EXPORT', label: '导出日志', module: 'system' },
  { value: 'LOG_CLEAN', label: '清理日志', module: 'system' },
  { value: 'PRODUCT_ADD', label: '新增商品', module: 'product' },
  { value: 'PRODUCT_EDIT', label: '修改商品', module: 'product' },
  { value: 'PRODUCT_REMOVE', label: '删除商品', module: 'product' },
  { value: 'STOCK_ALERT', label: '库存预警设置', module: 'product' },
  { value: 'ORDER_UPDATE', label: '修改订单', module: 'order' },
  { value: 'ORDER_CANCEL', label: '取消订单', module: 'order' },
  { value: 'ORDER_REMOVE', label: '删除订单', module: 'order' },
  { value: 'ORDER_BATCH_REMOVE', label: '批量删除订单', module: 'order' },
  { value: 'COUPON_ADD', label: '新增优惠券', module: 'promotion' },
  { value: 'COUPON_EDIT', label: '修改优惠券', module: 'promotion' },
  { value: 'COUPON_STOP', label: '停用优惠券', module: 'promotion' },
  { value: 'COUPON_REMOVE', label: '删除优惠券', module: 'promotion' },
  { value: 'COUPON_GRANT', label: '发放优惠券', module: 'promotion' },
  { value: 'BANNER_ADD', label: '新增Banner', module: 'promotion' },
  { value: 'BANNER_EDIT', label: '修改Banner', module: 'promotion' },
  { value: 'BANNER_STATUS', label: 'Banner上下架', module: 'promotion' },
  { value: 'BANNER_REMOVE', label: '删除Banner', module: 'promotion' },
  { value: 'ARTICLE_ADD', label: '新增文章', module: 'promotion' },
  { value: 'ARTICLE_EDIT', label: '修改文章', module: 'promotion' },
  { value: 'ARTICLE_STATUS', label: '文章上下架', module: 'promotion' },
  { value: 'ARTICLE_REMOVE', label: '删除文章', module: 'promotion' },
]

const successOptions = [
  { value: 1, label: '成功' },
  { value: 0, label: '失败' },
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
  keyword: '',
  module: undefined,
  action: undefined,
  username: '',
  success: undefined,
  range: [dayjs().subtract(7, 'day').startOf('day'), dayjs()],
})

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

// 选了模块后只显示该模块下的动作，避免出现「售后 + 发货」这种不存在的组合
const actionOptions = computed(() => {
  const module = query.value.module
  return allActions
    .filter((item) => !module || item.module === module)
    .map((item) => ({ value: item.value, label: item.label }))
})

const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '账号', dataIndex: 'username', key: 'username' },
  { title: '模块', dataIndex: 'module', key: 'module' },
  { title: '动作', dataIndex: 'action', key: 'action' },
  { title: '业务对象 ID', dataIndex: 'bizId', key: 'bizId' },
  { title: '结果', key: 'result' },
  { title: '耗时', dataIndex: 'costMs', key: 'costMs' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: 'URI', dataIndex: 'uri', key: 'uri' },
  { title: '操作', key: 'operation' },
]

// 空值统一显示「—」
function text(value) {
  return value === null || value === undefined || value === '' ? '—' : value
}

// 日志原文保持完整号码，只有后台展示时替换文本中的手机号
function maskPhones(value) {
  return String(text(value)).replace(/\b1[3-9]\d{9}\b/g, (phone) => maskPhone(phone))
}

// 列表里的 success 是布尔值（查询参数才是 1/0），两种写法都兼容一下
function successFlag(value) {
  if (value === true || value === 1) return true
  if (value === false || value === 0) return false
  return null
}

function successText(value) {
  const flag = successFlag(value)
  return flag === null ? '—' : flag ? '成功' : '失败'
}

function successColor(value) {
  const flag = successFlag(value)
  return flag === null ? 'default' : flag ? 'green' : 'red'
}

function moduleLabel(module) {
  if (!module) return '—'
  return moduleOptions.find((item) => item.value === module)?.label || module
}

// params 能解析就格式化，解析失败保留原文结构；两种情况的展示值都脱敏
const formattedParams = computed(() => {
  const raw = current.value.params
  if (!raw) return '—'
  try {
    return maskPhones(JSON.stringify(JSON.parse(raw), null, 2))
  } catch (e) {
    return maskPhones(raw)
  }
})

function buildParams() {
  const range = query.value.range || []
  return {
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    module: query.value.module || undefined,
    action: query.value.action || undefined,
    username: query.value.username || undefined,
    // success 用 0 表示失败，不能用真值判断
    success: query.value.success === undefined ? undefined : query.value.success,
    startTime: range[0] ? range[0].format('YYYY-MM-DD HH:mm:ss') : undefined,
    endTime: range[1] ? range[1].format('YYYY-MM-DD HH:mm:ss') : undefined,
  }
}

function loadData() {
  getOperationLogs(buildParams()).then((res) => {
    console.log(res)
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

// 切换模块时清空已选动作，因为动作下拉只剩该模块下的选项，旧值可能已不可见
function handleModuleChange() {
  query.value.action = undefined
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
    const res = await exportOperationLogs({ ...params, format })
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
    downloadBlob(res.data, `操作日志_${suffix}.${format}`)
    message.success('导出成功')
  } catch (err) {
    console.error('导出操作日志失败', err)
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

/* URI 可能很长，超出部分省略号，鼠标悬停看全文 */
.ellipsis {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

/* 请求参数是格式化后的 JSON，用等宽字体 + 保留换行展示 */
.code-block {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
</style>
