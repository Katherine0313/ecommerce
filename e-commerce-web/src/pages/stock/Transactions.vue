<template>
  <div>
    <!-- 过滤区 -->
    <a-card :bordered="false" class="filter-card">
      <a-row :gutter="16" align="middle">
        <a-col :xs="24" :sm="12" :md="6">
          <a-input-search
              placeholder="SKU 名称 / 编码"
              v-model:value="filters.search"
              @search="onSearch"
              allow-clear
          />
        </a-col>
        <a-col :xs="24" :sm="12" :md="4">
          <a-select placeholder="类型" v-model:value="filters.type" allow-clear style="width: 100%">
            <a-select-option value="IN">入库</a-select-option>
            <a-select-option value="OUT">出库</a-select-option>
            <a-select-option value="ADJUST">调整</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="4">
          <a-select placeholder="原因" v-model:value="filters.reason" allow-clear style="width: 100%">
            <a-select-option value="采购入库">采购入库</a-select-option>
            <a-select-option value="退货归还">退货归还</a-select-option>
            <a-select-option value="手工补录">手工补录</a-select-option>
            <a-select-option value="销售出库">销售出库</a-select-option>
            <a-select-option value="手工出库">手工出库</a-select-option>
            <a-select-option value="盘亏出库">盘亏出库</a-select-option>
            <a-select-option value="盘点调整">盘点调整</a-select-option>
            <a-select-option value="单据取消冲销">单据取消冲销</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <a-range-picker v-model:value="filters.range" style="width: 100%" show-time format="YYYY-MM-DD HH:mm" />
        </a-col>
        <a-col :xs="24" :sm="12" :md="4">
          <a-button type="primary" @click="onSearch">查询</a-button>
          <a-button style="margin-left: 8px" @click="onReset">重置</a-button>
        </a-col>
      </a-row>
    </a-card>

    <!-- 表格 -->
    <a-table
        :dataSource="list"
        :loading="loading"
        :pagination="pagination"
        :rowKey="(r) => r.id"
        @change="onTableChange"
        bordered
        style="margin-top: 16px"
    >
      <a-table-column title="SKU编码" dataIndex="skuCode" />
      <a-table-column title="商品名" dataIndex="skuName" />
      <a-table-column title="规格">
        <template #default="{ record }">{{ record.specText || '—' }}</template>
      </a-table-column>
      <a-table-column title="类型" align="center">
        <template #default="{ record }">
          <a-tag :color="typeColor(record.type)">{{ typeText(record.type) }}</a-tag>
        </template>
      </a-table-column>
      <a-table-column title="数量" >
        <template #default="{ record }">
          <span :style="{ color: record.type === 'IN' ? '#3f8600' : '#cf1322' }">
            {{ record.type === 'IN' ? '+' : '-' }}{{ record.qty }}
          </span>
        </template>
      </a-table-column>
      <a-table-column title="结存快照" dataIndex="balanceAfter"  />
      <a-table-column title="原因">
        <template #default="{ record }">{{ reasonText(record.reason) }}</template>
      </a-table-column>
      <a-table-column title="关联单据">
        <template #default="{ record }">
          <span>{{ record.orderNo || '—' }}</span>
        </template>
      </a-table-column>
      <a-table-column title="操作人" dataIndex="operatorName" />
      <a-table-column title="时间" dataIndex="createTime" />
    </a-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getTransactions } from '@/api/stock.js'

const loading = ref(false)
const list = ref([])

const filters = reactive({
  search: '',
  type: undefined,
  reason: undefined,
  range: null,
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (t) => `共 ${t} 条`,
})

const typeText = (t) => ({ IN: '入库', OUT: '出库', ADJUST: '调整' }[t] || t)
const typeColor = (t) => ({ IN: 'green', OUT: 'orange', ADJUST: 'blue' }[t] || 'default')
const reasonText = (r) => r || '—'

const loadList = () => {
  loading.value = true
  const params = {
    search: filters.search || undefined,
    type: filters.type,
    reason: filters.reason,
    startTime: filters.range?.[0]?.format?.('YYYY-MM-DD HH:mm:ss'),
    endTime: filters.range?.[1]?.format?.('YYYY-MM-DD HH:mm:ss'),
    page: pagination.current,
    size: pagination.pageSize,
  }
  getTransactions(params)
      .then(res => {
        const data = res.data?.data || {}
        list.value = data.records || data.list || []
        pagination.total = data.total || 0
      })
      .catch(() => { list.value = []; pagination.total = 0 })
      .finally(() => { loading.value = false })
}

const onSearch = () => { pagination.current = 1; loadList() }
const onReset = () => {
  filters.search = ''
  filters.type = undefined
  filters.reason = undefined
  filters.range = null
  pagination.current = 1
  loadList()
}
const onTableChange = (pag) => {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadList()
}

onMounted(loadList)
</script>

<style scoped>
.filter-card { margin-bottom: 0; }
</style>
