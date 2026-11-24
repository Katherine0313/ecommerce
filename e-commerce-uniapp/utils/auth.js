import { useUserStore } from '../store/user.js'
import { wxLogin } from '../api/login.js'
import { isTokenExpired } from './token.js'

// 手动退出登录标记：存在时 ensureLogin 不再自动静默登录，直到用户主动点「微信登录」
const MANUAL_LOGOUT_KEY = 'manualLogout'

// 是否处于「用户主动退出」状态
export function isManualLogout(){
	return !!uni.getStorageSync(MANUAL_LOGOUT_KEY)
}

// 进行中的登录 Promise：首页延后的静默登录和用户点加购可能同时触发，
// 复用同一次请求，避免并发 wxlogin、重复写 token
let loginPromise = null

// 需要登录的操作（加购、下单）前调用：已登录直接放行，未登录则静默登录。
// uni.login 只换 code，不弹授权框，用户全程无感；
// 需要用户主动操作的只有头像（chooseAvatar）和昵称（nickname 输入框）。
// options.force = true 表示用户主动点登录（如登录弹窗），此时忽略手动退出标记。
export async function ensureLogin(options = {}){
	const store = useUserStore()
	// 已经有有效 token 就不用再走一遍登录。
	// 这里直接校验 token 是否过期，而不是只读 store.isLogin：
	// isLogin 是 computed，Date.now() 不参与响应式，时间跨过过期点时它不会自动重算，
	// 会导致复用过期 token 发请求、等后端 401 才被动处理。
	if(store.token && !isTokenExpired(store.token)){
		return true
	}
	// 用户手动退出后，页面 onShow 里的自动登录要跳过，否则退出立刻又被登回去
	if(isManualLogout() && !options.force){
		const err = new Error('已退出登录')
		err.manualLogout = true
		throw err
	}

	if(!loginPromise){
		loginPromise = (async () => {
			const { code } = await uni.login({ provider: 'weixin' })
			const res = await wxLogin(code)
			store.setToken(res.token)
			store.setUserInfo({ id: res.id, avatar: res.avatar }) // WxLoginVO 无 name/phone：静默登录拿不到昵称，昵称/手机号统一由 /api/user/profile 补齐
			// 重新登录成功，清掉手动退出标记，恢复自动登录
			uni.removeStorageSync(MANUAL_LOGOUT_KEY)
			return true
		})().finally(() => {
			// 无论成功失败都释放锁，失败后下次操作还能重试
			loginPromise = null
		})
	}
	return loginPromise
}

// 主动登录：用户点「微信登录」时调用，清除手动退出标记后重新登录
export function login(){
	uni.removeStorageSync(MANUAL_LOGOUT_KEY)
	return ensureLogin({ force: true })
}

// 退出登录：打手动退出标记 + 清本地 token 和用户信息
export function logout(){
	uni.setStorageSync(MANUAL_LOGOUT_KEY, 1)
	useUserStore().logout()
}
