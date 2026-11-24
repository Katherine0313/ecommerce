<template>
  <div>
    <div class="head">
      <span class="tip">角色决定后台菜单和按钮权限，账号通过分配角色获得权限。</span>
      <a-button v-permission="'system:role:add'" type="primary" @click="openCreate">新增角色</a-button>
    </div>

    <a-table :dataSource="list" :columns="columns" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'userCount'">
          <!-- 启用中的账号数 / 总账号数：禁用账号不阻止角色删除 -->
          <a-tooltip v-if="record.enabledUserCount > 0" :title="`有 ${record.enabledUserCount} 个启用中的账号，暂不能删除`">
            <span>{{ record.enabledUserCount }} / {{ record.userCount }}</span>
          </a-tooltip>
          <span v-else>{{ record.userCount }} / {{ record.userCount }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">
            {{ record.status === 1 ? '正常' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <!-- 固定三列，缺失的按钮用空占位补齐，保证每行按钮上下对齐 -->
          <div class="row-actions">
            <a-button v-permission="'system:role:edit'" type="link" size="small"
                      @click="openEdit(record)">编辑</a-button>
            <a-button v-permission="'system:role:assign'" type="link" size="small"
                      @click="openAssign(record)">分配菜单</a-button>
            <a-popconfirm v-if="record.roleKey !== 'admin'" title="确定删除该角色？" @confirm="handleDelete(record)">
              <a-button v-permission="'system:role:remove'" type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
            <span v-else></span>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑角色 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑角色' : '新增角色'" :width="560"
             :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="角色标识" required>
          <a-input v-model:value="form.roleKey" :disabled="!!editingId" :maxlength="32"
                   placeholder="小写字母开头，如 operator" />
        </a-form-item>
        <a-form-item label="角色名称" required>
          <a-input v-model:value="form.roleName" :maxlength="64" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="form.statusBool" checked-children="正常" un-checked-children="停用" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :maxlength="120" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分配菜单 -->
    <a-modal v-model:visible="assignVisible" :title="`分配菜单 - ${assignTarget?.roleName || ''}`"
             :width="620" :confirm-loading="assigning" @ok="handleAssign">
      <div class="tree-tools">
        <a-button size="small" @click="selectReadonly">全部只读</a-button>
        <a-button size="small" @click="selectAllMenus">全选</a-button>
        <a-button size="small" @click="checkedKeys = []">清空</a-button>
        <span class="tree-tip">只勾菜单本身 = 只能查看；再勾下面的按钮 = 才可新增/编辑/删除</span>
      </div>
      <div class="tree-wrap">
        <!-- check-strictly：父子节点独立勾选，否则勾一个菜单会把它的所有按钮一起勾上，永远设不成只读 -->
        <a-tree v-model:checkedKeys="checkedKeys" :tree-data="treeData" checkable check-strictly
                default-expand-all :selectable="false" @check="handleTreeCheck" />
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  addRole,
  assignRoleMenus,
  deleteRole,
  getMenuTree,
  getRole,
  getRoles,
  updateRole,
} from '@/api/system.js'

const list = ref([])
const menuTree = ref([])
const saving = ref(false)
const formVisible = ref(false)
const editingId = ref(null)
const assignVisible = ref(false)
const assigning = ref(false)
const assignTarget = ref(null)
const checkedKeys = ref([])
const form = ref(emptyForm())

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '角色标识', dataIndex: 'roleKey', key: 'roleKey' },
  { title: '角色名称', dataIndex: 'roleName', key: 'roleName' },
  { title: '账号数', key: 'userCount' },
  { title: '状态', key: 'status'},
  { title: '备注', dataIndex: 'remark', key: 'remark' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action', width: 250 },
]

function emptyForm() {
  return {
    roleKey: '',
    roleName: '',
    statusBool: true,
    remark: '',
  }
}

function loadData() {
  getRoles().then((res) => {
    list.value = res.data.data || []
  })
}

function loadMenuTree() {
  return getMenuTree().then((res) => {
    menuTree.value = res.data.data || []
  })
}

function toTreeData(nodes) {
  return (nodes || []).map((node) => ({
    // 按钮节点标出来，避免和「菜单」混淆：只勾菜单 = 只读，勾按钮 = 可操作
    title: `${node.type === 'F' ? '[按钮] ' : ''}${node.name}${node.perms ? `（${node.perms}）` : ''}`,
    key: node.id,
    children: toTreeData(node.children),
  }))
}

function walkMenus(nodes, fn) {
  ;(nodes || []).forEach((node) => {
    fn(node)
    walkMenus(node.children, fn)
  })
}

// 一键只读：只勾「列表/查看」类菜单页（:list / :view），不勾按钮，也不勾「添加商品」这种 :add 页面
function selectReadonly() {
  const keys = []
  walkMenus(menuTree.value, (node) => {
    if (node.type === 'C' && /:(list|view)$/.test(node.perms || '')) keys.push(node.id)
  })
  checkedKeys.value = keys
}

function selectAllMenus() {
  const keys = []
  walkMenus(menuTree.value, (node) => keys.push(node.id))
  checkedKeys.value = keys
}

const treeData = computed(() => toTreeData(menuTree.value))

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  formVisible.value = true
}

