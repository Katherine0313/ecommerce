import axios from './request.js'

// ==================== 后台商品评价管理（/api/admin/reviews） ====================
// 买家真实评价整条只读：只能回复 / 显示隐藏 / 删除；
// 新增和编辑只对「虚拟评价」开放。

// 评价列表（分页 + 商品名/订单号/星级/类型/显示状态/时间筛选）
export function getReviewList(params) {
  return axios.get('/api/admin/reviews', { params })
}

// 评价详情
export function getReviewDetail(id) {
  return axios.get(`/api/admin/reviews/${id}`)
}

// 新增虚拟评价（商品名/图由后端从商品表取，前端不传）
export function addVirtualReview(data) {
  return axios.post('/api/admin/reviews', data)
}

// 编辑虚拟评价（真实评价后端会拒）
export function updateVirtualReview(id, data) {
  return axios.put(`/api/admin/reviews/${id}`, data)
}

// 回复评价（一条评价只保留一条回复，再次回复覆盖）
export function replyReview(id, reply) {
  return axios.put(`/api/admin/reviews/${id}/reply`, { reply })
}

// 显示 / 隐藏（visible = 1 显示 / 0 隐藏）
export function updateReviewVisible(id, visible) {
  return axios.put(`/api/admin/reviews/${id}/visible`, { visible })
}

// 删除评价（逻辑删除）
export function deleteReview(id) {
  return axios.delete(`/api/admin/reviews/${id}`)
}

// 导出评价 Excel（与列表同一套筛选条件）
export function exportReviews(params) {
  return axios.get('/api/admin/reviews/export', { params, responseType: 'blob' })
}
