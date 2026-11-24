// ==================== 来单提醒 WebSocket ====================
// 后台管理端登录后自动连 /ws/order，支付成功时后端推「新订单提醒」，
// 前端收到后弹 notification + SpeechSynthesis 语音播报。
// 断线自动重连（最多 10 次，避免 token 失效后死循环），登出时主动断开。

import { ref } from 'vue'
import { getToken } from '@/utils/token'

let ws = null
let reconnectTimer = null
let reconnectCount = 0
let manuallyClosed = false
let heartbeatTimer = null
let reconnectDelay = 3000
const MAX_RECONNECT_DELAY = 30000
const HEARTBEAT_INTERVAL = 25000

// 未读新订单数：WebSocket 收到一条 +1，用户点开顶栏铃铛查看后清零（见 clearUnreadOrders）
export const unreadOrderCount = ref(0)

// 新订单事件计数：工作台等页面 watch 它，收到推送后自动刷新统计，无需手动刷新页面
export const orderNotifyTick = ref(0)

/** 建立连接（已登录才有 token，没 token 直接返回） */
export function connectOrderNotify() {
  const token = getToken()
  if (!token) return
  // 已有可用连接（readyState：0=连接中、1=已连接）就不重复建；
  // 只判 ws 非空会在断线重连/HMR 后残留多个连接，后端遍历连接逐个推送，连接一多同一条消息就弹多次
  if (ws && ws.readyState < 2) {
    return
  }
  // 清理已关闭（2=关闭中、3=已关闭）的旧连接引用
  ws = null
  manuallyClosed = false
  // WebSocket 后端在网关，与 axios 的 VITE_API_BASE_URL 同地址，不能沿用 window.location.host：
  // 开发时前端跑在 vite(5173)，window.location.host 是 5173 而非网关 8080，直接连会握手失败、收不到提醒。
  const apiBase = import.meta.env.VITE_API_BASE_URL || window.location.origin
  const proto = apiBase.startsWith('https:') ? 'wss:' : 'ws:'
  const host = apiBase.replace(/^https?:\/\//, '')
  const url = `${proto}//${host}/ws/order?token=${token}`
  // 用局部变量持有本次连接，避免旧连接的 onclose 延迟触发时把新连接的 ws 引用误清
  const socket = new WebSocket(url)
  ws = socket
  // 握手卡住超过 10 秒就主动关闭重连，避免一直停在 CONNECTING
  const connectTimer = setTimeout(() => {
    if (socket.readyState === WebSocket.CONNECTING) {
      socket.close()
    }
  }, 10000)
  socket.onopen = () => {
    clearTimeout(connectTimer)
    const wasReconnect = reconnectCount > 0
    reconnectCount = 0
    reconnectDelay = 3000
    startHeartbeat()
    // 重连成功补一次对账：断线期间漏掉的推送不会弹窗，但工作台统计能立即刷新
    if (wasReconnect) orderNotifyTick.value += 1
    console.log('来单提醒连接成功')
  }
  socket.onmessage = (event) => handleMessage(event.data)
  socket.onclose = () => {
    clearTimeout(connectTimer)
    stopHeartbeat()
    // 只有当前 ws 还是这个 socket 时才清空
    if (ws === socket) {
      ws = null
    }
    clearTimeout(reconnectTimer)
    // 主动断开或已登出（token 被清）才停止重连；其余情况一直按退避重连（最长 30s）
    if (manuallyClosed) return
    if (!getToken()) {
      manuallyClosed = true
      return
    }
    reconnectCount += 1
    reconnectTimer = setTimeout(connectOrderNotify, reconnectDelay)
    reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY)
  }
  socket.onerror = () => {
    // onerror 后通常会触发 onclose，交给 onclose 统一处理重连
  }
}

/** 心跳：定时发 ping，既保持连接活跃，也能尽早发现半开连接 */
function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      try {
        ws.send(JSON.stringify({ type: 'ping' }))
      } catch (e) {
        // 发送失败交给 onclose 重连
      }
    }
  }, HEARTBEAT_INTERVAL)
}

function stopHeartbeat() {
  clearInterval(heartbeatTimer)
  heartbeatTimer = null
}

/** 页面回到前台 / 网络恢复时，连接已断就立即重连（定时器在后台标签页会被节流） */
function reconnectIfClosed() {
  if (manuallyClosed) return
  if (!ws || ws.readyState >= 2) {
    clearTimeout(reconnectTimer)
    connectOrderNotify()
  }
}

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') reconnectIfClosed()
  })
  window.addEventListener('online', reconnectIfClosed)
}

/** 断开连接（登出用），不再自动重连 */
export function disconnectOrderNotify() {
  manuallyClosed = true
  stopHeartbeat()
  clearTimeout(reconnectTimer)
  if (ws) {
    ws.close()
    ws = null
  }
}

/** 清空未读新订单计数（顶栏铃铛点开查看后调用） */
export function clearUnreadOrders() {
  unreadOrderCount.value = 0
}

