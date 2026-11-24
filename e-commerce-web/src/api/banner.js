import axios from './request.js'

// ==================== Banner 轮播（后台管理，/api/admin/banners） ====================

// Banner 列表（分页 + 标题/位置/状态筛选）
export function getBannerList(params) {
  return axios.get('/api/admin/banners', { params })
}

// Banner 详情
export function getBannerDetail(id) {
  return axios.get(`/api/admin/banners/${id}`)
}

// 新建 Banner
export function addBanner(data) {
  return axios.post('/api/admin/banners', data)
}

// 编辑 Banner
export function updateBanner(id, data) {
  return axios.put(`/api/admin/banners/${id}`, data)
}

// 启用 / 禁用 Banner
export function updateBannerStatus(id, status) {
  return axios.put(`/api/admin/banners/${id}/status`, null, { params: { status } })
}

// 删除 Banner（逻辑删除）
export function deleteBanner(id) {
  return axios.delete(`/api/admin/banners/${id}`)
}
