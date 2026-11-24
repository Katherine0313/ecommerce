import { getToken, setToken, clearToken } from "./token.js"
import { useUserStore } from "../store/user.js"

// 后端 API 网关地址：前端所有请求统一走网关，不直连各微服务端口。
// 网关按 /api/** 前缀路由到下游服务，各微服务的端口对前端透明，无需（也不应）为每个模块单独配地址。
// 后端微服务端口规划（仅维护参考，前端只需记住网关 8080）：
//   gateway 8080 / auth 9001 / product 9003 / order 9004
//   aftersale 9005 / user 9006 / file 9007
// 环境判断：
//   HBuilderX「运行」→ development → 本机网关
//   HBuilderX「发行」→ production  → 线上 API 域名
// process.env.NODE_ENV 在编译期被替换成字符串，生产包里不会残留 127.0.0.1
const DEV_BASE_URL = 'http://127.0.0.1:8080'   // 本地开发：本机 SpringBoot 网关
const PROD_BASE_URL = 'https://api.jiemids.com' // 打包上线：线上 API 域名
export const BASE_URL = process.env.NODE_ENV === 'development' ? DEV_BASE_URL : PROD_BASE_URL

// 手动退出的标记，与 utils/auth.js 的 MANUAL_LOGOUT_KEY 保持一致
const MANUAL_LOGOUT_KEY = 'manualLogout'

// 401/403 后的静默重登：并发请求只发一次 wxlogin。
// 这里直接用 uni.request 调 /api/wxlogin（不走本文件的 request 封装），
// 避免 server-api → auth → api/login → server-api 的循环依赖。
let reLoginPromise = null
function silentReLogin(){
	if(reLoginPromise){
		return reLoginPromise
	}
	reLoginPromise = new Promise((resolve, reject) => {
		// 用户主动退出后不该被自动登回去
		if(uni.getStorageSync(MANUAL_LOGOUT_KEY)){
			reject(new Error('已退出登录'))
			return
		}
		uni.login({
			provider: 'weixin',
			success(loginRes){
				uni.request({
					url: BASE_URL + '/api/wxlogin',
					method: 'POST',
					header: { 'Content-Type': 'application/json' },
					data: { code: loginRes.code },
					success(res){
						const body = res.data || {}
						const ok = res.statusCode >= 200 && res.statusCode < 300 && body.success === true
						const newToken = body.data && body.data.token
						if(ok && newToken){
							setToken(newToken)
							try {
								useUserStore().setToken(newToken)
							} catch (e) {}
							resolve(newToken)
						}else{
							reject(new Error('静默登录失败'))
						}
					},
					fail: reject
				})
			},
			fail: reject
		})
	}).finally(() => {
		reLoginPromise = null
	})
	return reLoginPromise
}

// 通用请求封装：自动带 token，成功返回 data，失败统一弹提示
export function request(url, options = {}){
	const token = getToken()
	const header = Object.assign({}, options.header || {})
	// 判断 token 是否存在
	if(token){
		header.Authorization = 'Bearer ' + token
	}
	// _retried 是内部重试标记，不能透传给 uni.request
	const reqOptions = Object.assign({}, options)
	delete reqOptions._retried

	return uni.request({
		url: BASE_URL + url,
		...reqOptions,
		header
	}).then((res) => {
		const code = res.statusCode
		// 401/403：token 失效或不可用（本项目 token 不匹配时返回 403）
		// 必须同时清掉 Storage 和 Pinia 内存里的 token：只清 Storage 时 store.token 仍是旧值，
		// isLogin 会一直是 true，ensureLogin 不再静默重登，本次会话后续请求全部 401
		if(code === 401 || code === 403){
			clearToken()
			try {
				useUserStore().logout()
			} catch (e) {}
			// token 失效：先静默重登并重试原请求一次，用户无感；
			// wxlogin 自身失败不再重试，避免死循环
			if(!options._retried && url !== '/api/wxlogin'){
				return silentReLogin().then(() => {
					return request(url, Object.assign({}, options, { _retried: true }))
				}).catch(() => {
					throw { statusCode: 401, message: '登录已过期，请重新登录' }
				})
			}
			throw { statusCode: 401, message: '登录已过期，请重新登录' }
		}
		// 其余非 2xx（网关 502、后端 500 等）：带上状态码，不与业务错误混淆
		// 后端 Result 里有 message 时透出来，方便定位（例如 wxlogin 的 400）
		if(code < 200 || code >= 300){
			const backendMsg = res.data && (res.data.message || res.data.msg)
			throw { statusCode: code, message: backendMsg || ('请求失败（' + code + '）') }
		}
		// 后端约定：Java 用 { success, data }，Python 服务用 { code:0, data }
		const ok = res.data.success === true || res.data.code === 0
		if(ok){
			return res.data.data
		}else{
			throw res.data
		}
	}).catch((err) => {
		// 统一错误提示，再抛给调用方；options.silent 时静默（如轮询）
		if(!options.silent){
			uni.showToast({
				title: (err && (err.message || err.msg || err.errMsg)) || '请求失败，请重试',
				icon: 'none'
			})
		}
		throw err
	})
}


