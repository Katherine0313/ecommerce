import axios from './request.js'

// ==================== SKU 库存 ====================
export function getSkuStockList(params) {
  return axios.get('/api/admin/stock/skus', { params })
}

// 建单弹窗里的 SKU 远程搜索（复用 SKU 库存列表接口，返回 id/skuCode/productName/specJson/stock）
export function searchSku(keyword) {
  return axios.get('/api/admin/stock/skus', { params: { search: keyword, page: 1, size: 20 } })
}

export function setSkuAlert(skuId, data) {
  return axios.post(`/api/admin/stock/skus/${skuId}/alert`, data)
}

export function batchSetSkuAlert(data) {
  return axios.post('/api/admin/stock/skus/alert/batch', data)
}

// ==================== 看板 ====================
export function getDashboard() {
  return axios.get('/api/admin/stock/dashboard')
}

export function getLowStockSku(limit = 20) {
  return axios.get('/api/admin/stock/dashboard/low-stock', { params: { limit } })
}

export function getTrend(days = 30) {
  return axios.get('/api/admin/stock/dashboard/trend', { params: { days } })
}

export function getTypeDist(days = 30) {
  return axios.get('/api/admin/stock/dashboard/type-dist', { params: { days } })
}

export function getSkuDist(limit = 15) {
  return axios.get('/api/admin/stock/dashboard/sku-dist', { params: { limit } })
}

// ==================== 流水 ====================
export function getTransactions(params) {
  return axios.get('/api/admin/stock/transactions', { params })
}

// ==================== 单据 ====================
export function getStockOrders(params) {
  return axios.get('/api/admin/stock/orders', { params })
}

export function getStockOrderDetail(id) {
  return axios.get(`/api/admin/stock/orders/${id}`)
}

export function createStockOrder(data) {
  return axios.post('/api/admin/stock/orders', data)
}

export function completeStockOrder(id) {
  return axios.post(`/api/admin/stock/orders/${id}/complete`)
}

export function cancelStockOrder(id) {
  return axios.post(`/api/admin/stock/orders/${id}/cancel`)
}
