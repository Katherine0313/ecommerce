import axios from './request.js'

// 获取地址列表
export function getAddressList(params) {
  return axios.get('/api/address', { params })
}

// 创建收货地址
export function addAddress(data) {
  return axios.post('/api/address', data)
}

// 更新收货地址
export function updateAddress(id, data) {
  return axios.put(`/api/address/${id}`, data)
}

// 删除收货地址
export function deleteAddress(id) {
  return axios.delete(`/api/address/${id}`)
}
