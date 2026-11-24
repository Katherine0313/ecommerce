<template>
  <div class="profile-page">
    <!-- 顶部资料卡片 -->
    <div class="profile-card">
      <a-avatar :src="profile.adminAvatar || defaultAvatar" :size="72" />
      <div class="profile-card-info">
        <div class="profile-card-name">{{ profile.name || profile.username }}</div>
        <div class="profile-card-meta">
          <a-tag v-if="profile.roles && profile.roles.length" :color="'blue'" class="role-tag">
            {{ profile.roles.join(' · ') }}
          </a-tag>
        </div>
        <div class="profile-card-sub">
          <span>@{{ profile.username }}</span>
          <span>{{ profile.phone ? maskPhone(profile.phone) : '手机号未绑定' }}</span>
          <span v-if="profile.createTime">入职 {{ formatDate(profile.createTime) }}</span>
        </div>
      </div>
    </div>

    <!-- Tab 面板 -->
    <a-tabs v-model:activeKey="activeTab" class="profile-tabs" size="large">
      <!-- ============ 基本资料 ============ -->
      <a-tab-pane key="basic" tab="基本资料">
        <a-form :model="basicForm" ref="basicFormRef" :rules="basicRules"
                :label-col="{ span: 4 }" :wrapper-col="{ span: 14 }" class="profile-form">
          <a-form-item label="用户名">
            <a-input :value="profile.username" disabled />
            <div class="form-hint">登录标识，创建后不可修改</div>
          </a-form-item>
          <a-form-item label="姓名" name="name">
            <a-input v-model:value="basicForm.name" placeholder="请输入姓名" :maxlength="32"
                     show-count allow-clear />
          </a-form-item>
          <a-form-item label="手机号" name="phone">
            <a-input v-model:value="basicForm.phone" placeholder="11 位手机号"
                     :maxlength="11" allow-clear />
          </a-form-item>
          <a-form-item label="头像">
            <div class="avatar-upload">
              <a-avatar :src="basicForm.adminAvatar || defaultAvatar" :size="56" />
              <div class="avatar-upload-actions">
                <a-upload :show-upload-list="false" :custom-request="handleAvatarUpload"
                          accept="image/*">
                  <a-button size="small">选择图片</a-button>
                </a-upload>
                <a-button v-if="basicForm.adminAvatar" size="small" danger
                          @click="basicForm.adminAvatar = null">移除</a-button>
              </div>
            </div>
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 4, span: 14 }">
            <a-button type="primary" :loading="savingBasic" @click="saveBasic">保存</a-button>
            <a-button class="margin-left-sm" @click="loadProfile">重置</a-button>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <!-- ============ 修改密码 ============ -->
      <a-tab-pane key="password" tab="修改密码">
        <a-form :model="pwdForm" ref="pwdFormRef" :rules="pwdRules"
                :label-col="{ span: 4 }" :wrapper-col="{ span: 14 }" class="profile-form">
          <a-form-item label="旧密码" name="oldPassword">
            <a-input-password v-model:value="pwdForm.oldPassword" placeholder="请输入当前密码" />
          </a-form-item>
          <a-form-item label="新密码" name="newPassword">
            <a-input-password v-model:value="pwdForm.newPassword" placeholder="6~64 位"
                              :maxlength="64" show-count />
          </a-form-item>
          <a-form-item label="确认新密码" name="confirmPassword">
            <a-input-password v-model:value="pwdForm.confirmPassword" placeholder="再次输入新密码" />
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 4, span: 14 }">
            <a-button type="primary" danger :loading="savingPwd" @click="savePassword">
              确认修改
            </a-button>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <!-- ============ 登录日志 ============ -->
      <a-tab-pane key="logins" tab="登录日志">
        <a-table :data-source="loginLogs" :columns="loginLogColumns" :pagination="false"
                 :row-key="rowKey" size="middle" bordered class="profile-logs"
                 :loading="logsLoading">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'resultCode'">
              <a-tag :color="record.resultCode === 'SUCCESS' ? 'green' : 'red'">
                {{ loginResultLabel(record.resultCode) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'loginType'">
              <a-tag :color="record.loginType === 'LOGOUT' ? 'default' : 'blue'">
                {{ loginTypeLabel(record.loginType) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatDate(record.createdAt) }}
            </template>
          </template>
          <template #emptyText>
            <div style="padding:30px;color:#999">暂无登录记录</div>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getMyProfile, updateMyProfile, updateMyPassword, getMyLoginLogs } from '@/api/profile.js'
import { logout } from '@/api/auth.js'
import { maskPhone } from '@/utils/mask.js'
import axios from '@/api/request.js'

const router = useRouter()
const defaultAvatar = ''

// ============ 资料 ============
const profile = reactive({
  id: null,
  username: '',
  name: '',
  phone: '',
  adminAvatar: '',
  createTime: null,
  roles: [],
})

const activeTab = ref('basic')

// ============ 基本资料 Tab ============
const basicForm = reactive({
  name: '',
  phone: '',
  adminAvatar: '',
})
const basicFormRef = ref(null)
const savingBasic = ref(false)

const basicRules = {
  name: [{ max: 32, message: '姓名最多 32 个字符' }],
  phone: [
    {
      validator(_, value) {
        if (!value) return Promise.resolve()
        if (!/^1[3-9]\d{9}$/.test(value)) {
          return Promise.reject(new Error('手机号格式不正确'))
        }
        return Promise.resolve()
      },
    },
  ],
}

// ============ 修改密码 Tab ============
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const pwdFormRef = ref(null)
const savingPwd = ref(false)

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, max: 64, message: '长度需在 6~64 位之间' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码' },
    ({ getFieldValue }) => ({
      validator(_, value) {
        if (!value) return Promise.reject(new Error('请确认新密码'))
        if (getFieldValue('newPassword') !== value) {
          return Promise.reject(new Error('两次输入的新密码不一致'))
        }
        return Promise.resolve()
      },
    }),
  ],
}

