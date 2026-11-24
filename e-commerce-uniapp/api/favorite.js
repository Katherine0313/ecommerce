import { request } from '../utils/server-api.js'

// ===== 商品收藏接口 =====
// 需登录：/api/favorites 不在后端免登录白名单，request 封装会自动带 user 令牌。

// 收藏列表（按收藏时间倒序，含已下架商品），对应 GET /api/favorites
export function listFavorites() {
	return request('/api/favorites', { method: 'GET' })
}

// 是否已收藏该商品，对应 GET /api/favorites/check?productId=x
export function checkFavorite(productId) {
	return request('/api/favorites/check?productId=' + productId, { method: 'GET' })
}

// 收藏商品，对应 POST /api/favorites/{productId}
export function addFavorite(productId) {
	return request('/api/favorites/' + productId, { method: 'POST' })
}

// 取消收藏，对应 DELETE /api/favorites/{productId}
export function removeFavorite(productId) {
	return request('/api/favorites/' + productId, { method: 'DELETE' })
}
