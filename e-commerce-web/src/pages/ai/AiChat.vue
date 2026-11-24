<template>
  <div class="ai-chat">
    <!-- 左侧：会话列表 -->
    <div class="chat-sidebar">
      <a-button type="primary" block v-permission="'ai:chat:use'" @click="createConversation">
        <PlusOutlined /> 新建对话
      </a-button>
      <div class="conversation-list">
        <a-spin v-if="conversationsLoading" style="width: 100%; margin-top: 24px" />
        <a-empty v-else-if="!conversations.length" description="暂无会话" :image-style="{ height: '60px' }" />
        <div
            v-for="c in conversations"
            :key="c.id"
            class="conversation-item"
            :class="{ active: c.id === currentId }"
            @click="selectConversation(c)"
        >
          <span class="conversation-title">{{ c.title || '未命名会话' }}</span>
          <a-popconfirm title="确定删除该会话吗？" @confirm.stop="removeConversation(c)" @click.stop>
            <DeleteOutlined class="conversation-delete" @click.stop />
          </a-popconfirm>
        </div>
      </div>
    </div>

    <!-- 右侧：聊天区 -->
    <div class="chat-main">
      <!-- 顶部：角色选择 -->
      <div class="chat-header">
        <span class="chat-header-label">聊天角色</span>
        <a-select
            v-model:value="currentRoleId"
            :options="roleOptions"
            placeholder="默认角色"
            allow-clear
            style="width: 220px"
            :loading="rolesLoading"
        />
        <span class="chat-header-tip">切换角色后对新会话生效</span>
      </div>

      <!-- 消息列表 -->
      <div class="message-list" ref="messageListRef">
        <!-- 固定欢迎语 + 快捷提问：进入页面即有 AI 存在感，不用先发消息 -->
        <div v-if="!messages.length && !messagesLoading" class="welcome">
          <img class="welcome-avatar" :src="aiAvatar" alt="AI 助手" />
          <div class="welcome-body">
            <p class="welcome-text">您好，我是电商 AI 助手，可以帮您查订单、写文案、推荐商品。</p>
            <div class="welcome-chips">
              <button
                  v-for="q in QUICK_QUESTIONS"
                  :key="q"
                  type="button"
                  class="welcome-chip"
                  :disabled="sending"
                  @click="quickAsk(q)"
              >
                {{ q }}
              </button>
            </div>
          </div>
        </div>
        <a-spin v-if="messagesLoading" style="width: 100%; margin-top: 80px" />
        <div
            v-for="(m, i) in messages"
            :key="i"
            class="message-row"
            :class="m.type === 'user' ? 'is-user' : 'is-assistant'"
        >
          <img
              v-if="m.type !== 'user'"
              class="message-avatar"
              :src="aiAvatar"
              alt="AI 客服"
          />
          <div class="message-bubble">
            <span v-if="m.thinking" class="thinking">
              正在思考<span class="thinking-dots"><i></i><i></i><i></i></span>
              <span class="thinking-time">{{ thinkingSeconds }}s</span>
            </span>
            <template v-else>
              <span v-if="m.thinkSeconds" class="think-done">已思考 {{ m.thinkSeconds }} 秒</span>
              <span class="message-text">{{ m.content }}</span>
              <span v-if="m.streaming" class="streaming-cursor">▍</span>
            </template>
            <!-- 商品卡片 -->
            <div v-if="m.products?.length" class="product-cards">
              <div v-for="p in m.products" :key="p.id" class="product-card">
                <img v-if="p.image" :src="p.image" class="product-image" alt="" />
                <div class="product-info">
                  <div class="product-name">{{ p.name }}</div>
                  <div class="product-meta">
                    <span class="product-price">¥{{ formatPrice(p.price ?? p.salesPrice) }}</span>
                    <span v-if="p.sales != null" class="product-sales">已售 {{ p.sales }}</span>
                  </div>
                  <div class="product-actions">
                    <a-button size="small" type="primary" @click="viewProduct(p)">查看商品</a-button>
                    <a-button size="small" @click="copyProductLink(p)">复制链接</a-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <!-- 用户头像：气泡右侧 -->
          <img
              v-if="m.type === 'user'"
              class="message-avatar message-avatar-user"
              :src="userAvatar"
              alt="我"
          />
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <a-textarea
            v-model:value="input"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            :auto-size="{ minRows: 1, maxRows: 6 }"
            :disabled="sending"
            @keydown="onInputKeydown"
        />
        <a-button
            type="primary"
            v-permission="'ai:chat:use'"
            :loading="sending"
            :disabled="!input.trim()"
            @click="send"
        >
          发送
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { chatStream, listConversations, getMessages, deleteConversation, listRoles } from '@/api/ai.js'
import { getAdminInfo } from '@/api/auth.js'
import aiAvatar from '@/assets/ai-avatar.webp'
import defaultAvatar from '@/assets/avatar/zongjie.webp'

const router = useRouter()

// ====================== 当前用户头像 ======================
const userAvatar = ref(defaultAvatar)
loadUserAvatar()

