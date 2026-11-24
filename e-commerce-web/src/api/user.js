import axios from './request.js'

// 用户增长统计（后台工作台「用户增长」双折线）
// period = yesterday | week | month，不传时后端默认近 12 个月
export function getUserStatistics(period) {
  return axios.get('/api/admin/users/statistics', { params: { period } })
}