function openEdit(record) {
  editingId.value = record.id
  form.value = {
    roleKey: record.roleKey,
    roleName: record.roleName,
    statusBool: record.status === 1,
    remark: record.remark || '',
  }
  formVisible.value = true
}

function handleSubmit() {
  if (!form.value.roleKey.trim()) {
    message.warning('请输入角色标识')
    return
  }
  if (!form.value.roleName.trim()) {
    message.warning('请输入角色名称')
    return
  }
  const payload = {
    roleKey: form.value.roleKey.trim(),
    roleName: form.value.roleName.trim(),
    status: form.value.statusBool ? 1 : 0,
    remark: form.value.remark,
  }
  saving.value = true
  const request = editingId.value ? updateRole(editingId.value, payload) : addRole(payload)
  request
    .then(() => {
      message.success(editingId.value ? '角色已更新' : '角色已创建')
      formVisible.value = false
      loadData()
    })
    .finally(() => {
      saving.value = false
    })
}

function openAssign(record) {
  assignTarget.value = record
  // 菜单树接口需要 system:role:assign 权限，只在真正打开「分配菜单」时加载；
  // 提前在页面初始化时加载，会让只读角色一进角色管理页就 403
  Promise.all([loadMenuTree(), getRole(record.id)]).then(([, res]) => {
    checkedKeys.value = toCheckedKeys(res.data.data.menuIds)
    assignVisible.value = true
  })
}

// check-strictly 模式下 a-tree 回传的是 { checked, halfChecked }，统一转成 id 数组
function toCheckedKeys(value) {
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.checked)) return value.checked
  return []
}

function handleTreeCheck(keys) {
  checkedKeys.value = toCheckedKeys(keys)
}

// 勾选子菜单时自动补上父菜单，保证侧边栏能正确组装
function withAncestors(keys) {
  const checked = toCheckedKeys(keys)
  const parentMap = new Map()
  const walk = (nodes) => {
    (nodes || []).forEach((node) => {
      (node.children || []).forEach((child) => parentMap.set(child.id, node.id))
      walk(node.children)
    })
  }
  walk(menuTree.value)
  const result = new Set(checked)
  checked.forEach((key) => {
    let parent = parentMap.get(key)
    while (parent) {
      result.add(parent)
      parent = parentMap.get(parent)
    }
  })
  return Array.from(result)
}

function handleAssign() {
  assigning.value = true
  assignRoleMenus(assignTarget.value.id, withAncestors(checkedKeys.value))
    .then(() => {
      message.success('菜单权限已保存')
      assignVisible.value = false
      loadData()
    })
    .finally(() => {
      assigning.value = false
    })
}

function handleDelete(record) {
  deleteRole(record.id).then(() => {
    message.success('角色已删除')
    loadData()
  })
}

onMounted(() => {
  loadData()
})
// 回到本页刷新角色列表；菜单树改为每次打开「分配菜单」时重新拉取
onActivated(() => {
  loadData()
})
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

.tree-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.tree-tip {
  color: #999;
  font-size: 12px;
}

.tree-wrap {
  max-height: 60vh;
  overflow: auto;
  padding: 8px 4px;
}

/* 操作列固定三列：缺按钮时留空占位，保证多行按钮上下对齐 */
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
