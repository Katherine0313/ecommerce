import { request } from '../utils/server-api.js'

// ===== 收货地址接口 =====
// /api/address/** 不在后端免登录白名单内，调用前需先登录。

// 地址列表，对应 GET /api/address；默认地址不单独接口，前端取 isDefault=1 那条
export function listAddress() {
	return request('/api/address', { method: 'GET' })
}

// 新增地址，对应 POST /api/address
export function addAddress(data) {
	return request('/api/address', {
		method: 'POST',
		data: data
	})
}

// 修改地址，对应 PUT /api/address/{id}；设默认即把 isDefault 改成 1
export function updateAddress(id, data) {
	return request('/api/address/' + id, {
		method: 'PUT',
		data: data
	})
}

// 删除地址，对应 DELETE /api/address/{id}
export function deleteAddress(id) {
	return request('/api/address/' + id, { method: 'DELETE' })
}
