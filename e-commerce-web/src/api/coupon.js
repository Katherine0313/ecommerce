import axios from './request.js'

// ==================== 优惠券（后台管理，/api/admin/coupons） ====================

// 券列表（分页 + 名称/状态筛选）
export function getCouponList(params) {
  return axios.get('/api/admin/coupons', { params })
}

// 券详情（含适用范围、领取/使用统计）
export function getCouponDetail(id) {
  return axios.get(`/api/admin/coupons/${id}`)
}

// 新建优惠券
export function addCoupon(data) {
  return axios.post('/api/admin/coupons', data)
}

// 编辑优惠券（仅未开始发放）
export function updateCoupon(id, data) {
  return axios.put(`/api/admin/coupons/${id}`, data)
}

// 停止发放
export function stopCoupon(id) {
  return axios.post(`/api/admin/coupons/${id}/stop`)
}

// 删除（仅未开始且无人领取）
export function deleteCoupon(id) {
  return axios.delete(`/api/admin/coupons/${id}`)
}

// 定向 / 全量发券
export function grantCoupon(id, data) {
  return axios.post(`/api/admin/coupons/${id}/grant`, data)
}

// 领取/使用记录
export function getCouponRecords(id, params) {
  return axios.get(`/api/admin/coupons/${id}/records`, { params })
}

// ==================== 秒杀券退款审核（/api/admin/coupon-seckill-refunds） ====================

// 退款单列表（分页 + 状态筛选）
export function getRefundList(params) {
  return axios.get('/api/admin/coupon-seckill-refunds', { params })
}

// 审核通过并退款
export function approveRefund(id) {
  return axios.post(`/api/admin/coupon-seckill-refunds/${id}/approve`)
}

// 拒绝退款（merchantRemark 可选）
export function rejectRefund(id, merchantRemark) {
  return axios.post(`/api/admin/coupon-seckill-refunds/${id}/reject`, { merchantRemark })
}
