import axios from './request.js'

// 申请售后
export function addAfterSale(data) {
  return axios.post('/api/after-sales', data)
}

// 获取售后列表（后台管理端，admin 令牌）
export function getAfterSales(params = {}) {
  return axios.get('/api/admin/after-sales',{params})
}
// 获取订单状态常量
export function getAfterSaleStatus() {
  return axios.get('/api/after-sales/status-constants')
}

// 售后详情（后台管理端，admin 令牌）
export function getAfterSale(id) {
  return axios.get(`/api/admin/after-sales/${id}`)
}

// 审核售后（更新状态，后台管理员）
export function reviewAfterSale(id, data) {
  return axios.put(`/api/admin/after-sales/${id}`, data)
}

// 售后变更流水（后台管理端，含商家内部备注）
export function getAfterSaleLogs(id) {
  return axios.get(`/api/admin/after-sales/${id}/logs`)
}

// 用户提交退货方式（上门取件/自行寄回 + 时间段）
export function submitAfterSaleReturn(id,data){
  return axios.post(`/api/after-sales/${id}/submit-return`,data)
}
// 删除售后（软删除，后台管理员）
export function deleteAfterSale(id){
  return axios.delete(`/api/admin/after-sales/${id}`)
}