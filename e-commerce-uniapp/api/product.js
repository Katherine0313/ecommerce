import { request } from '../utils/server-api.js'

// ===== 商品浏览接口 =====
// 这些接口免登录，游客也能访问（后端 WebMvcConfig 白名单放行）。
// 加购 / 下单才需要登录，走 utils/auth.js 的 ensureLogin。

// 获取商品列表，对应 GET /api/products
export function getProducts(){
	return request('/api/products', { method: 'GET' })
}

// 按一级分类查商品，对应 GET /api/products?categoryId=x
// 老接口不分页（后端兜底 LIMIT 200），仅兼容旧版本；新代码请用 getProductPage
export function getProductsByCategory(categoryId){
	return request('/api/products?categoryId=' + categoryId, { method: 'GET' })
}

// 商品分页列表，对应 GET /api/products/page
// params：page / pageSize / categoryId / keyword / brand / minPrice / maxPrice / sort
// 返回 { list, total, page, pageSize }
export function getProductPage(params = {}){
	const query = Object.keys(params)
		.filter((key) => params[key] !== undefined && params[key] !== null && params[key] !== '')
		.map((key) => key + '=' + encodeURIComponent(params[key]))
		.join('&')
	return request('/api/products/page' + (query ? '?' + query : ''), { method: 'GET' })
}

// 获取商品详情，对应 GET /api/products/{id}
export function getProductDetail(id){
	return request('/api/products/' + id, { method: 'GET' })
}

// 随机取一批商品，对应 GET /api/products/random（首页下拉刷新「换一批」用）
// excludeIds：当前已展示的商品 id，后端排除它们，避免刷新后和上一批大量重复
export function getRandomProducts(limit, excludeIds){
	let query = 'limit=' + (limit || 10)
	if (excludeIds && excludeIds.length) {
		query += '&excludeIds=' + excludeIds.join(',')
	}
	return request('/api/products/random?' + query, { method: 'GET' })
}

// 热门推荐商品，对应 GET /api/products/hot（推荐分类优先，不够随机补足）
// excludeIds：当前已展示的商品 id，下拉刷新换一批时排除
export function getHotProducts(limit, excludeIds){
	let query = 'limit=' + (limit || 8)
	if (excludeIds && excludeIds.length) {
		query += '&excludeIds=' + excludeIds.join(',')
	}
	return request('/api/products/hot?' + query, { method: 'GET' })
}

// 按关键词搜索商品，对应 GET /api/products?keyword=x（免登录）
// 老接口不分页（后端兜底 LIMIT 200），仅兼容旧版本；新代码请用 getProductPage
export function searchProducts(keyword){
	return request('/api/products?keyword=' + encodeURIComponent(keyword), { method: 'GET' })
}