// ============ 登录日志 Tab ============
const loginLogs = ref([])
const logsLoading = ref(false)

const loginLogColumns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
  { title: '类型', dataIndex: 'loginType', key: 'loginType', width: 100 },
  { title: '结果', dataIndex: 'resultCode', key: 'resultCode', width: 90 },
  { title: 'IP 地址', dataIndex: 'ip', width: 150 },
  { title: '设备', dataIndex: 'userAgent', ellipsis: true },
]

// ============ 方法 ============
async function loadProfile() {
  try {
    const res = await getMyProfile()
    // response interceptor 不解包，res.data 是 Result 对象 { success, data: AdminUserVO, ... }
    const result = res.data
    if (!result?.success || !result.data) {
      message.error(result?.message || '加载个人资料失败')
      return
    }
    const data = result.data                                          // AdminUserVO
    Object.assign(profile, {
      id: data.id,
      username: data.username,
      name: data.name,
      phone: data.phone,
      adminAvatar: data.adminAvatar,
      createTime: data.createTime,
      roles: data.roles || [],
    })
    basicForm.name = data.name || ''
    basicForm.phone = data.phone || ''
    basicForm.adminAvatar = data.adminAvatar || ''
    // 把头像写入 sessionStorage，顶栏头像初始值可以直接读
    try { sessionStorage.setItem('admin_profile_avatar', data.adminAvatar || '') } catch {}
  } catch (e) {
    message.error('加载个人资料失败')
  }
}

async function saveBasic() {
  try {
    await basicFormRef.value.validate()
  } catch {
    return
  }
  savingBasic.value = true
  try {
    const payload = {}
    // 只传改过的字段，避免服务端 COALESCE 触发不必要的 UPDATE
    if (basicForm.name !== profile.name) payload.name = basicForm.name
    if (basicForm.phone !== profile.phone) payload.phone = basicForm.phone || null
    if (basicForm.adminAvatar !== profile.adminAvatar) payload.adminAvatar = basicForm.adminAvatar || null
    if (Object.keys(payload).length === 0) {
      message.info('没有需要保存的修改')
      return
    }
    await updateMyProfile(payload)
    message.success('个人资料已更新')
    await loadProfile()
    // 头像变了 → 广播事件带新 URL，App.vue 顶栏立即更新
    if (payload.adminAvatar !== undefined) {
      window.dispatchEvent(new CustomEvent('admin-profile-updated', {
        detail: { adminAvatar: payload.adminAvatar || '' }
      }))
    }
  } catch (e) {
    // request.js 统一弹了错误消息
  } finally {
    savingBasic.value = false
  }
}

