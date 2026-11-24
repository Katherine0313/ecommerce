import { request } from '../utils/server-api.js'

// ===== 优惠券秒杀接口 =====
// 基础路径 /api/coupons/seckill；列表 / 详情免登录，下单 / 支付 / 取消 / 退款 / 查订单需登录。
// 调用需登录的方法前，页面先走 utils/auth.js 的 ensureLogin()。
// request 已统一解包后端 Result，直接返回 data（分页接口返回 { list, total }）。

// 秒杀券列表（分页），对应 GET /api/coupons/seckill?page=&pageSize=
// 返回分页对象 { list, total, page, pageSize }
export function getSeckillList(page = 1, pageSize = 10) {
	return request('/api/coupons/seckill?page=' + page + '&pageSize=' + pageSize, { method: 'GET' })
}

// 秒杀券详情，对应 GET /api/coupons/seckill/{couponId}
export function getSeckillDetail(couponId) {
	return request('/api/coupons/seckill/' + couponId, { method: 'GET' })
}

// 创建秒杀订单（扣库存、生成待支付订单），对应 POST /api/coupons/seckill/{couponId}/orders，body 为空
export function createSeckillOrder(couponId) {
	return request('/api/coupons/seckill/' + couponId + '/orders', { method: 'POST' })
}

// 查询自己的秒杀订单，对应 GET /api/coupons/seckill/orders/{orderNo}
export function getSeckillOrder(orderNo) {
	return request('/api/coupons/seckill/orders/' + orderNo, { method: 'GET' })
}

// 我的秒杀订单列表（分页），对应 GET /api/coupons/seckill/orders?page=&pageSize=&status=
// status：1 待支付 / 2 已支付 / 3 已取消 / 4 已退款；不传返回全部
export function getMySeckillOrders(page = 1, pageSize = 10, status) {
	const url = '/api/coupons/seckill/orders?page=' + page + '&pageSize=' + pageSize
		+ (status === null || status === undefined ? '' : '&status=' + status)
	return request(url, { method: 'GET' })
}

// 取消待支付订单（回补库存），对应 POST /api/coupons/seckill/orders/{orderNo}/cancel
export function cancelSeckillOrder(orderNo) {
	return request('/api/coupons/seckill/orders/' + orderNo + '/cancel', { method: 'POST' })
}

// 支付订单（v1 模拟支付），对应 POST /api/coupons/seckill/orders/{orderNo}/pay，body { payType }
// 后端直接改状态并发券，payType 仅记录，传 'MOCK' 对齐后端 SeckillPayDTO 注释。
export function paySeckillOrder(orderNo, payType = 'MOCK') {
	return request('/api/coupons/seckill/orders/' + orderNo + '/pay', {
		method: 'POST',
		data: { payType: payType }
	})
}

// 申请退款，对应 POST /api/coupons/seckill/orders/{orderNo}/refund，body { reason, images }
// images 为逗号分隔的 URL 字符串，可为空（凭证图可选）。
export function applySeckillRefund(orderNo, data) {
	return request('/api/coupons/seckill/orders/' + orderNo + '/refund', {
		method: 'POST',
		data: data || {}
	})
}
