<template>
  <div>
    <!-- 工具栏：多条件筛选 + 导出 + 新增虚拟评价 -->
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="商品名称" allow-clear style="width: 160px"
                    @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-input v-model:value="query.orderNo" placeholder="订单号" allow-clear style="width: 160px"
                    @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.rating" placeholder="星级" allow-clear style="width: 100px"
                    :options="ratingOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.isVirtual" placeholder="评价类型" allow-clear style="width: 120px"
                    :options="typeOptions" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.visible" placeholder="显示状态" allow-clear style="width: 110px"
                    :options="visibleOptions" />
        </a-form-item>
        <a-form-item>
          <a-range-picker v-model:value="dateRange" :show-time="{ format: 'HH:mm' }" style="padding: 5px"
                          :placeholder="['开始时间', '结束时间']" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
          <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
        </a-form-item>
      </a-form>
      <div class="head-actions">
        <a-button v-permission="'product:review:export'" :loading="exporting" @click="handleExport">
          <DownloadOutlined />
          导出
        </a-button>
        <a-button v-permission="'product:review:add'" type="primary" @click="openCreate">
          <PlusOutlined />
          新增虚拟评价
        </a-button>
      </div>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
              @change="handleTableChange">
      <template #bodyCell="{ column, record, index }"><!-- 👉 序号渲染，跨页连续 -->
        <template v-if="column.key === 'index'">
          {{ (pagination.current - 1) * pagination.pageSize + index + 1 }}
        </template>
        <template v-if="column.key === 'product'">
          <div class="cell-product">
            <a-image :src="imgUrl(record.productImage)" :width="44" :height="44" fit="cover"
                     :fallback="PLACEHOLDER" />
            <div class="cell-product-text">
              <div class="cell-product-name" :title="record.productName">{{ record.productName || '—' }}</div>
              <div class="cell-product-id">ID {{ record.productId }}</div>
            </div>
          </div>
        </template>
        <template v-else-if="column.key === 'rating'">
          <a-rate :value="record.rating" disabled allow-half />
        </template>
        <template v-else-if="column.key === 'content'">
          <div class="cell-content">
            <a-tooltip :title="record.content">
              <div class="cell-content-text">{{ record.content || '—' }}</div>
            </a-tooltip>
          </div>
        </template>
        <template v-else-if="column.key === 'media'">
          <div class="cell-media">
            <a-image v-for="(item, idx) in parseMedia(record.images).filter((m) => m.type !== 'video')"
                     :key="idx" :src="imgUrl(item.url)"
                     :width="44" :height="44" fit="cover" />
            <a-tag v-if="hasVideo(record.images)" color="blue">含视频</a-tag>
            <span v-if="!parseMedia(record.images).length" class="muted">—</span>
          </div>
        </template>
        <template v-else-if="column.key === 'type'">
          <a-tag :color="record.isVirtual === 1 ? 'purple' : 'blue'">
            {{ record.isVirtual === 1 ? '虚拟' : '真实' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'user'">
          <div v-if="record.userName">
            <div>{{ record.userName }}</div>
            <div class="muted">{{ record.userId }}</div>
          </div>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'orderNo'">
          <!-- 虚拟评价不挂真实订单，orderNo 为 null，显示占位符 -->
          <span v-if="record.orderNo">{{ record.orderNo }}</span>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.statusDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'reply'">
          <div v-if="record.reply" class="cell-reply" :title="record.reply">{{ record.reply }}</div>
          <span v-else class="muted">—</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="action-buttons">
            <a-button v-permission="'product:review:reply'" size="small" @click="openReply(record)">回复</a-button>
            <a-button v-permission="'product:review:remove'" size="small" danger @click="remove(record)">
              删除
            </a-button>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 回复弹窗：一条评价只保留一条回复，再次回复覆盖 -->
    <a-modal v-model:visible="replyVisible" title="回复评价" ok-text="提交回复" @ok="submitReply">
      <a-form layout="vertical">
        <a-form-item label="评价内容">
          <div class="reply-quote">{{ replyTarget?.content }}</div>
        </a-form-item>
        <a-form-item label="回复内容（再次回复会覆盖之前的回复）">
          <a-textarea v-model:value="replyText" :rows="4" :maxlength="1000" show-count
                      placeholder="请输入回复内容" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新增 / 编辑虚拟评价弹窗 -->
    <ReviewEditModal v-model:visible="editVisible" :review-id="editId" @success="loadData" />
  </div>
</template>

<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DownloadOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import {
  deleteReview,
  exportReviews,
  getReviewList,
  replyReview,
} from '@/api/review.js'
import ReviewEditModal from '@/pages/orders/review/ReviewEditModal.vue'

const list = ref([])
// 图片占位图（商品图缺失时不让表格出现裂图）
const PLACEHOLDER =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44"><rect width="100%" height="100%" fill="#f0f0f0"/></svg>'
  )
const query = ref({ keyword: '', orderNo: '', rating: undefined, isVirtual: undefined, visible: undefined })
const dateRange = ref([])

const pagination = ref({
  current: 1,
  pageSize: 13,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

// 回复弹窗
const replyVisible = ref(false)
const replyTarget = ref(null)
const replyText = ref('')
// 新增 / 编辑弹窗
const editVisible = ref(false)
const editId = ref(null)
// 导出中
const exporting = ref(false)

const ratingOptions = [1, 2, 3, 4, 5].map((n) => ({ value: n, label: `${n} 星` }))
const typeOptions = [
  { value: 0, label: '买家真实' },
  { value: 1, label: '后台虚拟' },
]
const visibleOptions = [
  { value: 1, label: '显示' },
  { value: 0, label: '隐藏' },
]

// 列不写死宽度、也不写 :scroll —— 操作列保留 fixed:'right' 并给 120px，
// 保证「回复 / 删除」两个按钮有足够宽度；窄屏放不下时按钮自动换行，
// 不会再出现文字挤出按钮的情况。其余列由 antd 走 table-layout: fixed 自适应，
// 24 位订单号不会把列撑爆，表格宽度正好等于内容区宽。
// 注意：没有 bodyCell 分支的列（如订单号）必须写 dataIndex，否则单元格是空的
const columns = [
  { title: '序号', key: 'index'},
  { title: '商品', key: 'product'},
  { title: '星级', key: 'rating'},
  { title: '评价内容', key: 'content'},
  { title: '媒体', key: 'media' },
  { title: '类型', key: 'type'},
  { title: '评价人', key: 'user' },
  { title: '订单号', key: 'orderNo', dataIndex: 'orderNo'},
  { title: '状态', key: 'status' },
  { title: '商家回复', key: 'reply' },
  { title: '评价时间', dataIndex: 'createdAt', key: 'createdAt'},
  { title: '操作', key: 'action', fixed: 'right', width: 150 },
]

/** 拼查询参数：空值不传，后端据此不筛 */
function buildParams() {
  const params = {
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
  }
  if (query.value.keyword) params.keyword = query.value.keyword
  if (query.value.orderNo) params.orderNo = query.value.orderNo
  if (query.value.rating !== undefined && query.value.rating !== null) params.rating = query.value.rating
  if (query.value.isVirtual !== undefined && query.value.isVirtual !== null) params.isVirtual = query.value.isVirtual
  if (query.value.visible !== undefined && query.value.visible !== null) params.visible = query.value.visible
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0].format('YYYY-MM-DD HH:mm')
    params.endDate = dateRange.value[1].format('YYYY-MM-DD HH:mm')
  }
  return params
}

function loadData() {
  getReviewList(buildParams()).then((res) => {
    list.value = res.data.data.list || []
    pagination.value.total = res.data.data.total || 0
  })
}

function handleSearch() {
  pagination.value.current = 1
  loadData()
}

function handleReset() {
  query.value = { keyword: '', orderNo: '', rating: undefined, isVirtual: undefined, visible: undefined }
  dateRange.value = []
  handleSearch()
}

function handleTableChange(pager) {
  pagination.value.current = pager.current
  pagination.value.pageSize = pager.pageSize
  loadData()
}

// 新增虚拟评价
function openCreate() {
  editId.value = null
  editVisible.value = true
}

// 回复
function openReply(record) {
  replyTarget.value = record
  replyText.value = record.reply || ''
  replyVisible.value = true
}

function submitReply() {
  if (!replyText.value.trim()) {
    message.error('请输入回复内容')
    return
  }
  replyReview(replyTarget.value.id, replyText.value.trim()).then(() => {
    message.success('回复成功')
    replyVisible.value = false
    loadData()
  })
}

// 删除（逻辑删除）
function remove(record) {
  Modal.confirm({
    title: '确认删除这条评价？',
    content: '删除后前台将不再展示（数据库为逻辑删除，可回溯）。',
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () =>
      deleteReview(record.id).then(() => {
        message.success('删除成功')
        loadData()
      }),
  })
}

// 导出 Excel：与列表用同一套筛选条件
async function handleExport() {
  if (exporting.value) return
  exporting.value = true
  try {
    const params = buildParams()
    delete params.page
    delete params.pageSize
    const res = await exportReviews(params)
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `商品评价列表_${dayjs().format('YYYYMMDD')}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (err) {
    console.error('导出评价失败', err)
    message.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

/** 解析媒体 JSON：[{"url":"...","type":"image"}] */
function parseMedia(json) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr.filter((m) => m && m.url) : []
  } catch (e) {
    return []
  }
}

/** 是否含视频（视频不进图片九宫格，单独标个标签） */
function hasVideo(json) {
  return parseMedia(json).some((m) => m.type === 'video')
}

/** 相对路径补上网关地址（生产同源，走 VITE_API_BASE_URL 空串即可） */
function imgUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : (import.meta.env.VITE_API_BASE_URL || '') + path
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

.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.cell-product {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cell-product-text {
  min-width: 0;
}

.cell-product-name {
  font-size: 13px;
  line-height: 1.4;
  max-height: 36px;
  overflow: hidden;
  text-align: left;
}

.cell-product-id {
  font-size: 12px;
  color: #999;
  text-align: left;
}

.cell-content-text {
  font-size: 13px;
  color: #555;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cell-media {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.cell-reply {
  font-size: 13px;
  color: #555;
  max-height: 40px;
  overflow: hidden;
}

.muted {
  color: #bbb;
}

.reply-quote {
  padding: 8px 12px;
  background: #fafafa;
  border-left: 3px solid #d9d9d9;
  color: #666;
  font-size: 13px;
  border-radius: 4px;
}

/* 操作列：回复、删除并排；窄屏放不下时自动换行，不挤压按钮文字 */
.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.action-buttons :deep(.ant-btn) {
  padding: 0 8px;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .head {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
