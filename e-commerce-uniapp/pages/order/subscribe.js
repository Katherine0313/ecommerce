// 微信一次性订阅消息：订单未支付提醒
// 字段：thing1 商品名称 / character_string5 订单编号 / amount7 订单金额 / time8 订单失效时间
export const UNPAID_SUBSCRIBE_TEMPLATE_ID = '_81DMBgBaFGYXqh9Cb6X41-32EfTF2zpqdSqFPXjzOg'

/**
 * 在用户点击事件中唤起一次性订阅授权。
 * <p>
 * 注意：必须由用户点击触发（不能在 onLoad/onShow 里调用），否则微信会拒绝。
 * 用户拒绝、基础库过低或环境异常时统一返回 false，不抛出异常，避免阻塞下单。
 *
 * @returns {Promise<boolean>} 是否同意订阅（只认 accept）
 */
export function requestUnpaidSubscribe() {
	// 基础库过低时没有这个 API，直接按未授权处理，不能让它阻塞下单
	if (typeof uni.requestSubscribeMessage !== 'function') {
		return Promise.resolve(false)
	}
	try {
		return uni.requestSubscribeMessage({
			tmplIds: [UNPAID_SUBSCRIBE_TEMPLATE_ID]
		}).then((res) => {
			// res[tmplId] 可能是 accept / reject / ban / filter
			return res[UNPAID_SUBSCRIBE_TEMPLATE_ID] === 'accept'
		}).catch(() => {
			return false
		})
	} catch (e) {
		// 同步异常（极低版本/异常环境）同样不阻塞下单
		return Promise.resolve(false)
	}
}
