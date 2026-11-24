<template>
  <a-modal v-model:visible="firstOpen" title="关联产品" width="900px" @ok="confirmTagGoods" wrap-class-name="drag-modal-tag1">
    <div class="add-goods">
      <a-button type="primary" @click="handleAddGoods">添加产品</a-button>
    </div>
    <a-table :row-key="record => record.id"
             :row-selection="{selectedRowKey: selectedTagGoods,
        onChange:onTagGoodsSelectChange }"
             :data-source="tagGoods" class="first-modal-table">
      <a-table-column title="商品信息">
        <template #default="{record}">
          <img v-lazy="record.image" width="50px">
        </template>
      </a-table-column>
      <a-table-column data-index="name"></a-table-column>
      <a-table-column title="操作" align="center">
        <template #default="{record}">
          <a-button type="link" danger @click="remove(record)">移除</a-button>
        </template>
      </a-table-column>
    </a-table>
    <template #footer>
      <div class="dialog-footer">
        <a-button @click="reloadTagGoods">刷新
          <ReloadOutlined />
        </a-button>
        <div class="r">
          <!--          <span style="margin-right: 20px">已选 {{ selectedTagGoods.length }} 条</span>-->
          <a-button @click="removeTagGoods">批量移除</a-button>
        </div>
      </div>
    </template>
  </a-modal>
  <!--关联产品的第二个对话框-->
  <a-modal v-model:visible="open" title="选择商品" width="900px" centered wrap-class-name="drag-modal-tag2">
    <div class="goods-modal-wrap">
      <div class="l">
        <div class="head">
          <a-space>
            <a-input placeholder="请输入商品名称" v-model:value="search" @pressEnter="handleSearch"></a-input>
            <a-button type="primary" @click="handleSearch">搜索
              <SearchOutlined />
            </a-button>
          </a-space>
          <a-cascader placeholder="商品分类"
                      :options="categoryTree"
                      :field-names="{ value: 'id', label: 'name', children: 'children' }"
                      v-model:value="selectedCategory"
                      :change-on-select="false"
                      @change="handleCategoryChange" />
        </div>
        <div class="content">
          <a-table :row-key="record => record.id"
                   :row-selection="goodsRowSelection"
                   class="goods-modal-table" :data-source="goodsList"
                   :scroll="{y:400}" :pagination="false">
            <a-table-column title="商品信息">
              <template #default="{record}">
                <img v-lazy="record.image">
              </template>
            </a-table-column>
            <a-table-column data-index="name"></a-table-column>
            <a-table-column data-index="subTitle"></a-table-column>
          </a-table>
        </div>
      </div>
      <div class="r">
        <div class="head">
          <span>已选{{ selectedGoods.length }}个</span>
        </div>
        <div class="content">
          <a-table :row-key="record => record.id" class="goods-modal-table"
                   :data-source="selectedGoods" :scroll="{y:400}" :pagination="false">
            <a-table-column title="商品信息" data-index="name"></a-table-column>
            <a-table-column title="操作" align="center">
              <template #default="{record}">
                <a-button type="link" danger @click="removeGoods(record)">移除</a-button>
              </template>
            </a-table-column>
          </a-table>
        </div>
      </div>
    </div>
    <!--自定义底部：左下角刷新，右侧取消/确定-->
    <template #footer>
      <div class="second-modal-footer">
        <a-button @click="handleRefresh" type="primary">
          <ReloadOutlined />
          刷新
        </a-button>
        <div>
          <a-button @click="open = false">取消</a-button>
          <a-button type="primary" @click="tagConfirm" style="margin-left: 8px">确定</a-button>
        </div>
      </div>
    </template>
  </a-modal>
</template>
<script setup>
import {computed, onMounted, ref} from 'vue'
import { useDragModal } from '@/composables/useDragModal'
useDragModal('drag-modal-tag1')
useDragModal('drag-modal-tag2')
import {getCategoryTree, getGoodsList} from '@/api/products.js'
import {bindTag, getTagGoods, getTagList, unbindTagGood, unbindTagGoods} from '@/api/tags.js'
import {message} from 'ant-design-vue'
import {SearchOutlined, ReloadOutlined} from '@ant-design/icons-vue'
// 定义对话框的响应式变量
const firstOpen = ref(false)
const open = ref(false)
// 通知父组件刷新标签列表
const emit = defineEmits(['refresh'])
// 添加商品中商品列表响应式变量
const goodsList = ref([])
// 定义搜索框关键词
const search = ref('')
// 添加商品的级联目录
const categoryTree = ref([])
// 当前选中的分类路径（id 数组，如 [1, 101]）
const selectedCategory = ref([])
// 已选商品（累积，跨搜索/分类不丢失），右侧表格数据源
const selectedGoods = ref([])
// 当前选中的标签（点击"关联产品"时记录）
const currentTag = ref(null)
// 第一个弹窗中的响应式变量
const tagGoods = ref([])
// 第一个弹窗中批量选中的商品ID
const selectedTagGoods = ref([])

// 暴露给父组件调用的方法：传入要关联的标签并打开弹窗
defineExpose({
  firstOpen(record) {
    currentTag.value = record
    firstOpen.value = true
    tagGoods.value = []
    loadTagGoods()
  }
})
// ************* 第一个弹窗 ****************
// 加载已打当前标签的产品列表
const loadTagGoods = (record) => {
  getTagGoods(currentTag.value.id).then((res) => {
    tagGoods.value = res.data.data
  })
}
// 解绑单个产品
const remove = (record) => {
  unbindTagGood(record.id, currentTag.value.id)
      .then(() => {
        message.success('已移除')
        loadTagGoods()
        emit('refresh')
      })
      .catch(() => {
        message.error('移除失败，请稍后重试')
      })
}
// 第一个弹窗的ok事件
const confirmTagGoods = () => {
  firstOpen.value = false
  emit('refresh')
}
// 第一个弹窗的刷新按钮
const reloadTagGoods = () => {
  getTagGoods(currentTag.value.id)
      .then(res => {
        tagGoods.value = res.data.data
      })
      .catch((err) => {
        console.log(err)
      })
}

