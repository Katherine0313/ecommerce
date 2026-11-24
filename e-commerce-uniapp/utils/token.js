// token 本地存储的 key
const TOKEN_KEY = 'token'

// 读取 token
export function getToken() {
	return uni.getStorageSync(TOKEN_KEY) || ''
}

// 保存 token
export function setToken(token) {
	uni.setStorageSync(TOKEN_KEY, token)
}

// 清除 token
export function clearToken() {
	uni.removeStorageSync(TOKEN_KEY)
}

// base64url → UTF-8 字符串。小程序没有 atob，用 uni.base64ToArrayBuffer 兜底
function decodeBase64Url(str) {
	let s = str.replace(/-/g, '+').replace(/_/g, '/')
	while (s.length % 4) {
		s += '='
	}
	let binary
	if (typeof atob === 'function') {
		binary = atob(s)
	} else {
		const bytes = new Uint8Array(uni.base64ToArrayBuffer(s))
		binary = ''
		for (let i = 0; i < bytes.length; i++) {
			binary += String.fromCharCode(bytes[i])
		}
	}
	try {
		// 按 UTF-8 还原，避免 payload 里中文（昵称等）被截断
		return decodeURIComponent(escape(binary))
	} catch (e) {
		return binary
	}
}

// token 是否已过期：解码 JWT payload 的 exp 与当前时间比较。
// 只用于前端提前判断、避免带着死 token 发请求；真正的鉴权仍以后端校验为准。
// 提前 60 秒判定过期，避免临界点请求刚发出去就过期。
export function isTokenExpired(token) {
	const value = token || ''
	const parts = value.split('.')
	if (parts.length !== 3) {
		return true
	}
	try {
		const payload = JSON.parse(decodeBase64Url(parts[1]))
		if (!payload || !payload.exp) {
			// 没有 exp 的令牌无法判断，交给后端兜底
			return false
		}
		return payload.exp * 1000 <= Date.now() + 60 * 1000
	} catch (e) {
		// 解析失败按过期处理，触发重新登录
		return true
	}
}
