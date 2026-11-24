<template>
  <div>
    <!-- 工具栏：活动名称 / 状态筛选 + 新建 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="请输入活动名称" allow-clear @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.status" style="width: 140px" placeholder="活动状态" allow-clear
                    :options="statusOptions" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
        </a-form-item>
      </a-form>
      <a-button v-permission="'business:promotion:add'" type="primary" @click="openCreate">新建活动</a-button>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'index'">
          {{ (pagination.current - 1) * pagination.pageSize + index + 1 }}
        </template>
        <template v-else-if="column.key === 'time'">
          {{ record.startTime }} ~ {{ record.endTime }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.statusDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <!-- 只留两个按钮：详情 / 编辑；提前结束、删除都在编辑弹窗里 -->
          <div class="action-buttons">
            <a-button size="small" @click="openDetail(record)">详情</a-button>
            <a-button v-permission="'business:promotion:edit'"
                      size="small" type="primary" @click="openEdit(record)">编辑</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 活动详情弹窗 -->
    <PromotionDetailModal v-model:visible="detailVisible" :activity-id="detailId" />

    <!-- 新建 / 编辑活动弹窗（提前结束、删除都在里面） -->
    <PromotionEditModal v-model:visible="editVisible" :activity-id="editId" @success="loadData" />
  </div>
</template>

<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { getPromotionList } from '@/api/promotion.js'
import PromotionDetailModal from '@/pages/business/promotion/PromotionDetailModal.vue'
import PromotionEditModal from '@/pages/business/promotion/PromotionEditModal.vue'

const list = ref([])
const query = ref({ keyword: '', status: undefined })

// 详情弹窗
const detailVisible = ref(false)
const detailId = ref(null)
// 新建 / 编辑弹窗
const editVisible = ref(false)
const editId = ref(null)

const statusOptions = [
  { value: 'PENDING', label: '未开始' },
  { value: 'ACTIVE', label: '进行中' },
  { value: 'ENDED', label: '已结束' },
  { value: 'CANCELLED', label: '已失效' },
]

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: '序号', key: 'index' },
  { title: '活动名称', dataIndex: 'name', key: 'name' },
  { title: '类型', dataIndex: 'typeDesc', key: 'typeDesc' },
  { title: '玩法', dataIndex: 'ruleTypeDesc', key: 'ruleTypeDesc' },
  { title: '活动时间', key: 'time' },
  { title: '商品数', dataIndex: 'productCount', key: 'productCount' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 200 },
]

function loadData() {
  getPromotionList({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    status: query.value.status || undefined,
  }).then((res) => {
    list.value = res.data.data.list || []
    pagination.value.total = res.data.data.total || 0
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

// 新建：活动 ID 传空，弹窗内部走空表单
function openCreate() {
  editId.value = null
  editVisible.value = true
}

// 编辑：传活动 ID，弹窗内部拉详情回填
function openEdit(record) {
  editId.value = record.id
  editVisible.value = true
}

// 详情
function openDetail(record) {
  detailId.value = record.id
  detailVisible.value = true
}

function statusColor(status) {
  return { PENDING: 'blue', ACTIVE: 'green', ENDED: 'default', CANCELLED: 'red' }[status] || 'default'
}

onMounted(() => {
  loadData()
})
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

/* 操作列：详情 / 编辑 两个按钮，等宽对齐 */
.action-buttons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.action-buttons :deep(.ant-btn) {
  width: 100%;
}

.title :deep(.ant-table-tbody > tr > td) {
  font-size: 14px;
  text-align: center;
}

@media (max-width: 767px) {
  .head {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
