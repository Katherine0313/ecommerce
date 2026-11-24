import { BASE_URL, request } from '../utils/server-api.js'
import { getToken } from '../utils/token.js'
import { getAnonymousId } from '../utils/anonymous.js'

const CHAT_PATH = '/api/mall/ai/chat/stream'
const JSON_HEADER = { 'content-type': 'application/json' }

/**
 * 商城客服对话（SSE 流式）。
 * 与商城网页端共用同一套后端：POST /api/mall/ai/chat/stream
 * - 已登录：Authorization 头带用户令牌
 * - 未登录：X-Anonymous-Id 头标识匿名访客（后端按访客维度限流并保存会话）
 *
 * SSE 事件：meta / token / products / confirm / done / error
 * 微信小程序用 wx.request + enableChunked 接收分片；基础库不支持时降级为整段解析。
 *
 * @param {Object} opts
 * @param {string} opts.message        用户输入
 * @param {number} [opts.conversationId] 会话 ID（后端用它保存多轮历史，首轮不传）
 * @param {(meta:Object)=>void} [opts.onMeta] 收到会话信息（含 conversationId）
 * @param {(offer:Object)=>void} [opts.onOffer] 后端建议转人工（handoff_offer）：前端挂确认卡片
 * @param {(items:Array)=>void} [opts.onProducts] 工具查到的商品列表（products）：前端渲染商品卡片
 * @param {(text:string)=>void} [opts.onChunk] 每收到一段增量文本回调
 * @param {(text:string, meta:Object)=>void} [opts.onDone] 流结束回调
 * @param {(err:any)=>void} [opts.onError] 失败回调
 */
export function chatStream({ message, conversationId, onMeta, onOffer, onProducts, onChunk, onDone, onError }) {
	// #ifdef MP-WEIXIN
	// 低版本基础库没有分片能力：直接走整段解析
	const canChunk = (typeof wx.canIUse === 'function' &&
			wx.canIUse('request.object.enableChunked')) ||
		(typeof wx.getSystemInfoSync === 'function' &&
			compareVersion(wx.getSystemInfoSync().SDKVersion, '2.20.0') >= 0)
	if (!canChunk) {
		chatOnce({ message, conversationId, onMeta, onOffer, onProducts, onChunk, onDone, onError })
		return
	}

	let finished = false
	let buffer = new Uint8Array(0)
	let fullText = ''
	let convId = conversationId || null
	// 首字看门狗：多轮工具调用（查库存 → 查商品 → 汇总）可能要 15~20 秒才出首字，
	// 这里给到 45 秒，和 uniapp 请求超时 60 秒、后端总超时 55 秒留出余量
	let firstChunkTimer = null

	const clearFirstChunkTimer = () => {
		if (firstChunkTimer) {
			clearTimeout(firstChunkTimer)
			firstChunkTimer = null
		}
	}

	const finish = () => {
		if (finished) return
		finished = true
		clearFirstChunkTimer()
		onDone && onDone(fullText, { conversationId: convId })
	}
	const fail = (err) => {
		if (finished) return
		finished = true
		clearFirstChunkTimer()
		onError && onError(err)
	}

	const task = wx.request({
		url: BASE_URL + CHAT_PATH,
		method: 'POST',
		header: buildHeader(),
		data: {
			message,
			conversationId: conversationId || undefined,
			// 标识小程序端：后端据此过滤「AI」字样（网页商城不受影响）
			client: 'miniapp'
		},
		enableChunked: true,
		timeout: 60000,
		success: (res) => {
			if (res.statusCode < 200 || res.statusCode >= 300) {
				fail({
					statusCode: res.statusCode,
					message: backendMessage(res.data) || '服务暂时不可用，请稍后再试'
				})
				return
			}
			// 流结束后把缓冲区里剩余的内容也解析掉
			flush(true)
			finish()
		},
		fail: (err) => fail(err)
	})

	if (typeof task.onChunkReceived !== 'function') {
		// 理论上走不到这里（上面已判断 canChunk），兜底防止卡死
		fail({ message: '当前微信版本不支持流式输出' })
		return task
	}

	firstChunkTimer = setTimeout(() => {
		try {
			task.abort()
		} catch (e) {
			// 忽略 abort 异常
		}
		fail({ message: '响应超时，请稍后再试' })
	}, 45000)

	task.onChunkReceived((res) => {
		if (finished) return
		clearFirstChunkTimer()
		appendBytes(new Uint8Array(res.data))
		flush(false)
	})

	// 追加二进制分片
	function appendBytes(bytes) {
		const next = new Uint8Array(buffer.length + bytes.length)
		next.set(buffer, 0)
		next.set(bytes, buffer.length)
		buffer = next
	}

	// 只在完整的空行边界（\n\n）处解码，避免中文多字节被分片截断
	function flush(force) {
		let cut = -1
		for (let i = 0; i < buffer.length - 1; i++) {
			if (buffer[i] === 10 && buffer[i + 1] === 10) {
				cut = i + 2
			}
		}
		if (cut === -1 && !force) {
			return
		}
		const end = cut === -1 ? buffer.length : cut
		const text = utf8Decode(buffer.slice(0, end))
		buffer = buffer.slice(end)
		parseSse(text, handleEvent)
	}

	function handleEvent(ev) {
		if (finished || !ev || !ev.type) return
		if (ev.type === 'meta') {
			if (ev.conversationId) {
				convId = ev.conversationId
				onMeta && onMeta(ev)
			}
		} else if (ev.type === 'handoff_offer') {
			onOffer && onOffer({ conversationId: ev.conversationId || convId, text: ev.text || '' })
		} else if (ev.type === 'products') {
			onProducts && onProducts(ev.items || [])
		} else if (ev.type === 'token') {
			const piece = ev.text || ''
			fullText += piece
			onChunk && onChunk(piece)
		} else if (ev.type === 'error') {
			fail({ message: ev.message || '服务暂时不可用，请稍后再试', code: ev.code })
		}
	}
	// #endif

	// #ifndef MP-WEIXIN
	chatOnce({ message, conversationId, onMeta, onOffer, onProducts, onChunk, onDone, onError })
	// #endif
}

