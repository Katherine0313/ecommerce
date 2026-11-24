import {
	defineStore
} from 'pinia'
import {
	matchAutoReply,
	matchHumanIntent,
	DEFAULT_REPLY
} from '../utils/auto-reply.js'
import {
	chatStream,
	handoff,
	getConversation,
	getMessages
} from '../api/agent.js'

let msgId = 0

const greeting = () => [{
	id: ++msgId,
	role: 'bot',
	text: '你好，我是客服助手，有什么可以帮你？'
}]

const newSessionId = () =>
	'float-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)

const HUMAN_POLL_INTERVAL = 3000

// 会话缓存：小程序被系统回收/重新进入后，当天会话还能接着看
const CACHE_KEY = 'aiChat:session'
const CACHE_MAX_MESSAGES = 60

// 本地日期串，用于判断缓存是否还是「当天」
const dayKey = (ts) => {
	const d = new Date(ts)
	return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate()
}

const loadCache = () => {
	try {
		const raw = uni.getStorageSync(CACHE_KEY)
		if (!raw) return null
		const data = typeof raw === 'string' ? JSON.parse(raw) : raw
		if (!data || !data.savedAt || !Array.isArray(data.messages) || !data.messages.length) {
			return null
		}
		// 只恢复当天的会话，隔天自动作废
		if (dayKey(data.savedAt) !== dayKey(Date.now())) return null
		return data
	} catch (e) {
		return null
	}
}

/**
 * 全局客服对话状态：悬浮球和「专属客服」页共用，
 * 放在 Pinia 里，切换页面不会丢消息。
 *
 * 回答走后端客服（/api/mall/ai/chat/stream 流式，与商城网页端同一套），
 * 接口不可用时才用本地规则兜底；「人工客服」转接后端人工会话。
 */
