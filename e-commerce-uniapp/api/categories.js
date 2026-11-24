import { request } from '../utils/server-api.js'

// ===== 分类浏览接口 =====
// 免登录，游客也能访问（后端 WebMvcConfig 白名单放行）。

// 获取分类列表，对应 GET /api/categories
export function getCategories(){
	return request('/api/categories', { method: 'GET' })
}
