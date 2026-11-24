import { request } from '../utils/server-api.js'

// ===== 首页聚合接口 =====
// 免登录：分类 + 猜你喜欢一次返回，首屏只发一个请求，弱网下少一次 RTT。

// 获取首页聚合数据，对应 GET /api/home
export function getHome(limit){
	return request('/api/home?limit=' + (limit || 10), { method: 'GET' })
}
