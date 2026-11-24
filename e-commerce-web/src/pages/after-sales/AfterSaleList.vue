<template>
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
    <a-form-item label="申请时间" style="font-size:16px;margin:0 40px;">
      <a-range-picker v-model:value="dateRange" :show-time="{ format: 'HH:mm' }" style="padding:5px;"
                      class="range-center" @change="onDateRangeChange"
                      :placeholder="['开始日期','结束日期']" />
    </a-form-item>
    <a-form-item>
      <a-button style="font-size:15px; height:32px; width:100px;"
                @click="reloadOrders">刷新
      </a-button>
    </a-form-item>
  </a-form>
  <a-tabs v-model:activeKey="activeKey" size="large">
    <a-tab-pane key="1" tab="仅退款" >
      <OnlyRefund />
    </a-tab-pane>
    <a-tab-pane key="2" tab="退货退款" >
      <ReturnRefund />
    </a-tab-pane>
    <a-tab-pane key="3" tab="换货" >
      <Exchange />
    </a-tab-pane>
  </a-tabs>

</template>

<script setup>
import { provide, ref } from 'vue';
import OnlyRefund from "@/pages/after-sales/only-refund/OnlyRefund.vue";
import ReturnRefund from "@/pages/after-sales/return_refund/ReturnRefund.vue";
import Exchange from "@/pages/after-sales/exchange/Exchange.vue";

const activeKey = ref('1');

// ======================== 共享过滤参数（供三个 Tab 注入使用） ========================
const filterParams = ref({})
provide('afterSaleFilter', filterParams)

// ======================== 搜索 ========================
const searchKeyWord = ref('')
const onOrderSearch = (value) => {
  filterParams.value = { ...filterParams.value, search: value || '' }
}

// ======================== 日期筛选 ========================
const dateRange = ref([])
const onDateRangeChange = (dates) => {
  if (dates && dates.length === 2) {
    filterParams.value = {
      ...filterParams.value,
      dateField: 'created_at',
      startDate: dates[0].format('YYYY-MM-DD HH:mm'),
      endDate: dates[1].format('YYYY-MM-DD HH:mm'),
    }
    console.log('change触发:', dates)
  } else {
    // 清空日期时移除 dateField / startDate / endDate
    const { dateField, startDate, endDate, ...rest } = filterParams.value
    filterParams.value = rest
  }
}

// 刷新：清空所有筛选条件
const reloadOrders = () => {
  filterParams.value = {}
  searchKeyWord.value = ''
  dateRange.value = []
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