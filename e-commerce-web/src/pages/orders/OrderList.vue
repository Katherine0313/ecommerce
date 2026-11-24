<template>
  <div class="order-container">
    <a-form style="margin:20px 0 10px;" layout="inline">
      <a-form-item>
        <a-input-search style=" width:400px;"
                        enter-button="搜索"
                        placeholder="商品名称/订单编号/收货人姓名/手机号"
                        v-model:value="searchKeyWord"
                        @search="onOrderSearch"
                        allow-clear
        >
        </a-input-search>
      </a-form-item>
      <a-form-item style="font-size:16px;margin:0 40px;">
        <DateRangeSelect ref="dateRangeRef" @change="onDateFilterChange" />
      </a-form-item>
      <a-form-item>
        <a-button style="font-size:15px; height:32px; width:100px;"
                  @click="reloadOrders">刷新
        </a-button>
      </a-form-item>
    </a-form>
    <a-tabs v-model:activeKey="activeKey" size="large" @change="onTabChange">
      <a-tab-pane key="1" tab="全部订单">
        <AllOrders :list="tabs.all.list"
                    :order-status="orderStatus"
                    :pagination="tabs.all.pagination"
                    @page-change="(p) => loadTab('all', p)"
                    @view="handleDetailOrdersOpen"
                    @delete="handleDeleteOrder"
                    @batchDelete="handleBatchDeleteOrders"
        />
      </a-tab-pane>
      <a-tab-pane key="2" tab="待付款" force-render>
        <PendingPayOrders :list="tabs.pendingPay.list"
                    :order-status="orderStatus"
                    :pagination="tabs.pendingPay.pagination"
                    @page-change="(p) => loadTab('pendingPay', p)"
                    @view="handleDetailOrdersOpen"
                    @delete="handleDeleteOrder"
                    @batchDelete="handleBatchDeleteOrders"
                    @refresh="reloadCurrent"
        />
      </a-tab-pane>
      <a-tab-pane key="3" tab="待发货">
        <PendingShipOrders :list="tabs.pendingShip.list"
                    :order-status="orderStatus"
                    :pagination="tabs.pendingShip.pagination"
                    @page-change="(p) => loadTab('pendingShip', p)"
                    @view="handleDetailOrdersOpen"
                    @delete="handleDeleteOrder"
                    @batchDelete="handleBatchDeleteOrders"
                    @refresh="reloadCurrent"
        />
      </a-tab-pane>
      <a-tab-pane key="4" tab="已发货">
        <AllOrders :list="tabs.shipped.list"
                    :order-status="orderStatus"
                    :pagination="tabs.shipped.pagination"
                    @page-change="(p) => loadTab('shipped', p)"
                    @view="handleDetailOrdersOpen"
                    @delete="handleDeleteOrder"
                    @batchDelete="handleBatchDeleteOrders"
        />
      </a-tab-pane>
      <a-tab-pane key="5" tab="已完成">
        <AllOrders :list="tabs.finished.list"
                    :order-status="orderStatus"
                    :pagination="tabs.finished.pagination"
                    @page-change="(p) => loadTab('finished', p)"
                    @view="handleDetailOrdersOpen"
                    @delete="handleDeleteOrder"
                    @batchDelete="handleBatchDeleteOrders"
        />
      </a-tab-pane>
      <a-tab-pane key="7" tab="已取消">
        <CancelledOrders :list="tabs.cancelled.list"
                    :order-status="orderStatus"
                    :pagination="tabs.cancelled.pagination"
                    @page-change="(p) => loadTab('cancelled', p)"
                    @view="handleDetailOrdersOpen"
                    @delete="handleDeleteOrder"
                    @batchDelete="handleBatchDeleteOrders"
        />
      </a-tab-pane>
    </a-tabs>
    <DetailOrders ref="detailOrdersRef" />
  </div>
</template>
<script setup>
import {ref, onMounted, onActivated, reactive} from 'vue';
import {getAllOrders, getOrderStatus, deleteOrder, deleteOrders} from '@/api/orders.js'
import DateRangeSelect from '@/components/date-range-select.vue'
import AllOrders from './AllOrders.vue'
import PendingPayOrders from './PendingPayOrders.vue'
import PendingShipOrders from './PendingShipOrders.vue'
import CancelledOrders from './CancelledOrders.vue'
import {message, Modal} from 'ant-design-vue'
import DetailOrders from "@/pages/orders/DetailOrders.vue";