/**
 * 整段解析兜底：H5 / 低版本小程序用。
 * 一次请求拿完整 SSE 文本，再按事件逐条回放，最终一次性给出全文。
 */
function chatOnce({ message, conversationId, onMeta, onOffer, onProducts, onChunk, onDone, onError }) {
	let fullText = ''
	let convId = conversationId || null
	let failed = null

	uni.request({
		url: BASE_URL + CHAT_PATH,
		method: 'POST',
		header: buildHeader(),
		data: {
			message,
			conversationId: conversationId || undefined,
			// 标识小程序端：后端据此过滤「AI」字样（网页商城不受影响）
			client: 'miniapp'
		},
		dataType: 'text',
		timeout: 60000,
		success: (res) => {
			if (res.statusCode < 200 || res.statusCode >= 300) {
				onError && onError({
					statusCode: res.statusCode,
					message: backendMessage(res.data) || '服务暂时不可用，请稍后再试'
				})
				return
			}
			parseSse(String(res.data || ''), (ev) => {
				if (!ev || failed) return
				if (ev.type === 'meta') {
					if (ev.conversationId) {
						convId = ev.conversationId
						onMeta && onMeta(ev)
					}
				} else if (ev.type === 'handoff_offer') {
					onOffer && onOffer({ conversationId: ev.conversationId || convId, text: ev.text || '' })
				} else if (ev.type === 'products') {
					onProducts && onProducts(ev.items || [])
				} else if (ev.type === 'token') {
					const piece = ev.text || ''
					fullText += piece
					onChunk && onChunk(piece)
				} else if (ev.type === 'error') {
					failed = { message: ev.message || '服务暂时不可用，请稍后再试', code: ev.code }
				}
			})
			if (failed) {
				onError && onError(failed)
				return
			}
			onDone && onDone(fullText, { conversationId: convId })
		},
		fail: (err) => onError && onError(err)
	})
}

