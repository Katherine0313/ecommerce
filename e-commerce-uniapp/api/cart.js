import { request } from '../utils/server-api.js'

// ===== 购物车接口 =====
// /api/cart/** 不在后端免登录白名单内，调用前需先登录（走 utils/auth.js 的 ensureLogin）。
// 后端购物车唯一键已升级为 (userId, skuId)，所有接口均带 skuId。

// 加入购物车，对应 POST /api/cart；该 SKU 已在购物车则由后端累加数量。
// insuranceDetail / insuranceFee 是加购弹窗里勾选的增值保障（不选传空数组 / 0）。
export function addToCart(productId, quantity, insuranceDetail, insuranceFee, skuId) {
	const data = {
		productId: productId,
		quantity: quantity,
		insuranceDetail: insuranceDetail,
		insuranceFee: insuranceFee
	}
	if (skuId != null) data.skuId = skuId
	return request('/api/cart', {
		method: 'POST',
		data: data
	})
}

// 购物车列表，对应 GET /api/cart；返回 CartVO 数组（含 subTotal 单品小计）
export function getCart() {
	return request('/api/cart', { method: 'GET' })
}

// 购物车商品总件数，对应 GET /api/cart/count（首页角标用，不拉完整列表）
// silent：启动时后台静默刷新角标，未登录/token 失效不弹 toast
export function getCartCount() {
	return request('/api/cart/count', { method: 'GET', silent: true })
}

// 更新数量，对应 PUT /api/cart/{productId}?skuId=xxx；quantity 最小 1，由后端兜底
export function updateQuantity(productId, quantity, skuId) {
	const query = (skuId != null) ? ('?skuId=' + skuId) : ''
	return request('/api/cart/' + productId + query, {
		method: 'PUT',
		data: { quantity: quantity }
	})
}

// 移除某个购物车行，对应 DELETE /api/cart/{productId}?skuId=xxx
export function removeItem(productId, skuId) {
	const query = (skuId != null) ? ('?skuId=' + skuId) : ''
	return request('/api/cart/' + productId + query, { method: 'DELETE' })
}

// 批量清空购物车行，对应 DELETE /api/cart；传 productIds 只删列出的行，不传则清空整个购物车
export function clearCart(productIds, skuIds) {
	const data = {}
	if (productIds && productIds.length) data.productIds = productIds
	if (skuIds && skuIds.length) data.skuIds = skuIds
	const hasBody = Object.keys(data).length > 0
	return request('/api/cart', {
		method: 'DELETE',
		data: hasBody ? data : undefined
	})
}
