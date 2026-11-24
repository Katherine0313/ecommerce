<template>
  <div>
    <a-card title="销售规格库">
      <div class="head">
        <a-input v-model:value="newGroupName" placeholder="新增规格名，如 颜色" style="width:240px" allow-clear />
        <a-button v-permission="'product:product:add'" type="primary" @click="createGroup">新增规格名</a-button>
        <span class="tip">规格库只提供候选值；商品实际卖哪些规格值，在商品编辑页单独维护。</span>
      </div>

      <a-table :data-source="groups" row-key="id" :pagination="false" size="middle">
        <a-table-column title="规格名" width="220">
          <template #default="{ record }">
            <a-input v-if="editingId === record.id" v-model:value="editingName" size="small" @pressEnter="saveName(record)" />
            <span v-else>{{ record.name }}</span>
          </template>
        </a-table-column>
        <a-table-column title="候选值">
          <template #default="{ record }">
            <div class="values">
              <a-tag v-for="item in record.values" :key="item.id" closable @close="removeValue(record, item)">
                {{ item.value }}
              </a-tag>
              <a-input
                  v-model:value="newValue[record.id]"
                  size="small"
                  class="value-input"
                  placeholder="+ 添加值，回车确认"
                  @pressEnter="addValue(record)"
              />
            </div>
          </template>
        </a-table-column>
        <a-table-column title="状态" width="100">
          <template #default="{ record }">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.status === 1 ? '启用' : '停用' }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="操作" width="220">
          <template #default="{ record }">
            <template v-if="editingId === record.id">
              <a-button type="link" size="small" @click="saveName(record)">保存</a-button>
              <a-button type="link" size="small" @click="cancelEdit">取消</a-button>
            </template>
            <template v-else>
              <a-button v-permission="'product:product:edit'" type="link" size="small" @click="startEdit(record)">重命名</a-button>
              <a-button v-permission="'product:product:edit'" type="link" size="small" @click="toggle(record)">
                {{ record.status === 1 ? '停用' : '启用' }}
              </a-button>
              <a-button v-permission="'product:product:remove'" type="link" size="small" danger @click="removeGroup(record)">删除</a-button>
            </template>
          </template>
        </a-table-column>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import {message, Modal} from 'ant-design-vue'
import {
  getSpecLibrary,
  addSpecGroup,
  updateSpecGroup,
  deleteSpecGroup,
  addSpecValue,
  deleteSpecValue
} from '@/api/specs.js'

const groups = ref([])
const newGroupName = ref('')
const newValue = ref({})
const editingId = ref(null)
const editingName = ref('')

const load = () => {
  getSpecLibrary()
      .then(res => {
        groups.value = res.data?.data || []
      })
      .catch(() => message.error('规格库加载失败'))
}

const createGroup = () => {
  const name = newGroupName.value.trim()
  if (!name) {
    message.warning('请输入规格名')
    return
  }
  addSpecGroup({name})
      .then(() => {
        message.success('新增成功')
        newGroupName.value = ''
        load()
      })
      .catch(err => message.error(err?.response?.data?.message || '新增失败'))
}

const startEdit = (record) => {
  editingId.value = record.id
  editingName.value = record.name
}

const cancelEdit = () => {
  editingId.value = null
  editingName.value = ''
}

const saveName = (record) => {
  const name = editingName.value.trim()
  if (!name) {
    message.warning('规格名不能为空')
    return
  }
  updateSpecGroup(record.id, {name, status: record.status})
      .then(() => {
        message.success('已保存')
        cancelEdit()
        load()
      })
      .catch(err => message.error(err?.response?.data?.message || '保存失败'))
}

const toggle = (record) => {
  const status = record.status === 1 ? 0 : 1
  updateSpecGroup(record.id, {name: record.name, status})
      .then(() => {
        message.success(status === 1 ? '已启用' : '已停用')
        load()
      })
      .catch(err => message.error(err?.response?.data?.message || '操作失败'))
}

const removeGroup = (record) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除规格「${record.name}」吗？已被商品引用时后端会拒绝。`,
    onOk() {
      return deleteSpecGroup(record.id)
          .then(() => {
            message.success('删除成功')
            load()
          })
          .catch(err => message.error(err?.response?.data?.message || '删除失败'))
    }
  })
}

const addValue = (record) => {
  const value = (newValue.value[record.id] || '').trim()
  if (!value) {
    message.warning('请输入规格值')
    return
  }
  addSpecValue({groupId: record.id, value})
      .then(() => {
        newValue.value[record.id] = ''
        load()
      })
      .catch(err => message.error(err?.response?.data?.message || '新增值失败'))
}

const removeValue = (record, item) => {
  deleteSpecValue(item.id)
      .then(() => {
        message.success('已删除')
        load()
      })
      .catch(err => message.error(err?.response?.data?.message || '删除失败'))
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.tip {
  color: #8c8c8c;
  font-size: 12px;
}

.values {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.value-input {
  width: 170px;
}
</style>
