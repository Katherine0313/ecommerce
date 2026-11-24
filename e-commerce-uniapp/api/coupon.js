import { request } from '../utils/server-api.js'

// ===== 优惠券接口 =====
// /api/coupons/** 需登录，调用前走 utils/auth.js 的 ensureLogin。

// 领券中心，对应 GET /api/coupons/center
export function getCouponCenter() {
	return request('/api/coupons/center', { method: 'GET' })
}

// 领取优惠券，对应 POST /api/coupons/{templateId}/claim
export function claimCoupon(templateId) {
	return request('/api/coupons/' + templateId + '/claim', { method: 'POST' })
}

// 我的优惠券，对应 GET /api/coupons/mine?status=AVAILABLE|NOT_STARTED|LOCKED|USED|EXPIRED
// 不传 status 返回该用户全部券（我的优惠券页、我的页数量都用这个口径，本地按状态分页签）
export function getMyCoupons(status) {
	return request('/api/coupons/mine' + (status ? '?status=' + status : ''), { method: 'GET' })
}

// 当前订单可用券，对应 POST /api/coupons/available
// items：[{ productId, quantity }]，返回每张券的 discountAmount / usable / reason
export function getAvailableCoupons(items) {
	return request('/api/coupons/available', {
		method: 'POST',
		data: { items: items }
	})
}
