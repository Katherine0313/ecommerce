import axios from './request.js'
import { message } from 'ant-design-vue'
import { getToken, removeToken } from '@/utils/token'

// ==================== AI 客服（后台管理，/api/admin/ai/**，经网关直转 Python） ====================

const LOGIN_PATH = import.meta.env.BASE_URL + 'front/login'

// 流式接口绕过了 axios 拦截器，401/403 需要在这里按同样的风格处理
function handleStreamAuthError(res, data) {
  const msg = data?.message || res.statusText || ''
  if (res.status === 401) {
    removeToken()
    sessionStorage.setItem('loginMessage', msg || '登录已失效，请重新登录')
    window.location.href = LOGIN_PATH
    throw new Error(msg || '登录已失效')
  }
  if (res.status === 403) {
    message.error(msg || '无权限操作，请联系管理员')
    throw new Error(msg || '无权限操作')
  }
}

/**
 * AI 对话流式请求（SSE）。
 * axios 不支持读流，这里手写 fetch + ReadableStream 逐行解析 `data: {json}` 事件。
 * 事件类型：meta / token / products / confirm / done / error
 *
 * @param {{message: string, conversationId?: number, roleId?: number}} body
 * @param {(event: object) => void} onEvent 每收到一个完整事件回调一次
 * @param {AbortSignal} [signal] 外部中断（60 秒看门狗 / 用户取消）
 */
export async function chatStream(body, onEvent, signal) {
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  let res
  try {
    res = await fetch(`${baseURL}/api/admin/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${getToken() || ''}`,
      },
      body: JSON.stringify(body),
      signal,
    })
  } catch (e) {
    if (e?.name === 'AbortError') throw e
    message.error('网络连接失败，请检查网络后再试')
    throw e
  }

  if (!res.ok) {
    // 非流式错误响应（JSON）
    const data = await res.json().catch(() => null)
    handleStreamAuthError(res, data)
    message.error(data?.message || `请求失败（${res.status}）`)
    throw new Error(data?.message || `请求失败（${res.status}）`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let pendingData = []

  // 空行 = 一个 SSE 事件结束；同一事件可能有多行 data:
  const dispatch = () => {
    if (!pendingData.length) return
    const text = pendingData.join('\n')
    pendingData = []
    try {
      onEvent(JSON.parse(text))
    } catch (e) {
      // 心跳/非 JSON 行直接忽略
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() // 最后一段可能不完整，留给下一轮
    for (const line of lines) {
      if (line === '') {
        dispatch()
      } else if (line.startsWith('data:')) {
        pendingData.push(line.slice(5).trimStart())
      }
    }
  }
  dispatch() // 流末尾可能没有结尾空行，兜底派发
}

// 会话列表（按时间倒序由前端排序）
export function listConversations() {
  return axios.get('/api/admin/ai/conversations')
}

// 会话历史消息
export function getMessages(id) {
  return axios.get(`/api/admin/ai/conversations/${id}/messages`)
}

// 删除会话
export function deleteConversation(id) {
  return axios.delete(`/api/admin/ai/conversations/${id}`)
}

// 聊天角色列表
export function listRoles() {
  return axios.get('/api/admin/ai/roles')
}

// ==================== 知识库管理 ====================

// 知识库列表（分页 + 筛选）
export function listKb(params) {
  return axios.get('/api/admin/ai/kb', { params })
}

// 知识库分类列表
export function listKbCategories(scope) {
  return axios.get('/api/admin/ai/kb/categories', { params: scope ? { scope } : {} })
}

// 新建知识条目
export function createKb(data) {
  return axios.post('/api/admin/ai/kb', data)
}

// 编辑知识条目
export function updateKb(id, data) {
  return axios.put(`/api/admin/ai/kb/${id}`, data)
}

// 删除知识条目
export function deleteKb(id) {
  return axios.delete(`/api/admin/ai/kb/${id}`)
}

// 生成商品文案（非流式；LLM 生成耗时长，单独放宽超时）
// data 二选一：{ productId, scene, requirement? } 或 { product: {name, ...}, scene, requirement? }（新增商品内联模式）
export function generateCopy(data) {
  return axios.post('/api/admin/ai/copy/generate', data, { timeout: 120000 })
}

// 把 AI 生成结果回填到已有商品（白名单：name/subTitle/title/description/image）
export function applyCopy(data) {
  return axios.post('/api/admin/ai/copy/apply', data, { timeout: 30000 })
}

// AI 生图（主图/详情图/海报/Banner；生图耗时长，单独放宽超时）
export function generateImage(data) {
  return axios.post('/api/admin/ai/image/generate', data, { timeout: 180000 })
}

// 知识库 Excel 批量导入：模板下载（blob）/ 预检 / 确认
export function downloadKbTemplate() {
  return axios.get('/api/admin/ai/kb/import/template', { responseType: 'blob', timeout: 30000 })
}

export function previewKbImport(file, scope) {
  const form = new FormData()
  form.append('file', file)
  form.append('scope', scope)
  return axios.post('/api/admin/ai/kb/import/preview', form, { timeout: 60000 })
}

export function confirmKbImport(importFileId) {
  return axios.post('/api/admin/ai/kb/import/confirm', { importFileId }, { timeout: 60000 })
}

// ==================== 人工客服（后台工作台） ====================

// 待处理的人工会话列表
export function listHumanConversations() {
  return axios.get('/api/admin/ai/human/conversations')
}

// 某个会话的消息
export function getHumanMessages(id) {
  return axios.get(`/api/admin/ai/human/conversations/${id}/messages`)
}

// 客服回复
export function replyHuman(id, message) {
  return axios.post(`/api/admin/ai/human/conversations/${id}/reply`, { message })
}

// 结束会话（交回 AI）
export function closeHuman(id) {
  return axios.post(`/api/admin/ai/human/conversations/${id}/close`)
}

// 删除人工会话（连同聊天记录，不可恢复）
export function deleteHumanConversation(id) {
  return axios.delete(`/api/admin/ai/human/conversations/${id}`)
}