const activeKey = ref('1');

// 各状态 tab 的独立数据：status（后端筛选）+ list（当前页）+ pagination（分页器，pageSize 从后端响应读）
// 状态码与后端 OrderStatusEnum 对齐：1待付款 3待发货 4已发货 5已完成 6已取消（2待确认收款已退役）
const tabs = reactive({
  all: { status: null, list: [], pagination: { current: 1, total: 0 } },
  pendingPay: { status: 1, list: [], pagination: { current: 1, total: 0 } },
  pendingShip: { status: 3, list: [], pagination: { current: 1, total: 0 } },
  shipped: { status: 4, list: [], pagination: { current: 1, total: 0 } },
  finished: { status: 5, list: [], pagination: { current: 1, total: 0 } },
  cancelled: { status: 6, list: [], pagination: { current: 1, total: 0 } },
})
// tab key -> tab 名映射
const keyMap = { '1': 'all', '2': 'pendingPay', '3': 'pendingShip', '4': 'shipped', '5': 'finished', '7': 'cancelled' }

// 加载某个 tab 的指定页（后端分页，pageSize 从响应读，前端不写死）
const loadTab = (tabName, page = 1) => {
  const tab = tabs[tabName]
  getAllOrders({
    status: tab.status,
    page,
    keyword: searchKeyWord.value || '',
    dateField: dateFilter.value.dateField,
    startDate: dateFilter.value.startDate || undefined,
    endDate: dateFilter.value.endDate || undefined,
  })
      .then(res => {
        tab.list = res.data.data.list
        tab.pagination.total = res.data.data.total
        tab.pagination.pageSize = res.data.data.pageSize
        tab.pagination.current = res.data.data.page
      })
      .catch(err => {
        console.log(err)
      })
}
// 加载当前激活的 tab
const reloadCurrent = () => loadTab(keyMap[activeKey.value])
// tab 切换时加载对应 tab 第 1 页
const onTabChange = (key) => loadTab(keyMap[key])

// 加载订单状态常量（map/color）
const orderStatus = ref()
const loadOrderStatus = () => {
  getOrderStatus()
      .then(res => {
        orderStatus.value = res.data.data
      })
      .catch(err => {
        console.log(err)
      })
}
onMounted(() => {
  loadOrderStatus()
})
// 每次进入页面刷新当前 tab
onActivated(() => {
  reloadCurrent()
})

// 定义子组件 DetailOrders 的实例
const detailOrdersRef = ref(null)
const handleDetailOrdersOpen = (id) => {
  detailOrdersRef.value.detailOrdersVisibleOpen(id)
}

// ================ 删除订单 ===================
const handleDeleteOrder = (id) => {
  Modal.confirm({
    title: '删除订单',
    content: '请问确认删除吗？',
    onOk() {
      deleteOrder(id)
          .then(() => {
            message.success('删除成功')
            reloadCurrent()
          })
          .catch(err => {
            message.error('网络连接超时，请检查网络后再试')
            console.error(err)
          })
    }
  })
}
//====================== 批量删除 ==================
const handleBatchDeleteOrders = (ids) => {
  deleteOrders(ids)
      .then(() => {
        message.success('订单已删除')
        reloadCurrent()
      })
      .catch(err => {
        console.log(err)
        message.error('订单不存在')
      })
}
// =======================================

// ======================== 搜索订单 ========================
const searchKeyWord = ref('')
const onOrderSearch = (value) => {
  searchKeyWord.value = value || ''
  reloadCurrent()
}
// ======================== 日期筛选 ========================
const dateRangeRef = ref(null)
const dateFilter = ref({ dateField: 'created_at', startDate: '', endDate: '' })
const onDateFilterChange = (filter) => {
  dateFilter.value = filter
  reloadCurrent()
}
// 刷新：清空搜索/日期，重新加载当前 tab
const reloadOrders = () => {
  searchKeyWord.value = ''
  dateRangeRef.value?.reset()
  reloadCurrent()
}
</script>

<style scoped>
:deep(.ant-input-search-button) {
  width: 100px;
}

.range-center :deep(.ant-picker-input > input) {
  text-align: center;
}

:deep(.ant-picker-panel-container) {
  font-size: 12px;
}
</style>
