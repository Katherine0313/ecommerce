import { BASE_URL } from './server-api.js'

// 金额统一两位小数展示
export function fmtMoney(n) {
	return Number(n || 0).toFixed(2)
}

// 订单时间：后端 LocalDateTime 序列化串 → "2026-10-15 17:15"（截到分钟）
export function formatOrderTime(t) {
	if (!t) {
		return ''
	}
	return String(t).replace('T', ' ').slice(0, 16)
}

// 订单时间 → 毫秒时间戳（待付款倒计时用）。
// 兼容：数字（毫秒）/ Jackson 3 可能序列化成的数组 [年,月,日,时,分,秒] / ISO 字符串
export function parseOrderTime(t) {
	if (!t) {
		return 0
	}
	// 数字时间戳（毫秒）
	if (typeof t === 'number') {
		return t
	}
	// Jackson 3 序列化 LocalDateTime 可能是数组
	if (Array.isArray(t)) {
		const y = t[0]
		const mo = t[1] || 1
		const d = t[2] || 1
		const h = t[3] || 0
		const mi = t[4] || 0
		const se = t[5] || 0
		const time = new Date(y, mo - 1, d, h, mi, se).getTime()
		return isNaN(time) ? 0 : time
	}
	// 字符串：空格分隔的 "yyyy-MM-dd HH:mm:ss" iOS 解析不了，先转 T 分隔（ISO）再解析
	const s = String(t).replace(' ', 'T')
	let time = new Date(s).getTime()
	if (isNaN(time)) {
		// 兜底：- 转 /（iOS 对 / 分隔的格式支持最好）
		time = new Date(String(t).replace(/-/g, '/')).getTime()
	}
	return isNaN(time) ? 0 : time
}

// 商品图 URL：后端 image 是 /image/upload/N.webp 相对路径，补 BASE_URL；没有则回退后端同命名商品图
export function goodsImgUrl(image, productId) {
	if (image) {
		return image.indexOf('http') === 0 ? image : BASE_URL + image
	}
	return productId ? BASE_URL + '/image/upload/' + productId + '.webp' : ''
}
