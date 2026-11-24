<template>
  <a-modal title="添加商品" v-model:visible="addGoodsOpen" :width="1000" wrap-class-name="drag-modal-addgoods" >
    <div class="container">
      <div class="l">
        <div class="head">
          <a-space>
            <a-input placeholder="请填写商品名称" v-model:value="search" />
            <a-button type="primary" @click="handleSearch">搜索
              <SearchOutlined />
            </a-button>
            <a-select style="width:150px;" placeholder="请选择标签" v-model:value="selectedTag">
              <a-select-option v-for="item in tagList" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
            </a-select>
          </a-space>
        </div>
        <a-table class="goods-modal-table" :data-source="goodsList"
                 :row-selection="{ selectedRowKeys, onChange: onSelectChange }" :row-key="'id'"
          :scroll="{y:1000}">
          <a-table-column title="商品信息">
            <template #default="{record}">
              <img v-lazy="record.image" width="80"/>
            </template>
          </a-table-column>
          <a-table-column title="商品名称" dataIndex="name"></a-table-column>
          <a-table-column dataIndex="subTitle"></a-table-column>
        </a-table>
      </div>
      <div class="r">
        <span>已选{{ selectedRowKeys.length }}个</span>
        <a-table class="goods-modal-table" :data-source="selectedRows" :row-key="'id'">
          <a-table-column title="商品信息">
            <template #default="{record}">
              <img v-lazy="record.image" width="100" />
            </template>
          </a-table-column>
          <a-table-column title="商品名称" dataIndex="name"></a-table-column>
          <a-table-column title="操作">
            <template #default="{record}">
              <a-button type="link" danger @click="removeSelected(record)">移除</a-button>
            </template>
          </a-table-column>
        </a-table>
      </div>
    </div>
    <template #footer>
      <div class="footer">
        <a-button @click="refreshGoodsList">刷新
          <ReloadOutlined />
        </a-button>
        <div class="footer-r">
          <a-button style="margin-right:5px;" @click="cancelAddGoods">取消</a-button>
          <a-button type="primary" @click="confirmAddGoods">确定</a-button>
        </div>
      </div>
    </template>
  </a-modal>
</template>
<script setup>
import {ref, onMounted, watch} from 'vue'
import { useDragModal } from '@/composables/useDragModal'
useDragModal('drag-modal-addgoods')
import {SearchOutlined, ReloadOutlined} from '@ant-design/icons-vue'
import {getGoodsList, updateGoods} from '@/api/products.js'
import {getTagGoods, getTagList} from '@/api/tags.js'
import {message} from 'ant-design-vue'

// ==================== 弹窗状态 ====================
const addGoodsOpen = ref(false)
const categoryId = ref(null)

// ==================== 左侧表格：搜索 ====================
const search = ref('')
const handleSearch = () => {
  getGoodsList({search: search.value, pageSize: 1000})
      .then(res => {
        setGoodsList(res.data.data.list)
      })
      .catch(err => {
        console.log(err)
      })
}
// ==================== 左侧表格：商品标签 ====================
const tagList = ref([])
onMounted(() => {
  getTagList()
      .then(res => {
        tagList.value = res.data.data
      })
})
// 定义选中的标签
const selectedTag = ref(null)
// 监听标签变化，筛选商品
watch(selectedTag, (newTagId) => {
  if (newTagId) {
    getTagGoods(newTagId)
        .then(res => {
          setGoodsList(res.data.data)
        })
        .catch(err => {
          console.log('获取标签失败', err)
        })

  }
})
// ==================== 左侧表格：全部商品 ====================
const goodsList = ref([])
// 统一入口：只保留还没绑定分类的商品
// 已经属于某个分类的商品不能再被关联，否则会把它从原分类里静默抢走
const setGoodsList = (list) => {
  goodsList.value = list.filter(item => !item.categoryId)
}

const openAddGoodsModal = (catId) => {
  categoryId.value = catId
  addGoodsOpen.value = true
  getGoodsList({pageSize: 1000})
      .then(res => {
        setGoodsList(res.data.data.list)
      })
}


// ==================== 复选框 ====================
const selectedRowKeys = ref([])
const selectedRows = ref([])
const onSelectChange = (keys, rows) => {
  selectedRowKeys.value = keys
  selectedRows.value = rows
}


// ==================== 右侧表格：已选商品 ====================
const removeSelected = (record) => {
  selectedRowKeys.value = selectedRowKeys.value.filter(k => k !== record.id)
  selectedRows.value = selectedRows.value.filter(r => r.id !== record.id)
}
// ****************************************************
// ==================== 页脚按钮 =======================
// 1. 刷新按钮
const refreshGoodsList = () => {
  search.value = ''
  selectedTag.value = null
  selectedRowKeys.value = []
  selectedRows.value = []
  getGoodsList({pageSize: 1000})
      .then(res => {
        setGoodsList(res.data.data.list)
      })
      .catch(err => {
        console.log(err)
      })
}
// 2. 取消按钮
const cancelAddGoods = () => {
  addGoodsOpen.value = false
}
// 3. 确定按钮
const emit = defineEmits(['confirm'])
const confirmAddGoods = () => {
  const promises = selectedRows.value.map(product =>
    updateGoods(product.id, { categoryId: categoryId.value })
  )
  Promise.all(promises)
    .then(() => {
      message.success(`已关联 ${selectedRows.value.length} 个产品`)
      emit('confirm')
      addGoodsOpen.value = false
    })
    .catch(err => {
      message.error('关联失败')
      console.error(err)
    })
}
// ==================== 暴露给父组件 ====================
defineExpose({openAddGoodsModal})
</script>

<style scoped>
.container {
  display: flex;
  gap: 20px;
}

.container .l {
  display: flex;
  flex-direction: column;
  width: 60%;
}

.container .r {
  flex: 1;
  display: flex;
  flex-direction: column;
}


/*行元素*/
.goods-modal-table :deep(.ant-table-tbody > tr > td) {
  font-size: 14px;
}

.container .head {
  margin-bottom: 10px;
}

.r span {
  margin-bottom: 5px;
  margin-top: 6px;
  padding-bottom: 8px;
}

/*页脚*/
.footer {
  display: flex;
  justify-content: space-between;
}
</style>