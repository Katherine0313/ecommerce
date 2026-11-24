import axios from './request.js'

// ==================== 前台商城（C 端 user，/api/orders） ====================

// 创建订单（结算页）
export function addOrder(data) {
  return axios.post('/api/orders', data)
}

// 支付订单（收银台）
export function addPaidOrder(id, data) {
  return axios.put(`/api/orders/${id}/pay`, data)
}

// 获取订单详情（收银台支付用）
export function getPendingOrderList(id) {
  return axios.get(`/api/orders/${id}`)
}

// ==================== 后台管理（admin，/api/admin/orders） ====================

// 获取全部订单
export function getAllOrders(params = {}) {
  return axios.get('/api/admin/orders', { params })
}

// 获取订单状态常量
export function getOrderStatus() {
  return axios.get('/api/admin/orders/status-constants')
}

// 获取订单详情（含明细）
export function getDetailOrders(id) {
  return axios.get(`/api/admin/orders/${id}`)
}

// 获取快递公司列表
export function getCouriers() {
  return axios.get('/api/admin/orders/couriers')
}

// 获取快递公司列表（别名）
export function getCourierList() {
  return axios.get('/api/admin/orders/couriers')
}

// 删除订单
export function deleteOrder(id) {
  return axios.delete(`/api/admin/orders/${id}`)
}

// 批量删除订单
export function deleteOrders(ids) {
  return axios.delete('/api/admin/orders/batch-delete', { data: { ids } })
}

// 发货
export function shipOrder(id, data) {
  return axios.put(`/api/admin/orders/${id}/ship`, data)
}

// 获取物流轨迹
export function getLogistics(id) {
  return axios.get(`/api/admin/orders/${id}/logistics`)
}

// 更新订单信息
export function updateOrder(id, data) {
  return axios.put(`/api/admin/orders/${id}`, data)
}

// 取消订单
export function cancelOrder(id) {
  return axios.put(`/api/admin/orders/${id}/cancel`)
}
