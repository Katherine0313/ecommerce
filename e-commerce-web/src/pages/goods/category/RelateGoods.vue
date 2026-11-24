
<template>
  <a-modal title="关联产品" v-model:visible="open" centered :width="900" wrap-class-name="drag-modal-relate">
    <div class="add-goods-btn">
      <!--通过实例打点调用子组件的方法，打开对话框-->
      <a-button type="primary" @click="addGoodsRef.openAddGoodsModal(currentCategory.id)">添加商品</a-button>
    </div>
    <a-table :row-selection="{ selectedRowKeys, onChange: onSelectChange }" :row-key="'id'"
             class="goods-modal-table" :data-source="relateGoodsList">
      <a-table-column title="商品信息">
        <template #default="{record}">
          <img v-lazy="record.image" />
        </template>
      </a-table-column>
      <a-table-column dataIndex="name"></a-table-column>
      <a-table-column dataIndex="subTitle"></a-table-column>
      <a-table-column title="操作">
        <template #default="{record}">
          <a-button type="link" @click="removeRelateGoods(record)">移除</a-button>
        </template>
      </a-table-column>
    </a-table>
    <template #footer>
      <div class="footer">
        <a-button @click="refresh">刷新<ReloadOutlined /></a-button>
        <a-button type="primary" @click="batchRemoveGoods" :disabled="selectedRows.length === 0">批量解绑</a-button>
      </div>
    </template>
  </a-modal>
  <!--监听子组件发射过来的选中的商品-->
  <AddGoodsModal ref="addGoodsRef" @confirm="handleAddGoodsConfirm" />
</template>
<script setup>
import {ref} from 'vue'
import { useDragModal } from '@/composables/useDragModal'
useDragModal('drag-modal-relate')
import AddGoodsModal from "@/pages/goods/category/AddGoodsModal.vue";
import {getGoodsList, updateGoods} from '@/api/products.js'
import{message} from 'ant-design-vue'
import {ReloadOutlined} from '@ant-design/icons-vue'
const open = ref(false)
const addGoodsRef = ref(null)
const relateGoodsList = ref([])
const currentCategory = ref(null)

// 加载当前分类已关联的产品
const loadRelateGoods = () => {
  getGoodsList({categoryId: currentCategory.value.id, pageSize: 1000})
      .then(res => {
        relateGoodsList.value = res.data.data.list
      })
}

// 打开弹窗，接收 Category.vue 传来的分类记录
const relateGoodsModal = (record) => {
  currentCategory.value = record
  open.value = true
  loadRelateGoods()
}

// 子组件绑定成功后，刷新关联列表
const handleAddGoodsConfirm = () => {
  loadRelateGoods()
  emit('refresh')
}

const emit = defineEmits(['refresh'])

defineExpose({relateGoodsModal})
// ==================== 复选框 ====================
const selectedRowKeys = ref([])
const selectedRows = ref([])
const onSelectChange = (keys, rows) => {
  selectedRowKeys.value = keys
  selectedRows.value = rows
}
// ==================== 批量移除 ====================
const batchRemoveGoods = () => {
  const promises = selectedRows.value.map(product =>
    updateGoods(product.id, { categoryId: 0 })
  )
  Promise.all(promises)
    .then(() => {
      message.success(`已移除 ${selectedRows.value.length} 个产品`)
      selectedRowKeys.value = []
      selectedRows.value = []
      loadRelateGoods()
      emit('refresh')
    })
    .catch(err => {
      message.error('批量删除失败')
      console.error(err)
    })
}

// ==================== 移除按钮 ====================
const removeRelateGoods = (record) => {
  // 后端局部更新语义下 null 表示「不修改」，清空分类关联要传哨兵值 0
  updateGoods(record.id, {categoryId: 0})
      .then(() => {
        message.success('移除成功')
        loadRelateGoods()
        emit('refresh')
      })
      .catch((err) => {
        message.error('移除失败')
        console.log(err)
      })
}
// ==================== 刷新按钮 ====================
const refresh = () => {
  selectedRowKeys.value = []
  selectedRows.value = []
}
</script>


<style scoped>
.add-goods-btn {
  margin-bottom: 10px;
}


/*行元素*/
.goods-modal-table :deep(.ant-table-tbody > tr > td) {
  font-size: 14px;
}

/*操作列*/
:deep(.ant-table-container table > thead > tr:first-child th:last-child) {
  padding-left: 30px;
}

/*商品信息列*/
:deep(.ant-table-container table > thead > tr > th:nth-child(2)) {
  padding-left: 10px;
}

.goods-modal-table img {
  width: 50px;
  object-fit: cover;
}
.footer{
  display: flex;
  justify-content: space-between;
}
</style>