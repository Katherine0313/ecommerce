import { request } from '../utils/server-api.js'

// ===== 售后接口 =====
// /api/after-sales/** 需登录，调用前走 utils/auth.js 的 ensureLogin。

// 申请售后，对应 POST /api/after-sales。
// data 字段：{ orderId, afterSaleType, reason, remark, images }
//   afterSaleType: only_refund 仅退款 / return_refund 退货退款 / exchange 换货
//   reason: quality / seven_days / wrong_goods / rejected / other / cancel
export function createAfterSale(data) {
	return request('/api/after-sales', {
		method: 'POST',
		data: data
	})
}

// 售后状态常量（状态/颜色/原因/类型 code → 中文），对应 GET /api/after-sales/status-constants（免登录）
export function getAfterSaleStatus() {
	return request('/api/after-sales/status-constants', { method: 'GET' })
}

// 按订单查售后单（判断订单是否已申请售后），无则返回 null
export function getAfterSaleByOrder(orderId) {
	return request('/api/after-sales/by-order?orderId=' + orderId, { method: 'GET' })
}

// 售后列表，对应 GET /api/after-sales
export function listAfterSales() {
	return request('/api/after-sales', { method: 'GET' })
}

// 售后详情，对应 GET /api/after-sales/{id}
export function getAfterSale(id) {
	return request('/api/after-sales/' + id, { method: 'GET' })
}

// 售后变更记录，对应 GET /api/after-sales/{id}/logs（买家端不返回商家内部备注）
export function getAfterSaleLogs(id) {
	return request('/api/after-sales/' + id + '/logs', { method: 'GET' })
}

// 回填退货物流（退货退款·自行寄回），对应 POST /api/after-sales/{id}/submit-return
export function submitAfterSaleReturn(id, data) {
	return request('/api/after-sales/' + id + '/submit-return', {
		method: 'POST',
		data: data
	})
}

// 买家确认上门取件，对应 POST /api/after-sales/{id}/pickup-complete
export function pickupAfterSale(id) {
	return request('/api/after-sales/' + id + '/pickup-complete', {
		method: 'POST'
	})
}
