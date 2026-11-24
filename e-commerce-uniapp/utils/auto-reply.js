/**
 * 客服本地兜底规则表。
 * 正常情况由后端知识库回答；这里只在接口失败/超时时兜底。
 * 例外：「人工客服」必须本地拦截，直接转后端人工客服会话。
 */

// 匹配前统一处理：去空白、转小写（中文不受影响）
const normalize = (input) => String(input || '').replace(/\s+/g, '').toLowerCase()

const RULES = [
	{
		keywords: ['发货', '什么时候发', '几天到', '多久到', '什么时候到'],
		reply: '付款后 48 小时内发货，一般 2~4 天送达。可在「我的 → 物流查询」查看物流。'
	},
	{
		keywords: ['退货', '退款', '换货', '七天无理由', '7天无理由'],
		reply: '支持 7 天无理由退换（商品需不影响二次销售）。可在订单详情里点「申请售后」。'
	},
	{
		keywords: ['运费', '邮费', '包邮'],
		reply: '全场包邮，不额外收取运费。'
	},
	{
		keywords: ['订单', '物流', '快递', '到哪', '查单'],
		reply: '订单和物流可以在「我的 → 物流查询」或订单列表里查看。'
	},
	{
		keywords: ['优惠', '优惠券', '活动', '打折'],
		reply: '优惠券和活动正在接入中，可以留意首页活动区。'
	},
	{
		keywords: ['人工', '人工客服', '转人工', '真人'],
		reply: '当前是常见问题自动答复，需要人工处理请点击下方「联系人工客服」。',
		contact: true,
		// 标记为特殊意图：页面必须本地拦截，不能交给知识库
		intent: 'human'
	}
]

// 网络异常/超时时的兜底：说明是没收到回复，而不是「不知道答案」，避免误导用户
export const DEFAULT_REPLY = {
	text: '抱歉，这次没有收到完整回复（网络或服务较慢），请再发一次；如果多次失败，可以点击下方「联系人工客服」。',
	contact: true,
	feedback: true
}

/**
 * 只匹配本地规则，未命中返回 null（交给后端知识库）。
 * @returns {{ text: string, contact?: boolean, feedback?: boolean } | null}
 */
export function matchAutoReply(input) {
	const text = normalize(input)
	if (!text) {
		return null
	}
	const rule = RULES.find(r => r.keywords.some(k => text.includes(normalize(k))))
	if (!rule) {
		return null
	}
	return {
		text: rule.reply,
		contact: !!rule.contact,
		feedback: !!rule.feedback,
		intent: rule.intent
	}
}

/**
 * 只匹配「转人工」意图：命中返回规则，否则 null。
 * 页面用它做本地拦截，其余消息（含人工客服时间/收费等信息类问题）交给后端知识库。
 */
const HANDOFF_INFO = /(几点|时间|上班|下班|电话|在哪|怎么联系|如何联系|能做什么|是谁|收费|机器人|真人吗)/
const BARE_HANDOFF = [
	'人工', '人工客服', '人工服务', '真人', '真人客服', '客服',
	'转人工', '转接人工', '要人工', '找人工', '叫人工', '请人工', '我要人工', '我要转人工'
]
const HANDOFF_TEXT = /(转人工|转接人工|找人工|要人工|叫人工|请人工|人工服务|真人客服|人工客服)/

export function matchHumanIntent(input) {
	const raw = String(input || '').trim()
	if (!raw || raw.length > 30 || HANDOFF_INFO.test(raw)) {
		return null
	}
	const text = normalize(raw)
	if (!BARE_HANDOFF.includes(text) && !HANDOFF_TEXT.test(text)) {
		return null
	}
	const rule = matchAutoReply(raw)
	return rule && rule.intent === 'human' ? rule : null
}

/**
 * 纯规则回复（规则未命中时返回兜底），后端不可用时用。
 */
export function getAutoReply(input) {
	return matchAutoReply(input) || { ...DEFAULT_REPLY }
}
