/**
 * 匿名访客标识：客服问答等免登录功能使用。
 * 首次使用生成 32 位十六进制串存入本地存储，之后请求带 X-Anonymous-Id 头，
 * 后端据此区分访客、限流并保存会话（与商城网页端同一套规则）。
 */
const STORAGE_KEY = 'jm_anonymous_id'

function genId() {
	let id = ''
	for (let i = 0; i < 32; i++) {
		id += Math.floor(Math.random() * 16).toString(16)
	}
	return id
}

export function getAnonymousId() {
	let id = uni.getStorageSync(STORAGE_KEY)
	if (!id || String(id).length < 32) {
		id = genId()
		uni.setStorageSync(STORAGE_KEY, id)
	}
	return String(id)
}
