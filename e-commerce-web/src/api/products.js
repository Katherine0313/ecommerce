import axios from './request.js'

// ==================== 商品（后台管理，/api/admin/**） ====================

// 获取商品列表（分页）
export function getGoodsList(params) {
  return axios.get('/api/admin/products', { params })
}

// 添加商品
export function addGoods(data) {
  return axios.post('/api/admin/products', data)
}

// 删除商品
export function deleteGoods(id) {
  return axios.delete(`/api/admin/products/${id}`)
}

// 编辑商品
export function updateGoods(id, data) {
  return axios.put(`/api/admin/products/${id}`, data)
}

// 获取商品品牌列表
export function getBrandList() {
  return axios.get('/api/admin/products/brands')
}

// 获取单个商品详情
export function getGoodsDetail(id) {
  return axios.get(`/api/admin/products/${id}`)
}

// 工作台聚合统计（后端 dashboard 接口待实现）
export function getDashboard() {
  return axios.get('/api/admin/products/dashboard')
}

// ==================== 分类（本期沿用 C 端只读接口，写操作后置） ====================

// 获取商品分类树
export function getCategoryTree() {
  return axios.get('/api/admin/categories')
}

// 更新商品分类
export function updateCategoryTree(id, data) {
  return axios.put(`/api/admin/categories/${id}`, data)
}

// 新增商品分类
export function addCategory(data) {
  return axios.post('/api/admin/categories', data)
}

// 移动商品分类
export function moveCategory(id, direction) {
  return axios.post(`/api/admin/categories/${id}/move`, { direction })
}

// 获取商品扁平分类
export function getCategoryFlat() {
  return axios.get('/api/admin/categories/flat')
}

// 删除商品分类
export function deleteCategory(id) {
  return axios.delete(`/api/admin/categories/${id}`)
}