// 复选框选中变化
const onTagGoodsSelectChange = (selectedRowKeys) => {
  selectedTagGoods.value = selectedRowKeys
}
// 批量解绑产品
const removeTagGoods = () => {
  unbindTagGoods(currentTag.value.id, selectedTagGoods.value)
      .then(() => {
        loadTagGoods()
        emit('refresh')
      })
}


// ************* 第二个弹窗 ****************
//  选择分类时按子类筛选商品
const handleCategoryChange = () => {
  loadGoodsList()
}

// 定义打开弹窗的方法
const handleAddGoods = () => {
  selectedGoods.value = []
  loadGoodsList()
  open.value = true
}

// 从已选中移除某商品（右侧"移除"按钮 + 左侧取消勾选共用）
const removeGoods = (record) => {
  selectedGoods.value = selectedGoods.value.filter(g => g.id !== record.id)
}
// 左侧表格受控选择：selectedRowKeys 受控，onSelect/onSelectAll 维护 selectedGoods
// 全选(changeRows)只作用于当前筛选后的列表，搜"苹果"后全选只会勾选苹果相关商品
const goodsRowSelection = computed(() => ({
  selectedRowKeys: selectedGoods.value.map(g => g.id),
  onSelect: (record, selected) => {
    if (selected) {
      selectedGoods.value.push(record)
    } else {
      removeGoods(record)
    }
  },
  onSelectAll: (selected, selectedRows, changeRows) => {
    if (selected) {
      changeRows.forEach(row => {
        if (!selectedGoods.value.some(g => g.id === row.id)) {
          selectedGoods.value.push(row)
        }
      })
    } else {
      const removeIds = changeRows.map(r => r.id)
      selectedGoods.value = selectedGoods.value.filter(g => !removeIds.includes(g.id))
    }
  }
}))
// 加载商品列表（排除已有任何标签的产品，确保一个产品最多一个标签）
const loadGoodsList = () => {
  // 先拿全部标签，再拿每个标签下的产品，汇总成"已有标签的产品 ID 集合"
  getTagList()
    .then(tagRes => {
      const allTags = tagRes.data.data
      const tagPromises = allTags.map(t => getTagGoods(t.id))
      return Promise.all([
        getGoodsList({
          search: search.value,
          categoryId: selectedCategory.value[selectedCategory.value.length - 1]
        }),
        ...tagPromises
      ])
    })
    .then(([goodsRes, ...tagResults]) => {
      const allGoods = goodsRes.data.data.list
      const boundIds = new Set()
      tagResults.forEach(r => {
        (r.data.data || []).forEach(p => boundIds.add(p.id))
      })
      goodsList.value = allGoods.filter(g => !boundIds.has(g.id))
    })
    .catch(err => console.log(err))
}
// 第二个弹窗中的搜索事件
const handleSearch = () => {
  loadGoodsList()
}
// 渲染商品目录
onMounted(() => {
  getCategoryTree()
      .then(res => {
        categoryTree.value = res.data.data
      })
      .catch(err => {
        console.log(err)
      })
})


// 刷新：清空搜索/分类条件后重新加载全部商品
const handleRefresh = () => {
  search.value = ''
  selectedCategory.value = []
  loadGoodsList()
}
// 把已选商品全部绑定到当前标签
const tagConfirm = () => {
  bindTag(currentTag.value.id, selectedGoods.value.map(g => g.id))
      .then(res => {
        message.success('关联成功')
        open.value = false
        selectedGoods.value = []
        loadGoodsList()
        loadTagGoods(currentTag.value.id)
        emit('refresh')
      })
      .catch(err => {
        message.error('关联失败，请稍后重试')
      })
}


</script>


<style scoped>
.add-goods {
  margin-bottom: 16px;
}

/*标签页首页*/

/*=========第一个弹窗============*/

/*行元素*/
.first-modal-table :deep(.ant-table-tbody td) {
  font-size: 14px;
}

.first-modal-table :deep(th:nth-child(3)),
.first-modal-table :deep(td:nth-child(3)) {
  padding-left: 10px;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
}

/*=========第二个弹窗============*/
.goods-modal-wrap {
  max-width: 1000px;
  display: flex;
  gap: 10px;
  overflow: hidden;
}

.l {
  border-right: 1px solid #ccc;
  width: 60%;
  padding-right: 8px;
}

.r {
  flex: 1;
}

.head {
  padding: 8px;
  display: flex;
  gap: 15px;
}

.r .head {
  display: flex;
  align-items: center; /*flex的垂直居中属性*/
  padding: 12px 0 12px 0;
}

/*表头*/

/*行元素*/
.goods-modal-table :deep(.ant-table-tbody > tr > td) {
  font-size: 14px;
}

/*行*/
.goods-modal-table :deep(.ant-table-tbody > tr > td) {
  text-align: center;
}

.goods-modal-table :deep(.ant-table-tbody > tr > td) img {
  max-width: 40px;
}

/*选择商品弹窗底部：刷新按钮靠左，取消/确定靠右*/
.second-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>