function loadUserAvatar() {
  getAdminInfo()
      .then(res => {
        const avatar = res.data?.data?.adminAvatar
        if (avatar) userAvatar.value = avatar
      })
      .catch(() => { /* 拉取失败用默认头像 */ })
}

// ====================== 会话列表 ======================
const conversations = ref([])
const conversationsLoading = ref(false)
const currentId = ref(null)

const loadConversations = () => {
  conversationsLoading.value = true
  listConversations()
      .then(res => {
        const list = res.data?.data || []
        // 按时间倒序（updatedAt 优先，其次 createdAt）
        conversations.value = [...list].sort((a, b) =>
            new Date(b.updatedAt || b.createdAt || 0) - new Date(a.updatedAt || a.createdAt || 0))
      })
      .catch(() => { conversations.value = [] })
      .finally(() => { conversationsLoading.value = false })
}

const createConversation = () => {
  if (sending.value) {
    message.warning('正在回复中，请稍后再试')
    return
  }
  currentId.value = null
  messages.value = []
}

const selectConversation = (c) => {
  if (sending.value || c.id === currentId.value) return
  currentId.value = c.id
  loadMessages(c.id)
}

const removeConversation = (c) => {
  deleteConversation(c.id)
      .then(() => {
        message.success('删除成功')
        if (currentId.value === c.id) {
          currentId.value = null
          messages.value = []
        }
        loadConversations()
      })
      .catch(() => {})
}

// ====================== 角色 ======================
const roles = ref([])
const rolesLoading = ref(false)
const currentRoleId = ref(null)
const roleOptions = computed(() =>
    (roles.value || []).map(r => ({ value: r.id, label: r.name })))

const loadRoles = () => {
  rolesLoading.value = true
  listRoles()
      .then(res => { roles.value = res.data?.data || [] })
      .catch(() => { roles.value = [] })
      .finally(() => { rolesLoading.value = false })
}

// ====================== 消息 ======================
const messages = ref([])
const messagesLoading = ref(false)
const messageListRef = ref(null)

// 欢迎区快捷提问：后台运营视角
const QUICK_QUESTIONS = ['今天有哪些待发货订单', '帮我写一段商品副标题', '推荐几款热销商品']

// 点快捷提问：直接以该文本发送
const quickAsk = (text) => {
  if (sending.value) return
  input.value = text
  send()
}

const loadMessages = (id) => {
  messagesLoading.value = true
  getMessages(id)
      .then(res => {
        const list = res.data?.data || []
        // 只展示用户和助手的对话，工具调用记录不直接展示
        messages.value = list
            .filter(m => m.type === 'user' || m.type === 'assistant')
            .map(m => ({ type: m.type, content: m.content || '' }))
        scrollToBottom()
      })
      .catch(() => { messages.value = [] })
      .finally(() => { messagesLoading.value = false })
}

