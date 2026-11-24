import axios from './request.js'

/** 已同步的服务列表 */
export function getApiServices() {
  return axios.get('/api/admin/system/api-docs/services')
}

/** 从网关 OpenAPI 同步接口（服务较多，放宽超时） */
export function syncApiDocs() {
  return axios.post('/api/admin/system/api-docs/sync', null, { timeout: 60000 })
}

/** 接口树 */
export function getApiTree(params) {
  return axios.get('/api/admin/system/api-docs/tree', { params })
}

/** 接口详情 */
export function getApiDoc(id) {
  return axios.get(`/api/admin/system/api-docs/${id}`)
}

/** 编辑接口人工字段 */
export function updateApiDoc(id, data) {
  return axios.put(`/api/admin/system/api-docs/${id}`, data)
}
