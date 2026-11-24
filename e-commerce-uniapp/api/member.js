import { request } from '../utils/server-api.js'

// ===== 会员接口 =====
// /api/member/** 需登录，调用前走 utils/auth.js 的 ensureLogin。

// 我的会员档案（等级、成长值、下一等级进度）
export function getMemberProfile() {
	return request('/api/member/profile', { method: 'GET' })
}

// 会员等级列表
export function getMemberLevels() {
	return request('/api/member/levels', { method: 'GET' })
}