const scrollToBottom = () => {
  nextTick(() => {
    const el = messageListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ====================== 发送（流式） ======================
const input = ref('')
const sending = ref(false)
let abortController = null
let watchdog = null

// 思考中的计时（秒）：首个 token 到达前显示「正在思考 Ns」
const thinkingSeconds = ref(0)
let thinkingTimer = null

function startThinkingTimer() {
  stopThinkingTimer()
  thinkingSeconds.value = 0
  thinkingTimer = setInterval(() => {
    thinkingSeconds.value += 1
  }, 1000)
}

function stopThinkingTimer() {
  if (thinkingTimer) {
    clearInterval(thinkingTimer)
    thinkingTimer = null
  }
}

const send = () => {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  sending.value = true

  messages.value.push({ type: 'user', content: text })
  // 用 reactive 包一层：流式 token 直接改这个对象，才能逐字触发视图更新
  const assistantMsg = reactive({ type: 'assistant', content: '', streaming: true, thinking: true, products: null })
  messages.value.push(assistantMsg)
  scrollToBottom()
  startThinkingTimer()

  abortController = new AbortController()
  // 60 秒看门狗：流挂死时主动中断，避免按钮一直 loading
  watchdog = setTimeout(() => abortController.abort(), 60000)

  const body = { message: text }
  if (currentId.value) body.conversationId = currentId.value
  // 角色只对新会话生效：已有会话沿用原角色
  if (!currentId.value && currentRoleId.value) body.roleId = currentRoleId.value

  const finish = () => {
    sending.value = false
    assistantMsg.streaming = false
    assistantMsg.thinking = false
    stopThinkingTimer()
    clearTimeout(watchdog)
    abortController = null
  }

  // 首个 token / 确认提示到达：结束思考态并记下耗时
  const markThinkDone = () => {
    if (!assistantMsg.thinking) return
    assistantMsg.thinking = false
    assistantMsg.thinkSeconds = thinkingSeconds.value
    stopThinkingTimer()
  }

  chatStream(body, (event) => {
    switch (event.type) {
      case 'meta':
        if (event.conversationId) currentId.value = event.conversationId
        break
      case 'token':
        markThinkDone()
        assistantMsg.content += event.text || ''
        scrollToBottom()
        break
      case 'products':
        assistantMsg.products = event.items || []
        scrollToBottom()
        break
      case 'confirm':
        // 危险操作待确认提示，按普通消息展示
        markThinkDone()
        assistantMsg.content += (assistantMsg.content ? '\n' : '') + (event.text || '')
        scrollToBottom()
        break
      case 'error':
        message.error(event.message || 'AI 回复失败')
        break
      case 'done':
      default:
        break
    }
  }, abortController.signal)
      .catch((e) => {
        if (e?.name === 'AbortError') {
          message.warning('响应超时，已中断')
        }
        // 其余错误已在 api 层提示
      })
      .finally(() => {
        finish()
        // 新会话发完后刷新左侧列表（拿到标题和时间）
        loadConversations()
      })
}

const onInputKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

// ====================== 商品卡片 ======================
const formatPrice = (value) => (value == null ? '-' : Number(value).toFixed(2))

// 后台没有独立商品详情路由，编辑弹窗在商品列表页，通过 editId 参数唤起
const viewProduct = (p) => {
  router.push({ path: '/goods/list', query: { editId: p.id } })
}

const copyProductLink = (p) => {
  navigator.clipboard.writeText(`/product/${p.id}`)
      .then(() => message.success('链接已复制'))
      .catch(() => message.warning('复制失败，请手动复制'))
}

onMounted(() => {
  loadConversations()
  loadRoles()
})

onBeforeUnmount(() => {
  stopThinkingTimer()
})
</script>

<style scoped>
.ai-chat {
  display: flex;
  gap: 16px;
  height: calc(100vh - 170px);
  min-height: 480px;
}

/* 左侧会话列表 */
.chat-sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.conversation-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #595959;
}
.conversation-item:hover {
  background: #f5f5f5;
}
.conversation-item.active {
  background: #e6f4ff;
  color: #1677ff;
}
.conversation-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conversation-delete {
  flex-shrink: 0;
  margin-left: 8px;
  color: #bfbfbf;
  visibility: hidden;
}
.conversation-item:hover .conversation-delete {
  visibility: visible;
}
.conversation-delete:hover {
  color: #ff4d4f;
}

/* 右侧聊天区 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}
.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.chat-header-label {
  color: #595959;
}
.chat-header-tip {
  color: #bfbfbf;
  font-size: 12px;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.message-row {
  display: flex;
}
.message-row.is-user {
  justify-content: flex-end;
}
.message-row.is-assistant {
  justify-content: flex-start;
}
.message-avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  align-self: flex-start;
  margin-right: 8px;
  border-radius: 50%;
  object-fit: cover;
  overflow: hidden;
}
.message-avatar-user {
  margin-right: 0;
  margin-left: 8px;
}
.message-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f5f5f5;
  color: #262626;
  /* 纯文本 + 换行，不渲染 Markdown */
  white-space: pre-wrap;
  word-break: break-word;
}
.message-row.is-user .message-bubble {
  background: #1677ff;
  color: #fff;
}
.streaming-cursor {
  display: inline-block;
  animation: blink 1s step-start infinite;
}

/* ===== 思考中：文案 + 跳动的点 + 秒表 ===== */
.thinking {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #8c8c8c;
  font-size: 13px;
}
.thinking-dots {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.thinking-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  animation: think-bounce 1.2s ease-in-out infinite;
}
.thinking-dots i:nth-child(2) {
  animation-delay: 0.15s;
}
.thinking-dots i:nth-child(3) {
  animation-delay: 0.3s;
}
.thinking-time {
  color: #1677ff;
  font-variant-numeric: tabular-nums;
}
/* 首个 token 到达后回显耗时，DeepSeek 式「已思考 N 秒」 */
.think-done {
  display: block;
  width: fit-content;
  margin-bottom: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f0f5ff;
  color: #8c8c8c;
  font-size: 12px;
}
@keyframes think-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
@keyframes blink {
  50% { opacity: 0; }
}

/* 固定欢迎区 */
.welcome {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  max-width: 560px;
  padding: 0 8px;
}
.welcome-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.welcome-text {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 10px 14px;
  color: #262626;
  margin: 0;
}
.welcome-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.welcome-chip {
  border: 1px solid #d9d9d9;
  background: #fff;
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 13px;
  color: #595959;
  cursor: pointer;
  transition: all 0.2s;
}
.welcome-chip:hover:not(:disabled) {
  border-color: #1677ff;
  color: #1677ff;
}
.welcome-chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* 商品卡片 */
.product-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}
.product-card {
  display: flex;
  gap: 12px;
  padding: 8px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  white-space: normal;
}
.product-image {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}
.product-info {
  flex: 1;
  min-width: 0;
}
.product-name {
  color: #262626;
  font-weight: 500;
  margin-bottom: 4px;
}
.product-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}
.product-price {
  color: #ff4d4f;
  font-weight: 600;
}
.product-sales {
  color: #8c8c8c;
  font-size: 12px;
}
.product-actions {
  display: flex;
  gap: 8px;
}

/* 输入区 */
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}
.chat-input :deep(textarea) {
  flex: 1;
}
</style>
