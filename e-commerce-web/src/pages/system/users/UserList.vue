<template>
  <div>
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.keyword" placeholder="用户名 / 姓名 / 手机号" allow-clear
                   @pressEnter="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-select v-model:value="query.status" style="width: 130px" placeholder="状态" allow-clear
                    :options="statusOptions" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <SearchOutlined />
            搜索
          </a-button>
        </a-form-item>
      </a-form>
      <a-button v-permission="'system:user:add'" type="primary" @click="openCreate">新增账号</a-button>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="pagination" row-key="id"
             @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'phone'">
          {{ maskPhone(record.phone) }}
        </template>
        <template v-else-if="column.key === 'roles'">
          <a-tag v-for="name in record.roleNames" :key="name" color="blue">{{ name }}</a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">
            {{ record.status === 1 ? '正常' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <!-- 固定四列，超管/当前账号没有停用和删除按钮，用空占位补齐保证上下对齐 -->
          <div class="row-actions">
            <a-button v-permission="'system:user:edit'" type="link" size="small"
                      @click="openEdit(record)">编辑</a-button>
            <a-button v-permission="'system:user:resetPwd'" type="link" size="small"
                      @click="openReset(record)">重置密码</a-button>
            <!-- 超级管理员和当前登录账号不能停用；停用后旧 token 会立即失效 -->
            <a-popconfirm
                v-if="record.id !== 1 && record.id !== currentUserId"
                :title="record.status === 1 ? '停用后该账号会立即退出登录，确定停用？' : '确定启用该账号？'"
                @confirm="toggleStatus(record)"
            >
              <a-button v-permission="'system:user:edit'" type="link" size="small" :danger="record.status === 1">
                {{ record.status === 1 ? '停用' : '启用' }}
              </a-button>
            </a-popconfirm>
            <span v-else></span>
            <!-- 删除 = 软删：账号从列表消失、立刻退出登录，历史数据保留 -->
            <a-popconfirm
                v-if="record.id !== 1 && record.id !== currentUserId"
                title="删除后该账号会立即退出登录并从列表移除，确定删除？"
                ok-text="确定删除"
                ok-type="danger"
                @confirm="handleDelete(record)"
            >
              <a-button v-permission="'system:user:remove'" type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
            <span v-else></span>
          </div>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑 -->
    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑账号' : '新增账号'" :width="620"
             :confirm-loading="saving" @ok="handleSubmit">
      <a-form :model="form" :label-col="{ span: 5 }">
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" :disabled="!!editingId" :maxlength="30"
                   placeholder="3~30 位字母、数字或下划线" />
        </a-form-item>
        <a-form-item v-if="!editingId" label="初始密码" required>
          <a-input-password v-model:value="form.password" :maxlength="64" placeholder="至少 6 位" />
        </a-form-item>
        <a-form-item label="姓名" required>
          <a-input v-model:value="form.name" :maxlength="64" placeholder="管理员真实姓名" />
        </a-form-item>
        <a-form-item label="手机号">
          <a-input v-model:value="form.phone" :maxlength="11" />
        </a-form-item>
        <a-form-item label="角色" required>
          <a-select v-model:value="form.roleIds" mode="multiple" :options="roleOptions"
                    placeholder="请选择角色" style="width: 100%" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="form.statusBool" checked-children="正常" un-checked-children="停用" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 重置密码 -->
    <a-modal v-model:visible="resetVisible" title="重置密码" :width="440" :confirm-loading="resetting"
             @ok="handleReset">
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="账号">{{ resetTarget?.username }}</a-form-item>
        <a-form-item label="新密码" required>
          <a-input-password v-model:value="resetPassword" :maxlength="64" placeholder="至少 6 位" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getCurrentUser } from '@/utils/auth'
import { maskPhone } from '@/utils/mask.js'
import {
  addAdminUser,
  deleteAdminUser,
  getAdminUsers,
  getRoles,
  resetAdminPassword,
  updateAdminUser,
} from '@/api/system.js'

const list = ref([])
const roles = ref([])
const saving = ref(false)
const formVisible = ref(false)
const editingId = ref(null)
const resetVisible = ref(false)
const resetting = ref(false)
const resetTarget = ref(null)
const resetPassword = ref('')
const query = ref({ keyword: '', status: undefined })
// 当前登录账号不能停用/删除自己（后端也有校验，这里直接不显示按钮）
const currentUserId = computed(() => getCurrentUser()?.id)

const statusOptions = [
  { value: 1, label: '正常' },
  { value: 0, label: '停用' },
]

const roleOptions = computed(() => roles.value.map((role) => ({
  value: role.id,
  label: role.roleName,
})))

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'name', key: 'name' },
  { title: '手机号', dataIndex: 'phone', key: 'phone' },
  { title: '角色', key: 'roles' },
  { title: '状态', key: 'status' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action', width: 320 },
]

