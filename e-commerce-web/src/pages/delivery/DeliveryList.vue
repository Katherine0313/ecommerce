<template>
  <div class="delivery-page">
    <a-tabs v-model:activeKey="activeKey" @change="onTabChange">
      <!-- ==================== Tab 1：快递发货列表 ==================== -->
      <a-tab-pane key="delivery" tab="快递发货列表">
        <a-form layout="inline" class="head">
          <a-form-item>
            <a-input v-model:value="query.orderNo" placeholder="订单号" allow-clear style="width: 180px"
                     @pressEnter="handleDeliverySearch" />
          </a-form-item>
          <a-form-item>
            <a-input v-model:value="query.trackingNumber" placeholder="快递单号" allow-clear style="width: 180px"
                     @pressEnter="handleDeliverySearch" />
          </a-form-item>
          <a-form-item>
            <a-select v-model:value="query.expressCode" placeholder="快递公司" allow-clear style="width: 160px"
                      :options="expressOptions" />
          </a-form-item>
          <a-form-item>
            <a-select v-model:value="query.deliveryType" placeholder="发货类型" allow-clear style="width: 140px"
                      :options="deliveryTypeOptions" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleDeliverySearch">
              <SearchOutlined />
              搜索
            </a-button>
          </a-form-item>
          <a-form-item>
            <a-button @click="resetDeliveryQuery">重置</a-button>
          </a-form-item>
        </a-form>

        <a-table :dataSource="deliveryList" :columns="deliveryColumns" :pagination="deliveryPagination"
                 :loading="deliveryLoading" row-key="id"  @change="onDeliveryTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'deliveryType'">
              <a-tag :color="record.deliveryType === 2 ? 'orange' : 'blue'">{{ record.deliveryTypeDesc }}</a-tag>
            </template>
            <template v-else-if="column.key === 'address'">
              {{ record.region }} {{ record.detailAddress }}
            </template>
            <template v-else-if="column.key === 'orderStatus'">
              <a-tag :color="orderStatus.color[record.orderStatus] || 'default'">
                {{ record.orderStatusDesc || record.orderStatus }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'signStatus'">
              <a-tag :color="signStatusColor(record.signStatus)">{{ signStatusText(record.signStatus) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <div class="action-buttons">
                <a-button size="small" type="primary" @click="openLogs(record)">
                  查看流水
                </a-button>
                <a-button size="small" @click="openLogistics(record)">
                  查看物流
                </a-button>
                <a-button v-permission="'order:delivery:resend'" size="small" @click="openModal('resend', record)">
                  补发
                </a-button>
                <a-button v-permission="'order:delivery:ship'" size="small" @click="openModal('edit', record)">
                  编辑
                </a-button>
              </div>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- ==================== Tab 2：待发货订单 ==================== -->
      <a-tab-pane key="pending" tab="待发货订单">
        <a-form layout="inline" class="head">
          <a-form-item>
            <a-input-search v-model:value="pendingQuery.keyword" style="width: 380px" enter-button="搜索"
                            placeholder="商品名称/订单编号/收货人姓名/手机号" allow-clear @search="handlePendingSearch" />
          </a-form-item>
          <a-form-item style="font-size: 16px; margin: 0 20px;">
            <DateRangeSelect ref="dateRangeRef" :options="dateOptions" @change="onDateFilterChange" />
          </a-form-item>
          <a-form-item>
            <a-button @click="resetPendingQuery">刷新</a-button>
          </a-form-item>
        </a-form>

        <a-table :dataSource="pendingList" :columns="pendingColumns" :pagination="pendingPagination"
                 :loading="pendingLoading" row-key="id"  @change="onPendingTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'address'">
              {{ record.region }} {{ record.detailAddress }}
            </template>
            <template v-else-if="column.key === 'orderStatus'">
              <a-tag :color="orderStatus.color[record.orderStatus] || 'default'">
                {{ orderStatus.map[record.orderStatus] || record.orderStatus }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button v-permission="'order:delivery:ship'" type="primary" size="small"
                        @click="openModal('ship', record)">去发货</a-button>
            </template>
          </template>
        </a-table>

        <!-- 待发货为空时给个空态提示（原先这里提示「N 单待确认收款」，该状态已退役） -->
        <div v-if="!pendingLoading && pendingList.length === 0" class="empty-hint">
          <a-empty description="暂无待发货订单" />
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- ==================== 发货流水抽屉 ==================== -->
    <a-drawer v-model:visible="logDrawerVisible" title="发货流水" :width="480">
      <a-timeline v-if="logList.length">
        <a-timeline-item v-for="log in logList" :key="log.id" :color="logColor(log.operateType)">
          <div class="log-title">{{ log.operateTypeDesc }}</div>
          <div class="log-content">{{ log.content }}</div>
          <div v-if="log.remark" class="log-remark">备注：{{ log.remark }}</div>
          <div class="log-time">{{ log.createdAt }} · 操作人 ID {{ log.operatorId }}</div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-else description="暂无发货流水" />
    </a-drawer>

    <!-- ==================== 物流轨迹抽屉 ==================== -->
    <LogisticsDrawer ref="logisticsRef" />

    <!-- ==================== 发货 / 补发 / 编辑 共用弹窗 ==================== -->
    <a-modal v-model:visible="modalVisible" :title="modalTitle" :width="760" :confirm-loading="submitting"
             @ok="handleSubmit">
      <!-- 发货时展示收货地址与商品明细，避免发错货 -->
      <template v-if="modalMode === 'ship' && currentOrder">
        <a-descriptions :column="2" bordered size="small" style="margin-bottom: 16px;">
          <a-descriptions-item label="订单编号">{{ currentOrder.orderNo }}</a-descriptions-item>
          <a-descriptions-item label="收货人">{{ currentOrder.consignee }} {{ maskPhone(currentOrder.phone) }}</a-descriptions-item>
          <a-descriptions-item label="收货地址" :span="2">
            {{ currentOrder.region }} {{ currentOrder.detailAddress }}
          </a-descriptions-item>
          <a-descriptions-item label="买家备注" :span="2">{{ currentOrder.remark || '—' }}</a-descriptions-item>
        </a-descriptions>
        <a-table :dataSource="currentOrder.items || []" row-key="productId" :pagination="false" size="small"
                 style="margin-bottom: 16px;">
          <a-table-column title="商品名称" dataIndex="productName"></a-table-column>
          <a-table-column title="商品图片" width="100">
            <template #default="{ record }">
              <img v-lazy="record.image" width="60" />
            </template>
          </a-table-column>
          <a-table-column title="购买数量" dataIndex="quantity" width="100"></a-table-column>
        </a-table>
      </template>
      <template v-else>
        <a-descriptions :column="2" bordered size="small" style="margin-bottom: 16px;">
          <a-descriptions-item label="订单编号">{{ currentOrder?.orderNo }}</a-descriptions-item>
          <a-descriptions-item label="包裹编号">{{ currentRecord?.packageNo || '—' }}</a-descriptions-item>
        </a-descriptions>
      </template>

      <a-form :model="form" layout="vertical">
        <a-form-item label="快递公司" required>
          <a-select v-model:value="form.expressCode" placeholder="请选择快递公司" style="width: 220px"
                    :options="expressOptions" />
        </a-form-item>
        <a-form-item label="快递单号" required>
          <a-input v-model:value="form.trackingNumber" placeholder="请填写快递单号" style="width: 320px" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" placeholder="选填，如走哪个仓/批次发出" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import { getAllOrders, getOrderStatus } from '@/api/orders.js'
import LogisticsDrawer from '@/pages/orders/LogisticsDrawer.vue'
import { createDelivery, getDeliveryList, getDeliveryLogs, getEnabledExpresses, resendDelivery, updateDelivery } from '@/api/delivery.js'
import DateRangeSelect from '@/components/date-range-select.vue'
import { maskPhone } from '@/utils/mask.js'
import { hasPermi } from '@/utils/auth.js'

const activeKey = ref('delivery')

// ==================== 快递公司下拉（页面级内存缓存，只拉一次） ====================
const expressOptions = ref([])
// expressCode -> 中文名，用于二次确认文案
const expressNameMap = ref({})

function loadExpressOptions() {
  if (expressOptions.value.length) return
  // 字典接口要 order:express:list；没有这个权限的角色（如只读包裹的客服）直接跳过，
  // 否则页面一进来就弹「无权访问该功能」
  if (!hasPermi('order:express:list')) return
  getEnabledExpresses()
      .then((res) => {
        const list = res.data?.data || []
        expressOptions.value = list.map((e) => ({ value: e.code, label: e.name }))
        const map = {}
        list.forEach((e) => {
          map[e.code] = e.name
        })
        expressNameMap.value = map
      })
      .catch((err) => console.log(err))
}

// ==================== 订单状态常量（状态标签配色） ====================
const orderStatus = ref({ map: {}, color: {} })

function loadOrderStatus() {
  getOrderStatus()
      .then((res) => {
        orderStatus.value = res.data?.data || { map: {}, color: {} }
      })
      .catch((err) => console.log(err))
}

// ==================== Tab 1：包裹列表 ====================
const deliveryList = ref([])
const deliveryLoading = ref(false)
const query = ref({ orderNo: '', trackingNumber: '', expressCode: undefined, deliveryType: undefined })

const deliveryPagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const deliveryTypeOptions = [
  { value: 1, label: '正常发货' },
  { value: 2, label: '补发' },
]

const deliveryColumns = [
  { title: '订单号', dataIndex: 'orderNo', key: 'orderNo' },
  { title: '包裹编号', dataIndex: 'packageNo', key: 'packageNo' },
  { title: '快递公司', dataIndex: 'expressName', key: 'expressName',width:150},
  { title: '快递单号', dataIndex: 'trackingNumber', key: 'trackingNumber' },
  { title: '发货类型', key: 'deliveryType' },
  { title: '发货时间', dataIndex: 'deliveryTime', key: 'deliveryTime' },
  { title: '收货地址', key: 'address' ,width:150},
  { title: '手机号', dataIndex: 'phone', key: 'phone', customRender: ({ text }) => maskPhone(text) },
  { title: '买家备注', dataIndex: 'buyerRemark', key: 'buyerRemark',width:100},
  { title: '订单状态', key: 'orderStatus' },
  { title: '签收状态', key: 'signStatus'},
  // 不设 fixed:'right'：有 fixed 列 antd 会切 table-layout: fixed，无宽度的列平分剩余空间，
  // 长文本列（收货地址/买家备注）会被挤爆变形、内容被裁掉；去掉后走 auto 布局按内容自适应
  { title: '操作', key: 'action' ,width:100},
]

function loadDeliveryList() {
  deliveryLoading.value = true
  getDeliveryList({
    page: deliveryPagination.value.current,
    pageSize: deliveryPagination.value.pageSize,
    orderNo: query.value.orderNo || undefined,
    trackingNumber: query.value.trackingNumber || undefined,
    expressCode: query.value.expressCode,
    deliveryType: query.value.deliveryType,
  })
      .then((res) => {
        const data = res.data?.data || {}
        deliveryList.value = data.list || []
        deliveryPagination.value.total = data.total || 0
      })
      .catch((err) => console.log(err))
      .finally(() => {
        deliveryLoading.value = false
      })
}

function handleDeliverySearch() {
  deliveryPagination.value.current = 1
  loadDeliveryList()
}

function resetDeliveryQuery() {
  query.value = { orderNo: '', trackingNumber: '', expressCode: undefined, deliveryType: undefined }
  deliveryPagination.value.current = 1
  loadDeliveryList()
}

function onDeliveryTableChange(pager) {
  deliveryPagination.value.current = pager.current
  deliveryPagination.value.pageSize = pager.pageSize
  loadDeliveryList()
}

// ==================== Tab 2：待发货订单 ====================
const pendingList = ref([])
const pendingLoading = ref(false)
const pendingQuery = ref({ keyword: '' })

const pendingPagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

// 时间筛选用下单/支付/发货三个时间口径
const dateOptions = [
  { value: 'created_at', label: '下单时间' },
  { value: 'pay_at', label: '支付时间' },
  { value: 'ship_at', label: '发货时间' },
]
const dateRangeRef = ref(null)
const dateFilter = ref({ dateField: 'created_at', startDate: '', endDate: '' })

// 列宽不写死：由表格按内容自适应分配，表格整体撑满容器，右侧不留空白
const pendingColumns = [
  { title: '订单编号', dataIndex: 'orderNo', key: 'orderNo' },
  { title: '支付时间', dataIndex: 'payAt', key: 'payAt' },
  { title: '订单金额', dataIndex: 'payAmount', key: 'payAmount' },
  { title: '支付方式', dataIndex: 'payTypeDesc', key: 'payTypeDesc' },
  { title: '客户', dataIndex: 'consignee', key: 'consignee' },
  { title: '收货地址', key: 'address' },
  { title: '手机号', dataIndex: 'phone', key: 'phone', customRender: ({ text }) => maskPhone(text) },
  { title: '买家备注', dataIndex: 'remark', key: 'remark' },
  { title: '订单状态', key: 'orderStatus' },
  // 同样不设 fixed:'right'，理由同上
  { title: '操作', key: 'action' },
]

function loadPendingList() {
  pendingLoading.value = true
  getAllOrders({
    status: 3,
    page: pendingPagination.value.current,
    pageSize: pendingPagination.value.pageSize,
    keyword: pendingQuery.value.keyword || '',
    dateField: dateFilter.value.dateField,
    startDate: dateFilter.value.startDate || undefined,
    endDate: dateFilter.value.endDate || undefined,
  })
      .then((res) => {
        const data = res.data?.data || {}
        pendingList.value = data.list || []
        pendingPagination.value.total = data.total || 0
        pendingPagination.value.pageSize = data.pageSize || pendingPagination.value.pageSize
        pendingPagination.value.current = data.page || pendingPagination.value.current
      })
      .catch((err) => console.log(err))
      .finally(() => {
        pendingLoading.value = false
      })
}

function handlePendingSearch() {
  pendingPagination.value.current = 1
  loadPendingList()
}

function resetPendingQuery() {
  pendingQuery.value = { keyword: '' }
  dateRangeRef.value?.reset()
  pendingPagination.value.current = 1
  loadPendingList()
}

function onDateFilterChange(filter) {
  dateFilter.value = filter
  pendingPagination.value.current = 1
  loadPendingList()
}

function onPendingTableChange(pager) {
  pendingPagination.value.current = pager.current
  pendingPagination.value.pageSize = pager.pageSize
  loadPendingList()
}

// ==================== 发货流水抽屉 ====================
const logDrawerVisible = ref(false)
const logList = ref([])

function openLogs(record) {
  logList.value = []
  logDrawerVisible.value = true
  getDeliveryLogs(record.orderId)
      .then((res) => {
        logList.value = res.data?.data || []
      })
      .catch((err) => console.log(err))
}

// ==================== 物流轨迹抽屉 ====================
// 轨迹是后端生成的假数据（不接真实物流 API），快递公司与单号取发货时写入的真实值
const logisticsRef = ref(null)

function openLogistics(record) {
  logisticsRef.value?.open(record.orderId)
}

// 流水时间线配色：创建发货/补发/修改快递信息
function logColor(operateType) {
  return { 1: 'green', 2: 'orange', 3: 'blue' }[operateType] || 'gray'
}

// ==================== 发货 / 补发 / 编辑 共用弹窗 ====================
const modalVisible = ref(false)
const modalMode = ref('ship')
const submitting = ref(false)
const currentOrder = ref(null)
const currentRecord = ref(null)
const form = ref({ expressCode: undefined, trackingNumber: '', remark: '' })

const modalTitle = ref('发货')

function openModal(mode, record) {
  modalMode.value = mode
  currentRecord.value = record
  loadExpressOptions()
  if (mode === 'ship') {
    // 发货：记录是订单，弹窗要展示收货地址 + 商品明细
    modalTitle.value = '发货'
    currentOrder.value = record
    form.value = { expressCode: undefined, trackingNumber: '', remark: '' }
  } else if (mode === 'resend') {
    modalTitle.value = '补发'
    currentOrder.value = { orderNo: record.orderNo }
    form.value = { expressCode: record.expressCode, trackingNumber: '', remark: '' }
  } else {
    modalTitle.value = '修改快递信息'
    currentOrder.value = { orderNo: record.orderNo }
    form.value = {
      expressCode: record.expressCode,
      trackingNumber: record.trackingNumber,
      remark: record.remark,
    }
  }
  modalVisible.value = true
}

function handleSubmit() {
  if (!form.value.expressCode) {
    message.warning('请选择快递公司')
    return
  }
  if (!form.value.trackingNumber || !form.value.trackingNumber.trim()) {
    message.warning('请填写快递单号')
    return
  }
  const orderNo = currentOrder.value?.orderNo || ''
  const expressName = expressNameMap.value[form.value.expressCode] || form.value.expressCode
  const trackingNumber = form.value.trackingNumber.trim()
  const payload = {
    expressCode: form.value.expressCode,
    trackingNumber,
    remark: form.value.remark,
  }
  // 发货前二次确认：文案带订单号 + 快递公司 + 单号
  Modal.confirm({
    title: modalMode.value === 'edit' ? '确认修改快递信息' : '确认发货',
    content: `订单 ${orderNo}，${expressName}，快递单号 ${trackingNumber}，确认${modalMode.value === 'edit' ? '修改' : '发货'}吗？`,
    onOk: () => doSubmit(payload),
  })
}

function doSubmit(payload) {
  submitting.value = true
  let request
  if (modalMode.value === 'ship') {
    request = createDelivery(currentOrder.value.id, payload)
  } else if (modalMode.value === 'resend') {
    request = resendDelivery(currentRecord.value.orderId, payload)
  } else {
    request = updateDelivery(currentRecord.value.id, payload)
  }
  return request
      .then(() => {
        message.success(modalMode.value === 'edit' ? '快递信息已更新' : (modalMode.value === 'resend' ? '补发成功' : '发货成功'))
        modalVisible.value = false
        loadDeliveryList()
        if (activeKey.value === 'pending') {
          loadPendingList()
        }
      })
      .catch((err) => {
        // 错误提示由 request.js 拦截器统一弹，这里不再重复弹
        console.log(err)
      })
      .finally(() => {
        submitting.value = false
      })
}

// ==================== 签收状态三态 ====================
function signStatusText(signStatus) {
  return { signed: '已签收', auto: '超时自动完成', unsigned: '未签收' }[signStatus] || '未签收'
}

function signStatusColor(signStatus) {
  return { signed: 'green', auto: 'blue', unsigned: 'default' }[signStatus] || 'default'
}

// ==================== 生命周期 ====================
function onTabChange(key) {
  if (key === 'pending') {
    loadPendingList()
  } else {
    loadDeliveryList()
  }
}

onMounted(() => {
  loadOrderStatus()
  loadExpressOptions()
  loadDeliveryList()
})
onActivated(() => {
  loadDeliveryList()
})
</script>

<style scoped>
.head {
  margin: 20px 0 10px;
}

/* 操作列：4 个按钮排 2×2，避免一行铺开占掉太多表格宽度（窄屏时更容易挤） */
.action-buttons {
  display: grid;
  grid-template-columns: repeat(2, auto);
  gap: 6px;
}


.empty-hint {
  margin-top: -40px;
}

/* 长地址/长备注自动换行：表格是 auto 布局（无 fixed 列），列宽按内容分配，
   遇到长数字串（快递单号、备注）不换行会撑破单元格 */
:deep(.ant-table-tbody > tr > td) {
  word-break: break-word;
}

.log-title {
  font-weight: 600;
  color: #333;
}

.log-content {
  font-size: 13px;
  color: #666;
}

.log-remark {
  font-size: 12px;
  color: #999;
}

.log-time {
  font-size: 12px;
  color: #999;
}
</style>
