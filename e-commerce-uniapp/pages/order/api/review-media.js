/**
 * 评价媒体（晒图/视频）解析工具（位于 pages/order 分包内，只给商品详情页用）。
 *
 * product_reviews.images 存的是 JSON 字符串：
 *   [{"url":"/image/review/xxx.png","type":"image"},{"url":"/image/review/v.mp4","type":"video"}]
 * 后端原样透传，前端解析后展示。解析失败一律当空数组，不让一条脏数据把详情页搞崩。
 */

/**
 * 解析评价媒体 JSON。
 * 兼容后端下发 JSON 字符串或已解析好的数组；脏数据一律降级为空数组。
 * @param {string|Array} images 后端返回的 images 字段
 * @returns {Array<{url:string,type:string}>} 媒体列表，type 只可能是 image / video
 */
export function parseReviewMedia(images) {
	if (!images) {
		return []
	}
	let arr = images
	if (typeof images === 'string') {
		try {
			arr = JSON.parse(images)
		} catch (e) {
			return []
		}
	}
	if (!Array.isArray(arr)) {
		return []
	}
	return arr
		.filter((item) => item && typeof item.url === 'string' && item.url)
		.map((item) => ({
			url: item.url,
			type: item.type === 'video' ? 'video' : 'image'
		}))
}

/**
 * 媒体地址补全：小程序把相对路径当包内路径，必须补上域名。
 * 支持 http(s) 绝对地址、// 协议相对地址、/image/... 与 image/... 两种相对写法。
 * @param {string} url 相对或绝对地址
 * @param {string} baseUrl 网关地址
 * @returns {string}
 */
export function reviewMediaUrl(url, baseUrl) {
	if (!url) {
		return ''
	}
	if (/^https?:\/\//i.test(url)) {
		return url
	}
	const base = (baseUrl || '').replace(/\/+$/, '')
	if (url.indexOf('//') === 0) {
		// 协议相对地址：沿用网关协议，缺省 https
		const scheme = (base.match(/^https?:/i) || ['https:'])[0]
		return scheme + url
	}
	return base + (url.charAt(0) === '/' ? url : '/' + url)
}
