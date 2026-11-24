<template>
  <div>
    <div class="head">
      <a-form layout="inline">
        <a-form-item>
          <a-input v-model:value="query.username" placeholder="用户名" allow-clear @pressEnter="load" />
        </a-form-item>
        <a-form-item>
          <a-input v-model:value="query.ip" placeholder="IP（脱敏后文本）" allow-clear @pressEnter="load" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="loading" @click="load">查询</a-button>
        </a-form-item>
      </a-form>
      <span class="tip">当前在线会话 {{ total }} 个 · IP 已脱敏</span>
    </div>

    <a-table :dataSource="list" :columns="columns" :pagination="false" row-key="sid" size="middle"
             :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'lastActive'">
          {{ record.lastActive || '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-popconfirm
              title="强制下线会让该账号全部会话立即失效（旧 token 直接 401），确定继续？"
              ok-text="确定下线"
              ok-type="danger"
              @confirm="handleForceLogout(record)"
          >
            <a-button v-permission="'monitor:online:logout'" type="link" size="small" danger>强制下线</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { forceLogout, getOnlineUsers } from '@/api/monitor.js'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = ref({ username: '', ip: '' })

const columns = [
  { title: '账号 ID', dataIndex: 'adminId', key: 'adminId' },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '角色', dataIndex: 'roles', key: 'roles' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: '登录时间', dataIndex: 'loginTime', key: 'loginTime' },
  { title: '最后活跃', key: 'lastActive' },
  { title: 'User-Agent', dataIndex: 'userAgent', key: 'userAgent' },
  { title: '操作', key: 'action' },
]

function load() {
  loading.value = true
  getOnlineUsers({
    username: query.value.username || undefined,
    ip: query.value.ip || undefined,
  })
    .then((res) => {
      const data = res.data.data || {}
      list.value = data.list || []
      total.value = data.total || 0
    })
    .catch(() => message.error('获取在线用户失败'))
    .finally(() => {
      loading.value = false
    })
}

function handleForceLogout(record) {
  forceLogout(record.adminId)
    .then((res) => {
      message.success(`已下线，清理会话 ${res.data.data || 0} 个`)
      load()
    })
    .catch(() => message.error('强制下线失败'))
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.tip {
  color: #999;
  font-size: 13px;
}
</style>
