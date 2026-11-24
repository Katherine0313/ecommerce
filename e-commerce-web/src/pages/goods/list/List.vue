<template>
  <div>
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input placeholder="请输入商品名称" v-model:value="search" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
  <!--点击编辑按钮，弹出对话框，这是对话框（子组件）-->
  <EditModal ref="editModalRef" @refresh="loadData"/>
  <!--点击行，预览弹窗（iframe 加载 C 端真实商品详情页）-->
  <Mall v-model:visible="mallOpen" :productId="mallProductId" />
  <!--:dataSource="data"指的是数据来源于后端的data字段，如果没有这个，表格渲染不出来
  customRow 点击行预览-->
  <a-table :dataSource="data" :columns="columns"  class="title" :customRow="customRow"
           :pagination="pagination" row-key="id" @change="handleTableChange">
    <!-- 操作列的插槽-->
    <template #bodyCell="{column, record}">
      <template v-if="column.key === 'image'">
        <!-- @click.stop 阻止事件冒泡，现在点击图片是 <a-image> 自带的预览放大效果，点击行的其他位置才跳 Mall.vue。-->
        <img v-lazy="record.image" width="60" @click.stop/>
      </template>
      <template v-if="column.key === 'category'">
        {{ categoryMap[record.categoryId] || record.categoryId }}
      </template>
      <template v-if="column.key === 'sku'">
        <a-tag v-if="record.skuType === 1" color="blue">多规格</a-tag>
        <span v-if="record.skuType === 1">{{ skuNames(record) }}</span>
        <span v-else>单规格</span>
      </template>
      <template v-if="column.key === 'salesPrice'">
        ¥{{ formatPrice(record.salesPrice) }}<span v-if="record.skuType === 1"> 起</span>
      </template>
      <template v-if="column.key === 'stock'">
        <span :style="record.stock === 0 ? 'color:#ff4d4f;font-weight:600' : ''">{{ record.stock ?? 0 }}</span>
      </template>
      <template v-if="column.key === 'listingStatus'">
        <a-switch :checked="record.listingStatus === 1" @change="(checked) => listingStatusChange(record, checked)" />
      </template>
      <!-- 格式化时间戳：ISO → YYYY-MM-DD HH:mm:ss -->
      <template v-if="column.key === 'createdAt'">
        {{ record.createdAt ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}
      </template>
      <template v-if="column.key === 'updatedAt'">
        {{ record.updatedAt ? dayjs(record.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}
      </template>
      <template v-if="column.key === 'action'">
        <a-button v-permission="'product:product:edit'"  @click="handleEdit(record)" type="primary" style="margin-right:5px">编辑
        </a-button>
        <a-button v-permission="'product:product:remove'" @click="handleDelete(record)" type="primary">删除</a-button>
      </template>
    </template>
  </a-table>
</template>
<script setup>
import {onMounted, onActivated, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {getGoodsList, getCategoryTree, deleteGoods, updateGoods, getGoodsDetail} from '@/api/products.js'
import {SearchOutlined} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
// 导入消息组件
import {message, Modal} from 'ant-design-vue'
import EditModal from './EditModal.vue'
import Mall from './Mall.vue'
const data = ref([])
// 定义输入框的search
const search = ref('')
// 分页状态（后端真分页：每次只请求当前页，不再全量拉取）
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})
// 加载商品数据（带分页参数，只查当前页）
const loadData = () => {
  getGoodsList({
    search: search.value,
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
  })
      .then(res => {
        console.log(res)
        data.value = res.data.data.list
        pagination.value.total = res.data.data.total
      })
      .catch(err => {
        message.error('网络连接超时，请检查网络后再试')
        console.log('商品列表加载失败', err)
      })
}
// 分页/每页条数变化时重新请求当前页
const handleTableChange = (pag) => {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  loadData()
}
// id → 名称的映射，例如 101 → "苹果手机"
const categoryMap = ref({})
// 从树形数据中提取所有节点，建立 id → label 映射
const buildCategoryMap = (tree) => {
  const map = {}
  const walk = (nodes) => {
    nodes.forEach(node => {
      map[node.id] = node.name
      if (node.children) walk(node.children)
    })
  }
  walk(tree)
  return map
}

// 分类映射是静态字典，只需加载一次，放 onMounted
onMounted(() => {
  getCategoryTree().then(res => {
    categoryMap.value = buildCategoryMap(res.data.data)
  })
})
// 商品列表每次进入/切回页面都要刷新，放 onActivated（首次进入也会触发）
onActivated(() => {
  loadData()
})

const columns = [
  {
    title: "商品id",
    dataIndex: "id",
    key: "id"
  },
  {
    title: "商品图片",
    dataIndex: "image",
    key: "image"
  },
  {
    title: "商品名称",
    dataIndex: "name",
    key: "name"
  },
  {
    title: "商品品牌",
    dataIndex: "brand",
    key: "brand"
  },
  {
    title: "商品分类",
    dataIndex: "categoryId",
    key: "category"
  },
  {
    title: "商品规格",
    key: "sku"
  },
  {
    title: "商品价格",
    dataIndex: "salesPrice",
    key: "salesPrice"
  },
  {
    title: "商品库存",
    dataIndex: "stock",
    key: "stock"
  },
  {
    title: "上下架状态",
    dataIndex: "listingStatus",
    key: "listingStatus",
  },
  {
    title: "创建时间",
    dataIndex: "createdAt",
    key: "createdAt"
  },
  {
    title: "更新时间",
    dataIndex: "updatedAt",
    key: "updatedAt"
  },
  {
    title: "操作",
    key: "action"
  },
]
// 多规格商品显示「颜色 / 内存」，单规格显示「单规格」
const skuNames = (record) => {
  const names = (record.skuSpecs || []).map(s => s.name)
  return names.length ? names.join(' / ') : '多规格'
}
const formatPrice = (value) => {
  return value == null ? '-' : Number(value).toFixed(2)
}
// 搜索事件：重置回第一页再查，避免带着旧页码查空页
const handleSearch = () => {
  pagination.value.current = 1
  loadData()
}
// ====================== 预览弹窗 =====================
const mallOpen = ref(false)
const mallProductId = ref('')

const openMall = (record) => {
  mallProductId.value = record.id
  mallOpen.value = true
}

const customRow = (record) => {
  return {
    style: { cursor: 'pointer' },
    onClick: (event) => {
      // 点击操作列的按钮时不触发预览
      if (event.target.closest('button') || event.target.closest('.ant-btn') || event.target.closest('.ant-switch')) {
        return
      }
      openMall(record)
    }
  }
}

// ====================== 编辑操作 =====================
const editModalRef = ref(null)
const handleEdit = (record) => {
  // 用实例去调用子组件暴露出来的打开弹窗的方法
  editModalRef.value.open(record)
}
// 支持外部页面带 ?editId= 跳转直接打开编辑弹窗（如 AI 文案助手「回填到商品」、AI 对话商品卡片「查看商品」）
const route = useRoute()
const router = useRouter()
const openEditByQuery = (editId) => {
  const id = Number(editId)
  if (!id) return
  getGoodsDetail(id)
      .then(res => {
        const detail = res.data?.data
        if (detail) editModalRef.value?.open(detail)
      })
      .catch(() => {})
      .finally(() => {
        // 清掉 query，避免刷新/回退时重复弹出
        router.replace({ query: { ...route.query, editId: undefined } })
      })
}
watch(() => route.query.editId, (editId) => {
  if (editId) openEditByQuery(editId)
})
onMounted(() => {
  if (route.query.editId) openEditByQuery(route.query.editId)
})
// ====================== 删除操作 =====================

const handleDelete = (record) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除商品「 ${record.name} 」吗？`,
    onOk() {
      deleteGoods(record.id)
          .then(() => {
            message.success('删除成功')
            loadData()
          })
          .catch(err => {
            message.error('删除失败')
            console.log('商品删除接口请求失败，错误详情：', err)
          })
    }
  })
}
// 开关状态的回调
const listingStatusChange = (record, checked) => {
  const newStatus = checked ? 1 : 0
  record.listingStatus = newStatus
  updateGoods(record.id, { listingStatus: newStatus })
    .then(() => message.success(checked ? '已上架' : '已下架'))
    .catch(() => {
      record.listingStatus = checked ? 0 : 1
      message.error('状态更新失败')
    })
}
</script>
<style scoped>
.head{
  margin-bottom:10px;
}
/*表头*/
.title{

  border-radius: 15px;
  /* 不能再用 overflow:hidden：表格需要横向滚动，裁剪会挡住滚动区 */
  overflow: visible;
}
.title :deep(.ant-table-container){
  border-radius: 15px;
  overflow: hidden;
}
/*行元素*/
.title :deep(.ant-table-tbody >tr > td){
  font-size: 14px;
  text-align:center;
}
/* 操作列 - 防止按钮换行叠加 */
.title :deep(.ant-table-tbody >tr >td:last-child) {
  white-space: nowrap;
}

.body-content :deep(img) {
  max-width: 100px;
  max-height: 100px;
  object-fit: cover;
}
</style>
<style>
/* 图片预览不遮挡标题，限制范围 */
.ant-image-preview-img {
  max-width: 700px !important;
  max-height: calc(100vh - 120px) !important;
  object-fit: contain;
}
/* 手机：700px 会超出屏幕，按视口收窄 */
@media (max-width: 767px) {
  .ant-image-preview-img {
    max-width: calc(100vw - 24px) !important;
  }
}
</style>