export const useAiChatStore = defineStore('aiChat', {
	state: () => {
		const cache = loadCache()
		if (cache) {
			// 用当前时间戳做自增起点，避免新消息 id 和缓存里的 local-N 撞号
			msgId = Date.now()
		}
		return {
			visible: false, // 聊天面板是否打开
			sessionId: cache ? cache.sessionId || '' : '',
			sending: false,
			messages: cache ? cache.messages : greeting(),
			// 悬浮球位置（px）：null 表示还没拖过，由组件按右侧中间初始化
			ballLeft: null,
			ballTop: null,
			// 人工客服相关
			conversationId: cache ? cache.conversationId || null : null,
			humanMode: cache ? !!cache.humanMode : false,
			humanTimer: null,
			// 缓存签名：内容没变就不重复写 storage
			cacheSig: ''
		}
	},
	actions: {
		ensureSession() {
			if (!this.sessionId) {
				this.sessionId = newSessionId()
			}
		},
		// ===== 本地缓存（只保留当天） =====
		persist() {
			try {
				const msgs = this.messages
					.filter(m => !m.typing && (m.text || m.contact))
					.slice(-CACHE_MAX_MESSAGES)
				if (!msgs.length) return
				const last = msgs[msgs.length - 1]
				const sig = [
					this.conversationId,
					this.humanMode ? 1 : 0,
					msgs.length,
					last.id,
					(last.text || '').length
				].join('|')
				if (sig === this.cacheSig) return
				this.cacheSig = sig
				uni.setStorageSync(CACHE_KEY, JSON.stringify({
					savedAt: Date.now(),
					sessionId: this.sessionId,
					conversationId: this.conversationId,
					humanMode: this.humanMode,
					messages: msgs
				}))
			} catch (e) {
				// 存储失败不影响聊天
			}
		},
		clearCache() {
			try {
				this.cacheSig = ''
				uni.removeStorageSync(CACHE_KEY)
			} catch (e) {}
		},
		// 回到前台/重新进入页面：人工会话续上轮询，并从服务端拉一次最新消息
		async resumeSession() {
			this.ensureSession()
			if (!this.conversationId) return
			if (this.humanMode && !this.humanTimer) {
				await this.refreshHuman()
				this.startHumanPolling()
			}
		},
		// 记住悬浮球位置，切页面不回到默认位置
		setBallPos(left, top) {
			this.ballLeft = left
			this.ballTop = top
		},
		open() {
			this.ensureSession()
			this.visible = true
		},
		close() {
			this.visible = false
		},
		// 新对话：换 sessionId + 清空消息
		reset() {
			this.stopHumanPolling()
			this.clearCache()
			this.sessionId = newSessionId()
			this.conversationId = null
			this.humanMode = false
			this.messages = greeting()
			this.sending = false
		},

		// ===== 人工客服 =====
		startHumanPolling() {
			this.stopHumanPolling()
			this.humanTimer = setInterval(() => this.refreshHuman(), HUMAN_POLL_INTERVAL)
		},
		stopHumanPolling() {
			if (this.humanTimer) {
				clearInterval(this.humanTimer)
				this.humanTimer = null
			}
		},
		async refreshHuman() {
			if (!this.conversationId) return
			const id = this.conversationId
			try {
				const [msgRes, convRes] = await Promise.all([
					getMessages(id),
					getConversation(id)
				])
				if (id !== this.conversationId) return

				const list = (msgRes || []).filter(m => m.type === 'user' || m.type === 'assistant')
				// 接口偶发返回空列表时不覆盖已有历史，避免界面被清空
				if (list.length) {
					this.messages = list.map(m => ({
						id: m.id,
						role: m.type === 'user' ? 'user' : 'bot',
						text: m.content || '',
						// 后台客服回复带管理员头像，自动答复没有，据此区分标识
						human: m.type === 'assistant' && !!m.senderAvatar
					}))
				}

				// 客服结束会话后切回自动答复
				if (convRes && !convRes.humanMode && this.humanMode) {
					this.humanMode = false
					this.stopHumanPolling()
					this.messages.push({
						id: 'local-close',
						role: 'bot',
						text: '人工客服已结束本次会话，如有其他问题可以继续咨询客服助手。',
						human: true
					})
				}
				this.persist()
			} catch (e) {
				// 轮询失败静默，下一轮重试
			}
		},
		async handoffToHuman(content) {
			if (this.sending) return false
			this.sending = true
			try {
				const res = await handoff(this.conversationId || undefined, content || '')
				if (res.conversationId) {
					this.conversationId = res.conversationId
					this.humanMode = true
				}
				await this.refreshHuman()
				this.startHumanPolling()
				this.persist()
				return true
			} catch (e) {
				this.messages.push({
					id: 'local-handoff-err',
					role: 'bot',
					text: '转人工失败，请检查网络后重试',
					human: true
				})
				return false
			} finally {
				this.sending = false
			}
		},
		async sendHumanMessage(content) {
			if (this.sending) return false
			this.sending = true
			try {
				await handoff(this.conversationId, content)
				await this.refreshHuman()
				return true
			} catch (e) {
				return false
			} finally {
				this.sending = false
			}
		},
		/**
		 * 发送一条消息：走后台客服（流式），失败/超时才用本地规则兜底。
		 * 命中「人工客服」意图时转接后台人工会话，之后消息直达人工客服。
		 */
		async send(text) {
			const content = String(text || '').trim()
			if (!content || this.sending) {
				return false
			}
			this.ensureSession()

			// 已经在人工模式：直接走人工通道发消息
			if (this.humanMode) {
				this.messages.push({
					id: 'local-' + (++msgId),
					role: 'user',
					text: content
				})
				const ok = await this.sendHumanMessage(content)
				this.persist()
				return ok
			}

			// 命中「人工客服」意图：先出确认卡片，点卡片再接人工
			const human = matchHumanIntent(content)
			if (human) {
				this.messages.push({
					id: 'local-' + (++msgId),
					role: 'user',
					text: content,
				})
				this.messages.push({
					id: 'local-' + (++msgId),
					role: 'bot',
					text: '需要人工客服协助的话，点击下方「联系人工客服」即可接入，工作时间 9:00~18:00。',
					contact: true,
					handoffText: content,
				})
				this.persist()
				return true
			}

			this.sending = true
			this.messages.push({
				id: 'local-' + (++msgId),
				role: 'user',
				text: content
			})
			// 先占位一个「正在查询」，收到增量再逐字填
			this.messages.push({
				id: 'local-' + (++msgId),
				role: 'bot',
				text: '',
				typing: true
			})
			const idx = this.messages.length - 1

			try {
				await new Promise((resolve, reject) => {
					chatStream({
						message: content,
						conversationId: this.conversationId || undefined,
						onMeta: (meta) => {
							if (meta && meta.conversationId) {
								this.conversationId = meta.conversationId
							}
						},
						onOffer: (offer) => {
							if (offer && offer.conversationId) {
								this.conversationId = offer.conversationId
							}
							const msg = this.messages[idx]
							if (msg) {
								msg.typing = false
								// 复用当前气泡挂确认卡片；用户消息后端已落库，点击时无需再传
								msg.contact = true
								msg.handoffText = ''
							}
						},
						onProducts: (items) => {
							const msg = this.messages[idx]
							if (msg && items && items.length) {
								// 挂在当前气泡上，渲染成可点的商品卡片
								msg.products = items
							}
						},
						onChunk: (piece) => {
							const msg = this.messages[idx]
							if (!msg) return
							if (msg.typing) {
								msg.typing = false
								msg.text = ''
							}
							msg.text += piece
						},
						onDone: resolve,
						onError: reject
					})
				})
				const msg = this.messages[idx]
				if (msg) {
					msg.typing = false
					if (!msg.text) msg.text = DEFAULT_REPLY.text
					msg.ai = true // 展示「自动答复」标识
				}
			} catch (e) {
				// 后端不可用：本地规则兜底，保证用户总能收到一句回复
				const reply = matchAutoReply(content) || {
					...DEFAULT_REPLY
				}
				const msg = this.messages[idx]
				if (msg) {
					msg.typing = false
					msg.ai = true
					if (!msg.text) {
						msg.text = reply.text
						msg.contact = !!reply.contact
						msg.feedback = !!reply.feedback
					}
				}
			} finally {
				this.sending = false
			}
			this.persist()
			return true
		}
	}
})
