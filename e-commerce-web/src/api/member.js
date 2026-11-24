import axios from './request.js'

// ==================== 会员（后台） ====================

// 会员列表（分页 + 等级/昵称/手机号筛选）
export function getMemberList(params) {
  return axios.get('/api/admin/members', { params })
}

// 会员等级列表
export function getMemberLevels() {
  return axios.get('/api/admin/member-levels')
}

// 编辑会员等级（名称 / 成长值门槛 / 权益文案）
export function updateMemberLevel(id, data) {
  return axios.put(`/api/admin/member-levels/${id}`, data)
}

// 刷新单个会员的成长值/等级（按订单重新计算）
export function refreshMember(id) {
  return axios.post(`/api/admin/members/${id}/refresh`)
}

// 会员详情（含已注销用户，只读）
export function getMemberDetail(id) {
  return axios.get(`/api/admin/members/${id}`)
}
