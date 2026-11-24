import { request } from '../utils/server-api.js'

// ===== 文章接口 =====
// 免登录，游客也能访问（后端 WebMvcConfig 白名单放行）。

// 按标题取文章，对应 GET /api/articles/by-title?title=x
// 用户协议 / 隐私政策这类固定内容用标题取，不用记 id
export function getArticleByTitle(title){
	return request('/api/articles/by-title?title=' + encodeURIComponent(title), { method: 'GET' })
}

// 文章详情，对应 GET /api/articles/{id}
export function getArticleDetail(id){
	return request('/api/articles/' + id, { method: 'GET' })
}

// 文章浏览量 +1，对应 POST /api/articles/{id}/browse
export function reportArticleBrowse(id){
	return request('/api/articles/' + id + '/browse', { method: 'POST' })
}
