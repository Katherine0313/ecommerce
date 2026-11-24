import { request, BASE_URL } from '../utils/server-api.js'
import { getToken } from '../utils/token.js'

// ===== 订单接口 =====
// /api/orders/** 不在后端免登录白名单内，调用前需先登录（走 utils/auth.js 的 ensureLogin）。

// 生成幂等键：一次「下单意图」调用一次，下单成功后前端丢弃，重试时复用同一个 key。
// 后端对相同 idempotencyKey 只真正下单一次（Redis SETNX 占位），防弱网重试重复下单。
// 小程序基础库较新时用 crypto.randomUUID，否则手写 v4 兜底。
export function genIdempotencyKey() {
	if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
		return crypto.randomUUID()
	}
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
		const r = Math.random() * 16 | 0
		const v = c === 'x' ? r : (r & 0x3 | 0x8)
		return v.toString(16)
	})
}

// 确认页报价，对应 POST /api/orders/quote。
// 传 [{ productId, quantity }] + 可选 couponId，返回促销/券优惠与应付金额；
// 只用于展示，提交订单时后端会按最新活动和券状态重新计算。
export function quoteOrder(items, couponId) {
	return request('/api/orders/quote', {
		method: 'POST',
		data: { items: items, couponId: couponId || null }
	})
}

// 下单，对应 POST /api/orders。
// data 字段：{ consignee, phone, region, detailAddress, remark, idempotencyKey,
//   items: [{ productId, quantity, insuranceDetail(String), insuranceFee }] }
export function createOrder(data) {
	return request('/api/orders', {
		method: 'POST',
		data: data
	})
}

// 订单列表，对应 GET /api/orders；返回 OrderVO 数组
// keyword 非空时后端按订单号/收货人/商品名模糊搜索
export function listOrders(keyword) {
	const url = '/api/orders' + (keyword ? '?keyword=' + encodeURIComponent(keyword) : '')
	return request(url, { method: 'GET' })
}

// 订单详情，对应 GET /api/orders/{id}
export function getOrder(id) {
	return request('/api/orders/' + id, { method: 'GET' })
}

// 物流轨迹，对应 GET /api/orders/{id}/logistics；返回 { courier, trackingNumber, traces: [{time,status,desc}] }
export function getLogistics(id) {
	return request('/api/orders/' + id + '/logistics', { method: 'GET' })
}

// 支付，对应 PUT /api/orders/{id}/pay；订单从待付款 → 待发货。
// 暂未接入微信支付，payType 仅被后端记录，传占位值即可（不传默认 WeChat，对齐 PayTypeEnum 的 code）。
export function payOrder(id, payType) {
	return request('/api/orders/' + id + '/pay', {
		method: 'PUT',
		data: { payType: payType || 'WeChat' }
	})
}

// 取消订单，对应 PUT /api/orders/{id}/cancel；后端恢复库存并改取消
export function cancelOrder(id) {
	return request('/api/orders/' + id + '/cancel', { method: 'PUT' })
}

// 删除订单（软删），对应 DELETE /api/orders/{id}
export function deleteOrder(id) {
	return request('/api/orders/' + id, { method: 'DELETE' })
}

// 确认收货，对应 PUT /api/orders/{id}/finish；已发货 → 已完成
export function finishOrder(id) {
	return request('/api/orders/' + id + '/finish', { method: 'PUT' })
}

// 提交订单评价，对应 POST /api/orders/{id}/review。
// data 字段：{ productId, rating, tag, content, images }
// 后端校验：订单已完成且未评价过，成功后订单列表里该订单 reviewed=true。
export function submitReview(orderId, data) {
	return request('/api/orders/' + orderId + '/review', {
		method: 'POST',
		data: data
	})
}

// 上传图片/视频：用 uni.uploadFile 传本地文件，对应 POST /api/upload?type=xxx。
// type 分流目录（review=评价晒图，after-sale=售后凭证）；不走 request 封装（multipart），返回相对路径。
export function uploadMedia(filePath, type = 'review') {
	return new Promise((resolve, reject) => {
		uni.uploadFile({
			url: BASE_URL + '/api/upload?type=' + type,
			filePath: filePath,
			name: 'file',
			header: {
				Authorization: 'Bearer ' + getToken()
			},
			success: (res) => {
				let data
				try {
					data = JSON.parse(res.data)
				} catch (e) {
					reject({ message: '上传失败' })
					return
				}
				if (data.success) {
					resolve(data.data.url)
				} else {
					reject(data)
				}
			},
			fail: (err) => {
				reject(err)
			}
		})
	})
}
