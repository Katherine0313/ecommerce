import axios from './request.js'

// ==================== 后台财务管理（/api/admin/finance） ====================
// 收款单由支付成功自动生成，后台只做查询 / 审核 / 导出；
// 收款账户按渠道维护，用于收款单归集。

// 财务看板（系统实收、实际退款、趋势、渠道和当前待办）
export function getFinanceDashboard(params) {
  return axios.get('/api/admin/finance/dashboard', { params })
}

// 收款单列表（分页 + 单号/订单号/买家/渠道/状态/时间筛选）
export function getReceiptList(params) {
  return axios.get('/api/admin/finance/receipts', { params })
}

// 收款单统计卡（与列表同一套筛选条件）
export function getReceiptSummary(params) {
  return axios.get('/api/admin/finance/receipts/summary', { params })
}

// 收款单详情
export function getReceiptDetail(id) {
  return axios.get(`/api/admin/finance/receipts/${id}`)
}

// 审核收款单（仅待审核可审核，审核后为终态）
export function auditReceipt(id, remark) {
  return axios.put(`/api/admin/finance/receipts/${id}/audit`, { remark })
}

// 修改备注（已审核只读）
export function updateReceiptRemark(id, remark) {
  return axios.put(`/api/admin/finance/receipts/${id}/remark`, { remark })
}

// 导出收款单 Excel（与列表同一套筛选条件）
export function exportReceipts(params) {
  return axios.get('/api/admin/finance/receipts/export', { params, responseType: 'blob' })
}

// 收款账户列表
export function getAccountList(params) {
  return axios.get('/api/admin/finance/accounts', { params })
}

// 启用中的收款账户（下拉用）
export function getEnabledAccounts() {
  return axios.get('/api/admin/finance/accounts/enabled')
}

// 新增收款账户
export function addAccount(data) {
  return axios.post('/api/admin/finance/accounts', data)
}

// 编辑收款账户（名称 / 商户号 / 备注）
export function updateAccount(id, data) {
  return axios.put(`/api/admin/finance/accounts/${id}`, data)
}

// 启停账户（status = 1 启用 / 0 停用）
export function updateAccountStatus(id, status) {
  return axios.put(`/api/admin/finance/accounts/${id}/status`, { status })
}

// 设为同渠道默认账户（互斥）
export function setDefaultAccount(id) {
  return axios.put(`/api/admin/finance/accounts/${id}/default`)
}

// 删除收款账户（软删除）
export function deleteAccount(id) {
  return axios.delete(`/api/admin/finance/accounts/${id}`)
}