/** 处理推送消息：只处理 type=1（新订单提醒） */
function handleMessage(data) {
  let msg
  try {
    msg = JSON.parse(data)
  } catch (e) {
    return
  }
  if (msg.type !== 1) return
  unreadOrderCount.value += 1
  // 通知监听方（工作台）刷新统计
  orderNotifyTick.value += 1
  // 来单提醒：antd notification 在本框架下渲染不出来，改用纯 DOM 实现右上角通知
  showOrderNotification(msg.orderId)
  // 语音播报
  speakNotice(msg.content)
}

// ==================== 语音播报 ====================
let zhVoice = null
let speechUnlocked = false

/** 缓存中文语音（getVoices 首次可能为空，voiceschanged 后才有） */
function loadVoices() {
  if (!window.speechSynthesis) return
  const voices = window.speechSynthesis.getVoices() || []
  zhVoice =
    voices.find((v) => v.lang && v.lang.toLowerCase().startsWith('zh')) ||
    null
}

/**
 * 浏览器自动播放策略：页面刷新后没有用户交互时 speechSynthesis 会被静默拒绝，
 * 表现就是「弹窗有、没声音」，重新登录（点击过页面）后才恢复。
 * 首次点击/按键时播一条静音语音预热，把语音引擎解锁。
 */
function unlockSpeech() {
  if (speechUnlocked || !window.speechSynthesis) return
  speechUnlocked = true
  try {
    window.speechSynthesis.resume()
    const utterance = new SpeechSynthesisUtterance(' ')
    utterance.volume = 0
    window.speechSynthesis.speak(utterance)
  } catch (e) {
    // 忽略：浏览器不支持就只保留弹窗
  }
}

if (typeof window !== 'undefined' && window.speechSynthesis) {
  loadVoices()
  window.speechSynthesis.onvoiceschanged = loadVoices
  document.addEventListener('pointerdown', unlockSpeech, {
    passive: true,
    once: true,
  })
  document.addEventListener('keydown', unlockSpeech, { once: true })
}

/** 语音播报：优先选中文语音，失败静默忽略 */
function speakNotice(text) {
  if (!window.speechSynthesis) return
  try {
    if (window.speechSynthesis.paused) {
      window.speechSynthesis.resume()
    }
    // 清空上一条，避免多条提醒排队/卡住
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.volume = 1
    if (zhVoice) {
      utterance.voice = zhVoice
    }
    // Chrome 在 cancel() 后立刻 speak() 偶尔会吞掉，延迟一点更稳
    setTimeout(() => window.speechSynthesis.speak(utterance), 60)
  } catch (e) {
    console.warn('语音播报失败', e)
  }
}

// ==================== 右上角通知（纯 DOM 实现） ====================
// antd 的 notification 在本项目（stepin 框架 + 按需加载）下渲染不出来，改用纯 DOM 实现右上角通知。
// 通知卡片样式仿 antd notification：白底圆角阴影，右上角固定，多条纵向堆叠。
let notifyContainer = null

function showOrderNotification(orderId) {
  // 右上角固定容器：flex 纵向堆叠多条通知
  if (!notifyContainer) {
    notifyContainer = document.createElement('div')
    notifyContainer.style.cssText =
      'position:fixed;top:78px;right:24px;z-index:9999;display:flex;flex-direction:column;gap:12px;'
    document.body.appendChild(notifyContainer)
  }

  // 单条通知卡片
  const wrap = document.createElement('div')
  wrap.style.cssText = [
    'width:350px',
    'padding:16px',
    'background:#fff',
    'border-radius:8px',
    'box-shadow:0 6px 16px 0 rgba(0,0,0,.08),0 3px 6px -4px rgba(0,0,0,.12),0 9px 28px 8px rgba(0,0,0,.05)',
    'display:flex',
    'align-items:flex-start',
    'gap:12px',
    'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif',
  ].join(';')

  // 绿色对勾圆
  const icon = document.createElement('div')
  icon.style.cssText =
    'flex-shrink:0;width:20px;height:20px;border-radius:50%;background:#52c41a;color:#fff;font-size:14px;line-height:20px;text-align:center;'
  icon.textContent = '✓'

  // 正文：标题 + 描述
  const body = document.createElement('div')
  body.style.cssText = 'flex:1;min-width:0;'
  const title = document.createElement('div')
  title.style.cssText = 'font-size:16px;font-weight:600;color:rgba(0,0,0,.88);line-height:1.5;'
  title.textContent = '新订单提醒'
  const desc = document.createElement('div')
  desc.style.cssText = 'margin-top:4px;font-size:14px;color:rgba(0,0,0,.45);line-height:1.5;word-break:break-all;'
  desc.textContent = '订单号：' + orderId
  body.appendChild(title)
  body.appendChild(desc)

  // 关闭按钮
  const close = document.createElement('div')
  close.style.cssText = 'flex-shrink:0;cursor:pointer;font-size:16px;color:rgba(0,0,0,.45);line-height:1;'
  close.textContent = '×'
  close.onclick = () => {
    wrap.remove()
    if (notifyContainer && notifyContainer.childElementCount === 0) {
      notifyContainer.remove()
      notifyContainer = null
    }
  }

  wrap.appendChild(icon)
  wrap.appendChild(body)
  wrap.appendChild(close)
  notifyContainer.appendChild(wrap)
}
