import axios from './request.js'

// ==================== 文章（后台管理，/api/admin/articles） ====================

// 文章列表（分页 + 标题/分类/状态筛选）
export function getArticleList(params) {
  return axios.get('/api/admin/articles', { params })
}

// 文章详情（含正文）
export function getArticleDetail(id) {
  return axios.get(`/api/admin/articles/${id}`)
}

// 新建文章
export function addArticle(data) {
  return axios.post('/api/admin/articles', data)
}

// 编辑文章
export function updateArticle(id, data) {
  return axios.put(`/api/admin/articles/${id}`, data)
}

// 启用 / 禁用文章
export function updateArticleStatus(id, status) {
  return axios.put(`/api/admin/articles/${id}/status`, null, { params: { status } })
}

// 删除文章（逻辑删除）
export function deleteArticle(id) {
  return axios.delete(`/api/admin/articles/${id}`)
}

// ==================== 文章分类（/api/admin/article-categories） ====================

// 全部分类（不分页，表格直接用）
export function getArticleCategoryList() {
  return axios.get('/api/admin/article-categories')
}

// 新建分类
export function addArticleCategory(data) {
  return axios.post('/api/admin/article-categories', data)
}

// 编辑分类
export function updateArticleCategory(id, data) {
  return axios.put(`/api/admin/article-categories/${id}`, data)
}

// 删除分类（分类下还有文章时后端会拒绝）
export function deleteArticleCategory(id) {
  return axios.delete(`/api/admin/article-categories/${id}`)
}
