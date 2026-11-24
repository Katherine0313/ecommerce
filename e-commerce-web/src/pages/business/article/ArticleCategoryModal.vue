<template>
  <a-modal :visible="visibleProxy" title="文章分类管理" :width="720" :footer="null"
           @cancel="close">
    <div class="bar">
      <a-button v-permission="'business:article:add'" type="primary" size="small" @click="openCreate">
        新建分类
      </a-button>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="false" row-key="id" size="small">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.statusDesc }}</a-tag>
        </template>
        <template v-else-if="column.key === 'articleCount'">
          {{ record.articleCount || 0 }}
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="action-buttons">
            <a-button v-permission="'business:article:edit'" size="small" type="primary"
                      @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除该分类？分类下还有文章时无法删除。" @confirm="handleDelete(record)">
              <a-button v-permission="'business:article:remove'" size="small" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 分类新增 / 编辑 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑分类' : '新建分类'" :width="520"
             :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="分类名称" required>
          <a-input v-model:value="form.name" :maxlength="64" placeholder="如「公司公告」" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" style="width: 200px" />
          <span class="unit">数字越小越靠前</span>
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model:value="form.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  addArticleCategory,
  deleteArticleCategory,
  getArticleCategoryList,
  updateArticleCategory,
} from '@/api/article.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'change'])

// Vue 3.2 没有 defineModel，用 computed 代理 visible
const visibleProxy = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const list = ref([])
const formVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)

const columns = [
  { title: '分类名称', dataIndex: 'name', key: 'name' },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '文章数', key: 'articleCount', width: 90 },
  { title: '操作', key: 'action', width: 160 },
]

const form = ref(emptyForm())

function emptyForm() {
  return { name: '', sortOrder: 0, status: 1 }
}

function loadData() {
  getArticleCategoryList().then((res) => {
    list.value = res.data.data || []
  })
}

watch(() => props.visible, (visible) => {
  if (visible) loadData()
}, { immediate: true })

function close() {
  emit('update:visible', false)
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  formVisible.value = true
}

function openEdit(record) {
  editingId.value = record.id
  form.value = {
    name: record.name,
    sortOrder: record.sortOrder ?? 0,
    status: record.status,
  }
  formVisible.value = true
}

function handleSubmit() {
  if (!form.value.name) {
    message.warning('请输入分类名称')
    return
  }
  saving.value = true
  const request = editingId.value
    ? updateArticleCategory(editingId.value, form.value)
    : addArticleCategory(form.value)
  request
    .then(() => {
      message.success(editingId.value ? '分类已更新' : '分类已创建')
      formVisible.value = false
      loadData()
      emit('change')
    })
    .finally(() => {
      saving.value = false
    })
}

function handleDelete(record) {
  deleteArticleCategory(record.id).then(() => {
    message.success('分类已删除')
    loadData()
    emit('change')
  })
}
</script>

<style scoped>
.bar {
  margin-bottom: 12px;
}

.unit {
  margin-left: 8px;
  color: #999;
}

/* 操作列一排两个，避免按钮挤在一行 */
.action-buttons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.action-buttons :deep(.ant-btn) {
  width: 100%;
}
</style>
