<template>
  <a-modal v-model:visible="detailOrdersVisible" title="订单详情" width="min(900px, 92vw)">
    <a-card style="margin-bottom: 10px;padding:0 4px;">
      <div class="consignee" v-if="orderInfo">
        <EnvironmentOutlined style="font-size:40px;color:#FF4E2A" />
        <div class="info">
          <div>
            <div class="name">
              <span style="margin-right:15px;">{{ orderInfo.consignee }}</span><span>{{ maskPhone(orderInfo.phone) }}</span>
            </div>
            <div class="address">{{ orderInfo.region }} {{ orderInfo.detailAddress }}</div>
          </div>
          <div class="ops">
            <a-button @click="handleEditAddress">编辑</a-button>
            <a-button type="primary" @click="handleViewLogistics">查看物流</a-button>
          </div>
        </div>
      </div>
    </a-card>
    <a-card>
      <a-table :row-selection="{}" class="title" :data-source="detailOrders">
        <a-table-column title="编号" dataIndex="id"></a-table-column>
        <a-table-column title="产品信息" dataIndex="productName"></a-table-column>
        <a-table-column>
          <template #default="{record}">
            <img v-lazy="record.image" width="100" />
          </template>
        </a-table-column>
        <a-table-column title="单价" dataIndex="salesPrice"></a-table-column>
        <a-table-column title="数量" dataIndex="quantity"></a-table-column>
        <a-table-column title="单品小计" dataIndex="subTotal"></a-table-column>
      </a-table>
    </a-card>
  </a-modal>
  <Address ref="addressRef" @saved="onConsigneeSaved"/>
  <LogisticsDrawer ref="logisticsRef" />
</template>
<script setup>
import {ref} from 'vue'
import {getDetailOrders, updateOrder} from '@/api/orders.js'
import {getAddressList} from '@/api/address.js'
import {message} from 'ant-design-vue'
import {EnvironmentOutlined} from '@ant-design/icons-vue'
import {maskPhone} from '@/utils/mask.js'
import Address from "@/pages/goods/list/Address.vue";
import LogisticsDrawer from "./LogisticsDrawer.vue";
// 定义收货人区域的响应式变量
const orderInfo = ref(null)
// 定义数据源
const detailOrders = ref([])
// 定义弹窗的响应式变量
const detailOrdersVisible = ref(false)
// 定义打开订单详情弹窗的方法
const detailOrdersVisibleOpen = (id) => {
  detailOrdersVisible.value = true
  getDetailOrders(id)
      .then(res => {
        orderInfo.value = res.data.data
        detailOrders.value = res.data.data.items
      })
      .catch(err => {
        console.log(err)
        message.error('网络连接超时，请检查网络后再试')
      })
}
// 暴露该方法给父组件OrderList.vue
defineExpose({detailOrdersVisibleOpen})

// ============== 编辑地址 ================
const addressRef = ref(null)
const handleEditAddress = () => {
  const { id, ...addrData } = orderInfo.value
  addressRef.value.addressVisibleOpen(addrData)
}

// ============== 查看物流轨迹 ==============
// 轨迹是后端生成的假数据（未发货订单返回空），快递公司与单号是发货时写入的真实值
const logisticsRef = ref(null)
const handleViewLogistics = () => {
  if (orderInfo.value) {
    logisticsRef.value.open(orderInfo.value.id)
  }
}
const onConsigneeSaved = () => {
  getAddressList().then(res => {
    const addrs = res.data.data || []
    // 按 id 降序取最新保存的地址，避免默认地址排前面导致取错
    const latest = addrs.sort((a, b) => b.id - a.id)[0]
    if (!latest) return
    updateOrder(orderInfo.value.id, latest).then(() => {
      Object.assign(orderInfo.value, latest)
    })
  })
}
</script>
<style scoped>

/*表头*/

/*行元素*/
.title :deep(.ant-table-tbody >tr > td) {
  font-size: 14px;
  text-align: center;

}

/* 操作列 - 防止按钮换行叠加 */
.title :deep(.ant-table-tbody >tr >td:last-child) {
  white-space: nowrap;
}

/*收货人*/
.consignee {
  max-width: 900px;
  display: flex;
  padding: 10px;
  align-items: center;
  border-radius: 10px;
  line-height: 30px;
  font-size: 16px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.2);
  gap: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.consignee .info {
  width: 90%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 操作按钮组：编辑 + 查看物流 */
.consignee .ops {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.consignee:hover {
  border: 2px solid #FF4E2A;
}

/* 手机：收货人信息纵向排列，避免姓名/电话/地址挤成一行 */
@media (max-width: 767px) {
  .consignee {
    max-width: 100%;
    padding: 8px;
  }
  .consignee .info {
    width: 100%;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>