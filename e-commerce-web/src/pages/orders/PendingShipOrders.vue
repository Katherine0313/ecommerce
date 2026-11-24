<template>
  <div class="order-container">
    <a-table class="title" :data-source="list" :pagination="pagination" @change="onPageChange"
             :row-selection="{selectedRowKeys,onChange:onOrderSelectChange}" row-key="id">
      <a-table-column title="编号" dataIndex="id"></a-table-column>
      <a-table-column title="订单编号" dataIndex="orderNo"></a-table-column>
      <a-table-column title="支付时间" dataIndex="payAt"></a-table-column>
      <a-table-column title="用户账号" dataIndex="userId"></a-table-column>
      <a-table-column title="订单金额" dataIndex="payAmount"></a-table-column>
      <a-table-column title="优惠金额">
        <template #default="{record}">
          <span v-if="record.discountAmount > 0" style="color:#FF6700">-¥{{ record.discountAmount }}</span>
          <span v-else>—</span>
        </template>
      </a-table-column>
      <a-table-column title="支付方式" dataIndex="payTypeDesc"></a-table-column>
      <a-table-column title="订单状态" dataIndex="orderStatus">
        <template #default="{record}">
          <a-tag :color="orderStatus.color[record.orderStatus] || 'default'">
            {{ orderStatus.map[record.orderStatus] || record.orderStatus }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column title="操作">
        <template #default="{record}">
          <div class="ops">
            <a-button type="primary" style="margin-right:10px;" @click="$emit('view',record.id)">查看订单
            </a-button>
            <a-button v-permission="'order:order:remove'" @click="$emit('delete',record.id)" size="small" type="link">删除
            </a-button>
          </div>
        </template>
      </a-table-column>
    </a-table>
    <a-button v-permission="'order:order:remove'" class="bat-delete-btn" @click="batchDelete">批量删除</a-button>
  </div>
</template>
<script setup>
import {ref} from 'vue'
import {cancelOrder} from '@/api/orders.js'
import {message, Modal} from 'ant-design-vue'

defineProps({
  list: {type: Array, default: () => []},
  orderStatus: {type: Object, default: () => ({ map: {}, color: {} })},
  pagination: {type: Object, default: () => ({})},
})

const emit = defineEmits(['view', 'delete', 'batchDelete', 'refresh', 'pageChange'])

// 分页变化时通知父组件加载对应页
const onPageChange = (pag) => {
  emit('pageChange', pag.current)
}

//====================== 复选框 ====================
const selectedRowKeys = ref([])
const onOrderSelectChange = (key) => {
  selectedRowKeys.value = key
}
const batchDelete = () => {
  emit('batchDelete', selectedRowKeys.value)
}

</script>


<style scoped>

.title {
  margin-top: 20px;
}

/*表头*/

/*行元素*/
.title :deep(.ant-table-tbody >tr > td) {
  font-size: 14px;
  text-align: center;
}

/* 操作列 - 防止按钮换行叠加 */
.title :deep(.ant-table-thead >tr >th:last-child) {
  padding-left: 80px;
  text-align: left;
}


.order-container {
  position: relative;
}

.bat-delete-btn {
  position: absolute;
  bottom: 15px;
}

/* 操作列按钮：每行最多三个，自动换行 */
.ops {
  display: grid;
  grid-template-columns: repeat(3, max-content);
  gap: 8px;
}
</style>
