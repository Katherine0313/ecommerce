import axios from './request.js'

// ==================== 快递发货（后台管理，/api/admin/deliveries） ====================

// 包裹列表（分页 + 订单号/单号/快递公司/发货类型筛选）
export function getDeliveryList(params) {
  return axios.get('/api/admin/deliveries', { params })
}

// 发货（订单 3 待发货 → 4 已发货）
export function createDelivery(orderId, data) {
  return axios.post('/api/admin/deliveries', data, { params: { orderId } })
}

// 补发（订单状态不动，追加一个包裹）
export function resendDelivery(orderId, data) {
  return axios.post('/api/admin/deliveries/resend', data, { params: { orderId } })
}

// 修改包裹的快递信息（改单号/备注）
export function updateDelivery(id, data) {
  return axios.put(`/api/admin/deliveries/${id}`, data)
}

// 某订单的发货流水
export function getDeliveryLogs(orderId) {
  return axios.get('/api/admin/deliveries/logs', { params: { orderId } })
}

// ==================== 快递公司字典（后台管理，/api/admin/expresses） ====================

// 快递公司列表（分页 + 编码/名称模糊 + 状态筛选）
export function getExpressList(params) {
  return axios.get('/api/admin/expresses', { params })
}

// 启用中的快递公司（下拉数据源）
export function getEnabledExpresses() {
  return axios.get('/api/admin/expresses/enabled')
}

// 新建快递公司
export function addExpress(data) {
  return axios.post('/api/admin/expresses', data)
}

// 编辑快递公司
export function updateExpress(id, data) {
  return axios.put(`/api/admin/expresses/${id}`, data)
}

// 删除快递公司（软删）
export function deleteExpress(id) {
  return axios.delete(`/api/admin/expresses/${id}`)
}
