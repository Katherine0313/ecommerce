import axios from './request.js'

// ==================== 标签（后台管理，/api/admin/**） ====================

// 获取标签列表
export function getTagList() {
  return axios.get('/api/admin/tags')
}

// 获取商品关联的标签
export function getProductTags(productId) {
  return axios.get(`/api/admin/tags/products/${productId}/tags`)
}

// 更新标签
export function updateTag(id, data) {
  return axios.put(`/api/admin/tags/${id}`, data)
}

// 给一个标签批量绑定商品
export function bindTag(tagId, productIds) {
  return axios.post(`/api/admin/tags/${tagId}/products`, { productIds })
}

// 新增标签
export function addTag(data) {
  return axios.post('/api/admin/tags', data)
}

// 获取标签关联的商品
export function getTagGoods(tagId) {
  return axios.get(`/api/admin/tags/${tagId}/products`)
}

// 解绑单个商品标签
export function unbindTagGood(productId, tagId) {
  return axios.delete(`/api/admin/tags/products/${productId}/tags/${tagId}`)
}

// 批量解绑标签下的商品
export function unbindTagGoods(tagId, productIds) {
  return axios.delete(`/api/admin/tags/${tagId}/products`, { data: { productIds } })
}

// 删除标签
export function deleteTag(id) {
  return axios.delete(`/api/admin/tags/${id}`)
}
