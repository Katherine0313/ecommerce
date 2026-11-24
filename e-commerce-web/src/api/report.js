import axios from './request.js'

// ==================== 后台报表统计（admin，/api/admin/report） ====================

// 营业额卡片：period = yesterday | week | month
export function getTurnover(period) {
  return axios.get('/api/admin/report/turnover', { params: { period } })
}

// 营业额折线：period = week | month（昨日只有一天，前端不画折线）
export function getTurnoverReport(period) {
  return axios.get('/api/admin/report/turnover-report', { params: { period } })
}

// 销量排名：period = yesterday | week | month，返回前 limit 名
export function getSalesRanking(period, limit = 8) {
  return axios.get('/api/admin/report/sales-ranking', { params: { period, limit } })
}

// 工作台统计卡：今日/昨日营业额与订单量、待发货数
export function getDashboardStats() {
  return axios.get('/api/admin/report/dashboard')
}

// 工作台顶部累计销售额：全时段实收 − 退款
export function getHeadline() {
  return axios.get('/api/admin/report/headline')
}

// 运营数据报表导出（近 30 天 Excel）
export function exportOperationReport() {
  return axios.get('/api/admin/report/export', { responseType: 'blob' })
}
