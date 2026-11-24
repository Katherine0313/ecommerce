<template>
  <a-card :bordered="false" body-style="padding: 0">
    <div class="human-service">
      <!-- 左侧：待接待会话 -->
      <aside class="hs-sidebar">
        <div class="hs-sidebar-header">
          <span class="hs-title">人工客服</span>
          <span class="hs-header-right">
            <a-tag v-if="conversations.length" color="red">{{ conversations.length }} 待接待</a-tag>
            <a-popconfirm
                v-if="conversations.length"
                :title="`将删除当前 ${conversations.length} 个会话及其聊天记录，且无法恢复，确定清空吗？`"
                ok-text="清空"
                cancel-text="取消"
                @confirm="clearAllConversations"
            >
              <a-button size="small" type="text" danger v-permission="'ai:human:use'">清空</a-button>
            </a-popconfirm>
          </span>
        </div>
        <div class="hs-conversation-list">
          <a-empty
              v-if="!conversations.length"
              description="暂无待接待用户"
              :image-style="{ height: '60px' }"
              style="margin-top: 40px"
          />
          <div
              v-for="c in conversations"
              :key="c.id"
              class="hs-conversation"
              :class="{ active: c.id === currentId }"
              @click="selectConversation(c)"
          >
            <img class="hs-avatar" :src="c.userAvatar || defaultAvatar" alt="" />
            <div class="hs-conversation-body">
              <div class="hs-conversation-top">
                <span class="hs-user-name">{{ c.userName }}</span>
                <span class="hs-time">{{ shortTime(c.lastAt || c.updatedAt) }}</span>
              </div>
              <div class="hs-last-message">
                <span v-if="c.lastType === 'assistant'" class="hs-last-prefix">客服：</span>
                {{ c.lastMessage || '暂无消息' }}
              </div>
            </div>
            <a-popconfirm
                title="删除后该会话及其聊天记录将无法恢复，确定删除吗？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="removeConversation(c)"
            >
              <span class="hs-del" @click.stop v-permission="'ai:human:use'">删除</span>
            </a-popconfirm>
          </div>
        </div>
      </aside>

      <!-- 右侧：聊天窗 -->
      <main class="hs-main">
        <template v-if="current">
          <div class="hs-main-header">
            <div class="hs-main-user">
              <img class="hs-avatar" :src="current.userAvatar || defaultAvatar" alt="" />
              <div>
                <div class="hs-user-name">{{ current.userName }}</div>
                <div class="hs-sub">转人工时间：{{ shortTime(current.humanAt) || '-' }}</div>
              </div>
            </div>
            <div class="hs-header-actions">
              <a-tag :color="humanMode ? 'green' : 'default'">
                {{ humanMode ? '人工接待中' : '会话已结束' }}
              </a-tag>
              <a-popconfirm
                  v-if="humanMode"
                  title="结束后用户将继续由 AI 客服接待，确定结束吗？"
                  @confirm="closeConversation"
              >
                <a-button size="small" danger v-permission="'ai:human:use'">结束会话</a-button>
              </a-popconfirm>
            </div>
          </div>

          <div class="hs-message-list" ref="messageListRef">
            <a-spin v-if="messagesLoading" style="width: 100%; margin-top: 60px" />
            <div
                v-for="(m, i) in messages"
                :key="m.id || i"
                class="hs-message-row"
                :class="m.type === 'user' ? 'is-customer' : 'is-service'"
            >
              <img
                  v-if="m.type === 'user'"
                  class="hs-avatar small"
                  :src="current.userAvatar || defaultAvatar"
                  alt=""
              />
              <div class="hs-bubble">
                <span class="hs-text">{{ m.content }}</span>
                <span class="hs-msg-time">{{ shortTime(m.createdAt) }}</span>
              </div>
            </div>
          </div>

          <div class="hs-input">
            <a-textarea
                v-model:value="input"
                placeholder="输入回复内容，Enter 发送，Shift+Enter 换行"
                :auto-size="{ minRows: 1, maxRows: 5 }"
                :disabled="!humanMode || sending"
                @keydown="onInputKeydown"
            />
            <a-button
                type="primary"
                v-permission="'ai:human:use'"
                :loading="sending"
                :disabled="!humanMode || !input.trim()"
                @click="send"
            >
              发送
            </a-button>
          </div>
        </template>

        <a-empty v-else description="从左侧选择一位用户开始接待" style="margin-top: 120px" />
      </main>
    </div>
  </a-card>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  listHumanConversations,
  getHumanMessages,
  replyHuman,
  closeHuman,
  deleteHumanConversation,
} from '@/api/ai.js'
import defaultAvatar from '@/assets/avatar.png'

const conversations = ref([])
const currentId = ref(null)
const messages = ref([])
const messagesLoading = ref(false)
const humanMode = ref(true)
const input = ref('')
const sending = ref(false)
const messageListRef = ref(null)

let listTimer = null
let msgTimer = null
let lastMessageKey = ''

const current = computed(() => conversations.value.find(c => c.id === currentId.value) || null)

