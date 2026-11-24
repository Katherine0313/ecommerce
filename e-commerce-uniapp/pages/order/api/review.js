// 本文件位于 pages/order 分包内（原来在根 api/）。
// 只被分包的商品详情页使用，放在主包会触发微信提示
// 「主包内不应存在主包未使用的JS文件」，故随分包一起打包。
import { request } from '../../../utils/server-api.js'

// ===== 商品评价（公开只读，免登录） =====
// 商品详情页展示评价列表与星级统计。
// 后端只返回 status=1（显示）且未删除的评价，后台隐藏一条这里立刻不显示。

// 某商品的评价列表：GET /api/reviews/product/{productId}?page=1&pageSize=10
// 返回 { list, total, page, pageSize }
export function getProductReviews(productId, page = 1, pageSize = 10){
	const p = Math.max(1, Number(page) || 1)
	const size = Math.max(1, Number(pageSize) || 10)
	const id = encodeURIComponent(productId)
	return request(`/api/reviews/product/${id}?page=${p}&pageSize=${size}`, { method: 'GET' })
}

// 某商品的评价统计：GET /api/reviews/product/{productId}/summary
// 返回 { totalCount, avgRating, goodRate, withMediaCount, fiveStar ... oneStar }
export function getReviewSummary(productId){
	return request(`/api/reviews/product/${encodeURIComponent(productId)}/summary`, { method: 'GET' })
}