// 客服接口统一请求头：登录态带令牌，未登录带匿名访客标识
function buildHeader() {
	const header = Object.assign({}, JSON_HEADER, {
		'X-Anonymous-Id': getAnonymousId()
	})
	const token = getToken()
	if (token) {
		header.Authorization = 'Bearer ' + token
	}
	return header
}

// 非 2xx 时尽量透出后端 message，便于定位
function backendMessage(data) {
	if (!data) return ''
	if (typeof data === 'string') {
		try {
			const parsed = JSON.parse(data)
			return parsed.message || parsed.msg || ''
		} catch (e) {
			return ''
		}
	}
	return data.message || data.msg || ''
}

// 解析 SSE：取 data: 行；兼容纯文本块（老接口）
function parseSse(text, onEvent) {
	if (!text) return
	text.split('\n\n').forEach((block) => {
		if (!block) return
		const parts = []
		block.split('\n').forEach((raw) => {
			const line = raw.replace(/\r$/, '')
			if (line.startsWith('data:')) {
				parts.push(line.slice(5).replace(/^ /, ''))
			}
		})
		const data = parts.join('\n')
		if (!data) return
		let ev
		try {
			ev = JSON.parse(data)
		} catch (e) {
			// 半截 JSON（流被截断）直接丢弃，其余按纯文本块兼容老接口
			if (data.trim().charAt(0) === '{') return
			ev = { type: 'token', text: data }
		}
		onEvent(ev)
	})
}

/**
 * 转人工：创建/复用会话并标记人工模式。
 * 之后在人工模式下重复调用本接口，会把 message 存为当前会话的用户消息。
 */
export function handoff(conversationId, message) {
	return request('/api/mall/ai/handoff', {
		method: 'POST',
		header: Object.assign({}, JSON_HEADER, {
			'X-Anonymous-Id': getAnonymousId()
		}),
		data: { conversationId, message }
	})
}

/** 查询单个会话状态（含 humanMode） */
export function getConversation(id) {
	return request(`/api/mall/ai/conversations/${id}`, {
		silent: true,
		header: { 'X-Anonymous-Id': getAnonymousId() }
	})
}

/** 查询会话消息列表 */
export function getMessages(id) {
	return request(`/api/mall/ai/conversations/${id}/messages`, {
		silent: true,
		header: { 'X-Anonymous-Id': getAnonymousId() }
	})
}

// 基础库版本比较：v1 > v2 返回 1，v1 < v2 返回 -1
function compareVersion(v1, v2) {
	const a = String(v1 || '').split('.').map(Number)
	const b = String(v2 || '').split('.').map(Number)
	const len = Math.max(a.length, b.length)
	for (let i = 0; i < len; i++) {
		const x = a[i] || 0
		const y = b[i] || 0
		if (x > y) return 1
		if (x < y) return -1
	}
	return 0
}

// UTF-8 解码（小程序不一定有 TextDecoder；按完整事件解码，不会截断中文）
function utf8Decode(bytes) {
	let out = ''
	for (let i = 0; i < bytes.length;) {
		const b = bytes[i++]
		if (b < 0x80) {
			out += String.fromCharCode(b)
		} else if (b < 0xE0) {
			out += String.fromCharCode(((b & 0x1F) << 6) | (bytes[i++] & 0x3F))
		} else if (b < 0xF0) {
			out += String.fromCharCode(((b & 0x0F) << 12) |
				((bytes[i++] & 0x3F) << 6) |
				(bytes[i++] & 0x3F))
		} else {
			const cp = ((b & 0x07) << 18) |
				((bytes[i++] & 0x3F) << 12) |
				((bytes[i++] & 0x3F) << 6) |
				(bytes[i++] & 0x3F)
			const v = cp - 0x10000
			out += String.fromCharCode(0xD800 + (v >> 10), 0xDC00 + (v & 0x3FF))
		}
	}
	return out
}