const shortTime = (value) => {
  if (!value) return ''
  // 后端时间存的是 UTC（无时区后缀），转成本地时间展示
  const d = new Date(`${String(value).replace(' ', 'T')}Z`)
  if (Number.isNaN(d.getTime())) return String(value).slice(5, 16)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const scrollToBottom = (force = false) => {
  nextTick(() => {
    const el = messageListRef.value
    if (el && (force || el.scrollHeight - el.scrollTop < 240)) {
      el.scrollTop = el.scrollHeight
    }
  })
}

const loadConversations = () => {
  listHumanConversations()
      .then(res => {
        conversations.value = res.data?.data || []
        // 当前会话被结束（其他客服操作）后从列表消失，自动退出
        if (currentId.value && !conversations.value.some(c => c.id === currentId.value)) {
          currentId.value = null
          messages.value = []
          humanMode.value = false
          stopMessagePolling()
        }
      })
      .catch(() => {})
}

const loadMessages = (forceScroll = false) => {
  const id = currentId.value
  if (!id) return
  getHumanMessages(id)
      .then(res => {
        if (id !== currentId.value) return
        const list = res.data?.data || []
        messages.value = list.filter(m => m.type === 'user' || m.type === 'assistant')
        humanMode.value = !!res.data?.humanMode
        const key = `${messages.value.length}:${messages.value[messages.value.length - 1]?.id || ''}`
        if (key !== lastMessageKey) {
          lastMessageKey = key
          scrollToBottom(forceScroll)
        }
      })
      .catch(() => {})
      .finally(() => { messagesLoading.value = false })
}

const selectConversation = (c) => {
  if (c.id === currentId.value) return
  currentId.value = c.id
  messages.value = []
  lastMessageKey = ''
  humanMode.value = true
  messagesLoading.value = true
  loadMessages(true)
  startMessagePolling()
}

const startMessagePolling = () => {
  stopMessagePolling()
  msgTimer = setInterval(() => loadMessages(false), 3000)
}

const stopMessagePolling = () => {
  if (msgTimer) {
    clearInterval(msgTimer)
    msgTimer = null
  }
}

const send = () => {
  const text = input.value.trim()
  if (!text || sending.value || !currentId.value) return
  sending.value = true
  replyHuman(currentId.value, text)
      .then(() => {
        input.value = ''
        loadMessages(true)
        loadConversations()
      })
      .catch(() => {})
      .finally(() => { sending.value = false })
}

const onInputKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

const closeConversation = () => {
  if (!currentId.value) return
  closeHuman(currentId.value)
      .then(() => {
        message.success('会话已结束')
        currentId.value = null
        messages.value = []
        humanMode.value = false
        stopMessagePolling()
        loadConversations()
      })
      .catch(() => {})
}

// 删除单个会话（连同聊天记录）
const removeConversation = (c) => {
  deleteHumanConversation(c.id)
      .then(() => {
        message.success('已删除')
        if (currentId.value === c.id) {
          currentId.value = null
          messages.value = []
          humanMode.value = false
          stopMessagePolling()
        }
        loadConversations()
      })
      .catch(() => {})
}

// 清空当前列表里的所有会话
const clearAllConversations = () => {
  const ids = conversations.value.map(c => c.id)
  if (!ids.length) return
  Promise.all(ids.map(id => deleteHumanConversation(id).catch(() => null)))
      .then(() => {
        message.success('已清空')
        currentId.value = null
        messages.value = []
        humanMode.value = false
        stopMessagePolling()
        loadConversations()
      })
}

onMounted(() => {
  loadConversations()
  listTimer = setInterval(loadConversations, 5000)
})

onBeforeUnmount(() => {
  if (listTimer) clearInterval(listTimer)
  stopMessagePolling()
})
</script>

<style scoped>
.human-service {
  display: flex;
  height: calc(100vh - 170px);
  min-height: 480px;
}

/* 左侧会话列表 */
.hs-sidebar {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #f0f0f0;
}
.hs-sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.hs-title {
  font-weight: 600;
  color: #262626;
}
.hs-header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 会话项右侧的删除入口：默认淡出，鼠标移入才明显，避免误点 */
.hs-del {
  flex-shrink: 0;
  align-self: center;
  padding: 2px 6px;
  font-size: 12px;
  color: #bfbfbf;
  cursor: pointer;
  border-radius: 4px;
}
.hs-conversation:hover .hs-del {
  color: #ff4d4f;
  background: #fff1f0;
}
.hs-conversation-list {
  flex: 1;
  overflow-y: auto;
}
.hs-conversation {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #fafafa;
}
.hs-conversation:hover {
  background: #fafafa;
}
.hs-conversation.active {
  background: #e6f4ff;
}
.hs-conversation-body {
  flex: 1;
  min-width: 0;
}
.hs-conversation-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.hs-user-name {
  font-weight: 500;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-time {
  flex-shrink: 0;
  margin-left: 8px;
  color: #bfbfbf;
  font-size: 12px;
}
.hs-last-message {
  color: #8c8c8c;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-last-prefix {
  color: #1677ff;
}
.hs-avatar {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 50%;
  object-fit: cover;
  background: #f0f0f0;
}
.hs-avatar.small {
  width: 32px;
  height: 32px;
  align-self: flex-start;
  margin-right: 8px;
}

/* 右侧聊天窗 */
.hs-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.hs-main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.hs-main-user {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hs-sub {
  color: #8c8c8c;
  font-size: 12px;
}
.hs-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hs-message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #fafafa;
}
.hs-message-row {
  display: flex;
  align-items: flex-end;
}
.hs-message-row.is-customer {
  justify-content: flex-start;
}
.hs-message-row.is-service {
  justify-content: flex-end;
}
.hs-bubble {
  max-width: 70%;
  padding: 9px 13px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #f0f0f0;
  color: #262626;
  white-space: pre-wrap;
  word-break: break-word;
}
.hs-message-row.is-service .hs-bubble {
  background: #1677ff;
  border-color: #1677ff;
  color: #fff;
}
.hs-text {
  display: block;
}
.hs-msg-time {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: #bfbfbf;
  text-align: right;
}
.hs-message-row.is-service .hs-msg-time {
  color: rgba(255, 255, 255, 0.75);
}
.hs-input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
.hs-input :deep(textarea) {
  flex: 1;
}
</style>