const form = ref(emptyForm())

function emptyForm() {
  return {
    username: '',
    password: '',
    name: '',
    phone: '',
    roleIds: [],
    statusBool: true,
  }
}

function loadRoles() {
  getRoles().then((res) => {
    roles.value = res.data.data || []
  })
}

function loadData() {
  getAdminUsers({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    status: query.value.status === undefined ? undefined : query.value.status,
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

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  formVisible.value = true
}

function openEdit(record) {
  editingId.value = record.id
  form.value = {
    username: record.username,
    password: '',
    name: record.name,
    phone: record.phone || '',
    roleIds: record.roleIds || [],
    statusBool: record.status === 1,
  }
  formVisible.value = true
}

function handleSubmit() {
  if (!form.value.username.trim()) {
    message.warning('请输入用户名')
    return
  }
  // 用户名规则与后端一致：3-30 位字母、数字或下划线（仅新增时校验，账号创建后不可改）
  if (!editingId.value && !/^[a-zA-Z0-9_]{3,30}$/.test(form.value.username.trim())) {
    message.warning('用户名为 3-30 位字母、数字或下划线')
    return
  }
  if (!editingId.value && (!form.value.password || form.value.password.length < 6)) {
    message.warning('初始密码至少 6 位')
    return
  }
  if (!form.value.name.trim()) {
    message.warning('请输入姓名')
    return
  }
  if (!form.value.roleIds.length) {
    message.warning('请选择角色')
    return
  }
  const payload = {
    username: form.value.username.trim(),
    password: form.value.password,
    name: form.value.name.trim(),
    phone: form.value.phone.trim(),
    status: form.value.statusBool ? 1 : 0,
    roleIds: form.value.roleIds,
  }
  saving.value = true
  const request = editingId.value
    ? updateAdminUser(editingId.value, payload)
    : addAdminUser(payload)
  request
    .then(() => {
      message.success(editingId.value ? '账号已更新' : '账号已创建')
      formVisible.value = false
      loadData()
    })
    .finally(() => {
      saving.value = false
    })
}

function toggleStatus(record) {
  const nextStatus = record.status === 1 ? 0 : 1
  updateAdminUser(record.id, {
    name: record.name,
    phone: record.phone,
    status: nextStatus,
    roleIds: record.roleIds || [],
  })
    .then(() => {
      message.success(nextStatus === 1 ? '账号已启用' : '账号已停用')
      loadData()
    })
    .catch(() => loadData())
}

// 删除账号（软删）：后端返回后该账号从列表消失
function handleDelete(record) {
  deleteAdminUser(record.id)
    .then(() => {
      message.success('账号已删除')
      loadData()
    })
    .catch(() => loadData())
}

function openReset(record) {
  resetTarget.value = record
  resetPassword.value = ''
  resetVisible.value = true
}

function handleReset() {
  if (!resetPassword.value || resetPassword.value.length < 6) {
    message.warning('新密码至少 6 位')
    return
  }
  resetting.value = true
  resetAdminPassword(resetTarget.value.id, resetPassword.value)
    .then(() => {
      message.success('密码已重置')
      resetVisible.value = false
    })
    .finally(() => {
      resetting.value = false
    })
}

onMounted(() => {
  loadRoles()
  loadData()
})
// 页面被 keep-alive 缓存时，回到本页要同时刷新账号列表和角色下拉，
// 否则在角色管理新增/改名后，这里仍显示旧角色，只能按 F5
onActivated(() => {
  loadRoles()
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

/* 操作列固定四列：超管/当前账号没有停用和删除按钮时留空占位，保证上下对齐 */
.row-actions {
  display: grid;
  grid-template-columns: repeat(4, 72px);
  align-items: center;
  justify-content: center;
}

.row-actions > * {
  justify-self: center;
}
</style>
