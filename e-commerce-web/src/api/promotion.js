import axios from './request.js'

// ==================== 促销活动（后台管理，/api/admin/promotions） ====================

// 活动列表（分页 + 名称/状态筛选）
export function getPromotionList(params) {
  return axios.get('/api/admin/promotions', { params })
}

// 活动详情（含商品、阶梯、效果统计）
export function getPromotionDetail(id) {
  return axios.get(`/api/admin/promotions/${id}`)
}

// 新建活动
export function addPromotion(data) {
  return axios.post('/api/admin/promotions', data)
}

// 编辑活动（仅未开始）
export function updatePromotion(id, data) {
  return axios.put(`/api/admin/promotions/${id}`, data)
}

// 提前结束（仅进行中）
export function endPromotion(id) {
  return axios.post(`/api/admin/promotions/${id}/end`)
}

// 删除（仅未开始，逻辑删除）
export function deletePromotion(id) {
  return axios.delete(`/api/admin/promotions/${id}`)
}

// 活动效果统计
export function getPromotionStats(id) {
  return axios.get(`/api/admin/promotions/${id}/stats`)
}
