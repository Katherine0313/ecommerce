import { request } from '../utils/server-api.js'

// 提交意见反馈，对应 POST /api/feedback（需登录，request 封装自动带 user 令牌）
// data: { type: suggestion/product/order/other, content, images: [], contact }
export function submitFeedback(data) {
	return request('/api/feedback', {
		method: 'POST',
		data
	})
}
