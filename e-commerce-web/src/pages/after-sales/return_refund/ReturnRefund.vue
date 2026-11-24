<template>
  <a-table class="title":dataSource="ReturnRefundList">
    <a-table-column title="编号" dataIndex="id"></a-table-column>
    <a-table-column title="订单编号" dataIndex="orderNo"></a-table-column>
    <a-table-column title="创建时间" dataIndex="createdAt"></a-table-column>
    <a-table-column title="订单状态" dataIndex="orderStatus">
      <template #default="{record}">
        <a-tag :color="orderStatus.color[record.orderStatus] || 'default'">
          {{ orderStatus.map[record.orderStatus] || record.orderStatus }}
        </a-tag>
      </template>
    </a-table-column>
    <a-table-column title="售后状态" dataIndex="status">
      <template #default="{record}">
        <a-tag :color="afterSaleStatus.color[record.status] || 'default'">
          {{ afterSaleStatus.map[record.status] || record.status }}
        </a-tag>
      </template>
    </a-table-column>
    <a-table-column title="售后原因" dataIndex="reason">
      <template #default="{record}">
        {{ afterSaleStatus.reasonMap[record.reason] || record.reason }}
      </template>
    </a-table-column>
    <a-table-column title="补充说明" dataIndex="remark"></a-table-column>
    <a-table-column title="操作">
      <template #default="{record}">
        <a-button v-permission="'aftersale:aftersale:audit'" type="primary" style="margin-right:15px;"
                  @click="handleReturnRefundReview(record)">售后审核</a-button>
        <a-button @click="handleReturnRefundDetail(record)">售后详情</a-button>
        <a-button v-permission="'aftersale:aftersale:remove'" type="link" @click="handleDelete(record)">删除</a-button>
      </template>
    </a-table-column>
  </a-table>
  <!--售后审核组件，商家的-->
  <ReturnRefundReview ref="returnRefundReviewRef" :afterSaleStatus="afterSaleStatus" @refresh="loadData"/>
  <!--售后详情组件，用户的-->
  <ReturnRefundDetail ref="returnRefundDetailRef" :afterSaleStatus="afterSaleStatus"/>
</template>

<script setup>
import { inject, onMounted, ref, computed, watch } from 'vue'
import { deleteAfterSale,getAfterSaleStatus,getAfterSales } from '@/api/afterSale.js'
import {getOrderStatus} from '@/api/orders.js'
import { message, Modal } from 'ant-design-vue'
import ReturnRefundDetail from './ReturnRefundDetail.vue'
import ReturnRefundReview from './ReturnRefundReview.vue'
// ================ 获取售后列表 ===================
const afterSaleList = ref([])
const filterParams = inject('afterSaleFilter', ref({}))
const loadData = (override = {}) => {
  getAfterSales({ ...filterParams.value, ...override })
      .then((res) => {
        afterSaleList.value = res.data.data
      })
      .catch((err) => {
        console.log(err)
      })
}
watch(filterParams, () => loadData(), { deep: true })
// =============== 过滤订单 ==================
const ReturnRefundList = computed(() =>
    afterSaleList.value.filter(item => item.afterSaleType == 'return_refund')
)
// =============== 获取订单状态 =================
const orderStatus = ref({ map: {}, color: {} })
const loadOrderStatus = () => {
  getOrderStatus()
      .then(res => {
        orderStatus.value = res.data.data
      })
      .catch((err) => {
        console.log(err)
      })
}
// ================= 获取售后状态 ==================
const afterSaleStatus = ref({map:{}, color:{}, reasonMap:{}})
const loadAfterSaleStatus = () => {
  getAfterSaleStatus()
      .then(res =>{
        afterSaleStatus.value = res.data.data
      })
      .catch(err => {
        console.log(err)
      })
}
onMounted(() => {
  loadOrderStatus()
  loadAfterSaleStatus()
  loadData()
})

// 删除售后（软删除）
const handleDelete = (record) => {
  Modal.confirm({
    title: '删除售后记录',
    content: `请问确认订单 ${record.orderNo} 吗？`,
    onOk() {
      deleteAfterSale(record.id)
          .then(() => {
            message.success('删除成功')
            loadData()
          })
          .catch((err) => {
            message.error(err.response?.data?.message || '删除失败')
          })
    }
  })
}
// ================== 售后审核 =================
const returnRefundReviewRef = ref(null)
const handleReturnRefundReview = (record) => {
  returnRefundReviewRef.value.returnRefundReviewOpen(record)
}
// ================== 售后详情 =================
const returnRefundDetailRef = ref(null)
const handleReturnRefundDetail = (record) => {
  returnRefundDetailRef.value.returnRefundDetailOpen(record)
}
// =============== 管理员身份 ===================
</script>

<style scoped>
/*表头*/

/*行元素*/
.title :deep(.ant-table-tbody >tr > td) {
  font-size: 14px;
  text-align: center;
}
</style>
