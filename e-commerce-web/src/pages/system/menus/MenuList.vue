<template>
  <div>
    <div class="head">
      <span class="tip">维护后台目录、菜单和按钮权限；停用的菜单不会出现在角色分配树里。</span>
      <a-button v-permission="'system:menu:add'" type="primary" @click="openCreate()">新增菜单</a-button>
    </div>

    <a-table
        :data-source="treeData"
        :columns="columns"
        row-key="id"
        :pagination="false"
        :expanded-row-keys="expandedKeys"
        @expandedRowsChange="onExpandedChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'menuName'">
          <!-- 图标已全局注册（stepin），按名字渲染组件；没有图标的节点只显示名称 -->
          <span class="menu-name">
            <component :is="record.icon" v-if="record.icon" class="menu-icon" />
            <span>{{ record.menuName }}</span>
          </span>
        </template>
        <template v-else-if="column.key === 'menuType'">
          <a-tag :color="typeColor(record.menuType)">{{ typeText(record.menuType) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'visible'">
          <a-tag :color="record.visible === 1 ? 'blue' : 'default'">
            {{ record.visible === 1 ? '显示' : '隐藏' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">
            {{ record.status === 1 ? '正常' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <!-- 固定三列，按钮节点没有"新增子项"，用空占位补齐保证上下对齐 -->
          <div class="row-actions">
            <a-button v-permission="'system:menu:edit'" type="link" size="small"
                      @click="openEdit(record)">编辑</a-button>
            <a-button v-if="record.menuType !== 'F'" v-permission="'system:menu:add'" type="link" size="small"
                      @click="openCreate(record)">新增子项</a-button>
            <span v-else></span>
            <a-popconfirm title="确定删除该菜单？" @confirm="handleDelete(record)">
              <a-button v-permission="'system:menu:remove'" type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑菜单 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑菜单' : '新增菜单'" :width="680"
             :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 6 }">
        <a-form-item label="上级菜单">
          <a-tree-select
              v-model:value="form.parentId"
              :tree-data="parentTreeData"
              :field-names="{ label: 'title', value: 'id', children: 'children' }"
              placeholder="不选表示顶级菜单"
              allow-clear
              tree-default-expand-all
              style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="菜单类型" required>
          <a-radio-group v-model:value="form.menuType">
            <a-radio-button value="M">目录</a-radio-button>
            <a-radio-button value="C">菜单</a-radio-button>
            <a-radio-button value="F">按钮</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="菜单名称" required>
          <a-input v-model:value="form.menuName" :maxlength="64" />
        </a-form-item>
        <a-form-item v-if="form.menuType !== 'F'" label="路由路径" :required="form.menuType === 'C'">
          <a-input v-model:value="form.path" :maxlength="200" placeholder="以 / 开头，如 /system/menus" />
        </a-form-item>
        <a-form-item v-if="form.menuType === 'C'" label="组件路径">
          <a-input v-model:value="form.component" :maxlength="200"
                   placeholder="预留字段，当前使用静态路由，可留空" />
        </a-form-item>
        <a-form-item v-if="form.menuType !== 'M'" label="权限标识">
          <a-input v-model:value="form.perms" :maxlength="100"
                   placeholder="模块:实体:动作，如 system:menu:list" />
        </a-form-item>
        <a-form-item v-if="form.menuType !== 'F'" label="图标">
          <a-input v-model:value="form.icon" :maxlength="64"
                   placeholder="Ant Design 图标名，如 SettingOutlined" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 140px" />
        </a-form-item>
        <a-form-item v-if="form.menuType !== 'F'" label="是否显示">
          <a-switch v-model:checked="form.visibleBool" checked-children="显示" un-checked-children="隐藏" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="form.statusBool" checked-children="正常" un-checked-children="停用" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { addMenu, deleteMenu, getMenuManageTree, updateMenu } from '@/api/system.js'

const treeData = ref([])
const expandedKeys = ref([])
const saving = ref(false)
const formVisible = ref(false)
const editingId = ref(null)
const form = ref(emptyForm())

const columns = [
  // 列宽不写死：由表格按内容自适应分配，表格整体撑满容器，右侧不留空白
  { title: '菜单名称', dataIndex: 'menuName', key: 'menuName', align: 'left' },
  { title: '类型', key: 'menuType' },
  { title: '路由路径', dataIndex: 'path', key: 'path' },
  { title: '权限标识', dataIndex: 'perms', key: 'perms' },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder' },
  { title: '显示', key: 'visible' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action' },
]

function emptyForm() {
  return {
    parentId: null,
    menuName: '',
    menuType: 'C',
    path: '',
    component: '',
    perms: '',
    icon: '',
    sortOrder: 0,
    visibleBool: true,
    statusBool: true,
  }
}

function typeText(type) {
  return { M: '目录', C: '菜单', F: '按钮' }[type] || type
}

function typeColor(type) {
  return { M: 'blue', C: 'green', F: 'default' }[type] || 'default'
}

function collectIds(nodes, acc = []) {
  (nodes || []).forEach((node) => {
    acc.push(node.id)
    collectIds(node.children, acc)
  })
  return acc
}

// Ant Design 会把 children: [] 当成可展开节点，叶子节点也会显示 +；
// 递归去掉空 children，只有真正有子级的节点才保留展开图标。
function normalizeTree(nodes) {
  return (nodes || []).map((node) => {
    const children = normalizeTree(node.children)
    const item = { ...node }
    if (children.length) {
      item.children = children
    } else {
      delete item.children
    }
    return item
  })
}

function loadData(options = {}) {
  getMenuManageTree().then((res) => {
    treeData.value = normalizeTree(res.data.data || [])
    const ids = new Set(collectIds(treeData.value))
    // 默认收起，只保留用户手动展开的层级；新增子项后自动展开父级
    let keys = expandedKeys.value.filter((key) => ids.has(key))
    if (options.expandId) {
      keys = [...new Set([...keys, options.expandId])]
    }
    expandedKeys.value = keys
  })
}

function onExpandedChange(keys) {
  expandedKeys.value = keys
}

// 上级菜单树：编辑时排除自身及后代；按钮不能作为父级
function buildParentTree(nodes, excludeId) {
  return (nodes || [])
      .filter((node) => node.id !== excludeId && node.menuType !== 'F')
      .map((node) => ({
        id: node.id,
        title: node.menuType === 'M' ? `${node.menuName}（目录）` : node.menuName,
        children: buildParentTree(node.children, excludeId),
      }))
}

const parentTreeData = computed(() => buildParentTree(treeData.value, editingId.value))

function openCreate(parent) {
  editingId.value = null
  form.value = emptyForm()
  if (parent) {
    form.value.parentId = parent.id
    // 目录下默认建菜单，菜单下默认建按钮
    form.value.menuType = parent.menuType === 'M' ? 'C' : 'F'
  }
  formVisible.value = true
}

function openEdit(record) {
  editingId.value = record.id
  form.value = {
    parentId: record.parentId || null,
    menuName: record.menuName,
    menuType: record.menuType,
    path: record.path || '',
    component: record.component || '',
    perms: record.perms || '',
    icon: record.icon || '',
    sortOrder: record.sortOrder || 0,
    visibleBool: record.visible === 1,
    statusBool: record.status === 1,
  }
  formVisible.value = true
}

function handleSubmit() {
  if (!form.value.menuName.trim()) {
    message.warning('请输入菜单名称')
    return
  }
  if (form.value.menuType === 'C') {
    if (!form.value.path.trim()) {
      message.warning('菜单必须填写路由路径')
      return
    }
    if (!form.value.path.trim().startsWith('/')) {
      message.warning('路由路径必须以 / 开头')
      return
    }
  }
  const payload = {
    parentId: form.value.parentId ?? 0,
    menuName: form.value.menuName.trim(),
    menuType: form.value.menuType,
    path: form.value.menuType === 'F' ? null : (form.value.path.trim() || null),
    component: form.value.menuType === 'C' ? (form.value.component.trim() || null) : null,
    perms: form.value.perms.trim() || null,
    icon: form.value.menuType === 'F' ? null : (form.value.icon.trim() || null),
    sortOrder: form.value.sortOrder ?? 0,
    visible: form.value.visibleBool ? 1 : 0,
    status: form.value.statusBool ? 1 : 0,
  }
  saving.value = true
  const request = editingId.value ? updateMenu(editingId.value, payload) : addMenu(payload)
  const parentId = form.value.parentId
  request
      .then(() => {
        message.success(editingId.value ? '菜单已更新' : '菜单已创建')
        formVisible.value = false
        // 新增子项后展开父级，方便看到刚创建的行
        loadData(!editingId.value && parentId ? { expandId: parentId } : {})
      })
      .finally(() => {
        saving.value = false
      })
}

function handleDelete(record) {
  deleteMenu(record.id).then(() => {
    message.success('菜单已删除')
    loadData()
  })
}

onMounted(loadData)
onActivated(loadData)
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0;
}

.tip {
  color: #999;
  font-size: 13px;
}

/* 菜单名称：图标 + 文字，图标放在名称左侧 */
.menu-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.menu-icon {
  font-size: 16px;
  color: #5f7799;
}

/* 操作列固定三列：按钮节点没有"新增子项"时留空占位，保证上下对齐 */
.row-actions {
  display: grid;
  grid-template-columns: repeat(3, 72px);
  align-items: center;
  justify-content: center;
}

.row-actions > * {
  justify-self: center;
}
</style>
