import { defineStore } from 'pinia'
import { getToken, setToken as saveToken, clearToken, isTokenExpired } from '../utils/token.js'

// 用户登录态：token 落到本地 Storage（重开小程序还在），用户信息只存内存（进页面时重新拉）
export const useUserStore = defineStore('user', {
	state: () => ({
		// 初始值从本地读，之后由 setToken / logout 同步维护
		token: getToken(),
		// 用空对象而不是 null：模板里直接读 userInfo.avatar 不会报错
		userInfo: {}
	}),
	getters: {
		// 有 token 且未过期才算已登录：token 有效期 7 天，过期的 token 不能再当登录态，
		// 否则 ensureLogin 会直接放行、带着死 token 请求，后端 401 后才被动清理
		isLogin: (state) => !!state.token && !isTokenExpired(state.token)
	},
	actions: {
		setToken(token) {
			this.token = token
			saveToken(token)
		},
		setUserInfo(info) {
			this.userInfo = info
		},
		// 只改头像，保留其它字段
		setAvatar(avatar) {
			this.userInfo.avatar = avatar
		},
		logout() {
			this.token = ''
			this.userInfo = {}
			clearToken()
		}
	}
})