async function savePassword() {
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  savingPwd.value = true
  try {
    await updateMyPassword({ ...pwdForm })
    message.success('密码修改成功，请重新登录')
    // 强制登出，后端 invalidateAfterCommit 已使旧 token 失效
    await logout().catch(() => {})
    router.push('/front/login')
  } catch (e) {
    // request.js 统一弹了错误消息
  } finally {
    savingPwd.value = false
  }
}

async function loadLoginLogs() {
  logsLoading.value = true
  try {
    const res = await getMyLoginLogs(10)
    const result = res.data
    loginLogs.value = (result?.success && result.data) ? result.data : []
  } catch {
    loginLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

function handleAvatarUpload({ file }) {
  // 复用后台上传接口，type=avatar 放行只校验登录
  // 后端返回 Result<UploadVO>：{ success, data:{ url }, message, code }
  // response interceptor 不解包，所以 axios 拿到 res.data 是 Result 对象
  const formData = new FormData()
  formData.append('file', file)
  axios.post('/api/admin/upload?type=avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(res => {
    const result = res.data                                    // Result 对象
    if (!result?.success) {
      message.error(result?.message || '上传失败')
      return
    }
    const url = result.data?.url                               // UploadVO.url
    if (url) {
      basicForm.adminAvatar = url
      message.success('头像上传成功')
    } else {
      message.error('上传失败：未返回 URL')
    }
  }).catch(() => {
    message.error('头像上传失败')
  })
}

function loginTypeLabel(type) {
  const map = { LOGIN: '登录', LOGOUT: '登出' }
  return map[type] || type || '—'
}
function loginResultLabel(code) {
  const map = { SUCCESS: '成功', BAD_PASSWORD: '密码错误', NOT_FOUND: '账号不存在',
    DISABLED: '已停用', EXPIRED: 'Token 过期', REVOKED: 'Token 已吊销' }
  return map[code] || code || '—'
}
function formatDate(dt) {
  if (!dt) return '—'
  // 后端返回 ISO 字符串，浏览器直接格式化
  const d = new Date(dt)
  if (isNaN(d.getTime())) return dt
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function rowKey(r) { return r.id }

// Tab 切换时懒加载
async function onTabChange(key) {
  if (key === 'logins' && loginLogs.value.length === 0) {
    await loadLoginLogs()
  }
}

onMounted(async () => {
  await loadProfile()
  loadLoginLogs()
})
</script>

<style scoped>
.profile-page {
  padding: 24px;
  max-width: 880px;
}
.profile-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 28px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  margin-bottom: 24px;
}
.profile-card-info { flex: 1; }
.profile-card-name { font-size: 20px; font-weight: 600; margin-bottom: 6px; }
.profile-card-meta { margin-bottom: 6px; }
.profile-card-sub { color: #888; font-size: 13px; display: flex; gap: 18px; }
.role-tag { font-size: 12px; }
.profile-tabs { background: #fff; padding: 0 24px 24px; border-radius: 8px; border: 1px solid #f0f0f0; }
.profile-tabs :deep(.ant-tabs-nav) { margin: 0 -24px 16px; padding: 0 24px; border-bottom: 1px solid #f0f0f0; }
.profile-form { padding-top: 12px; }
.form-hint { color: #888; font-size: 12px; margin-top: 4px; }
.margin-left-sm { margin-left: 12px; }
.avatar-upload { display: flex; align-items: center; gap: 16px; }
.avatar-upload-actions { display: flex; gap: 8px; }
.profile-logs { margin-top: 8px; }
</style>
