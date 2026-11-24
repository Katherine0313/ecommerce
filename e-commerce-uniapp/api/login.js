import { request, BASE_URL } from '../utils/server-api.js'
import { getToken } from '../utils/token.js'

// ===== 登录 / 用户接口 =====
// 这些接口需要登录态，登录流程走 utils/auth.js 的 ensureLogin。

// 微信登录：用 code 换 token 和用户信息（单店模式）
export function wxLogin(code){
	return request('/api/wxlogin', {
		method: 'POST',
		data: { code }
	})
}

// 获取当前登录用户资料（含头像），对应 GET /api/user/profile
export function getProfile(){
	return request('/api/user/profile',{method:'GET'})
}

// 绑定手机号：用 getPhoneNumber 授权拿到的 code 换手机号，对应 POST /api/user/phone
export function bindPhone(code){
	return request('/api/user/phone', {
		method: 'POST',
		data: { code }
	})
}

// 注销账号：对应 POST /api/user/deactivate
// 后端把账号软删（is_deleted=1）并清空购物车，同一事务；不可恢复
export function deactivateAccount(){
	return request('/api/user/deactivate', {
		method: 'POST'
	})
}

// 设置昵称：对应 POST /api/user/name，返回去除首尾空格后的昵称
export function updateName(name){
	return request('/api/user/name', {
		method: 'POST',
		data: { name }
	})
}

// 上传头像：uni.uploadFile 调 C 端上传接口 /api/upload，返回图片相对路径（/image/upload/uuid.webp）
// 拿到 URL 后调用 saveAvatar 存进 user.avatar，完整链路见 onChooseAvatar
export function uploadAvatar(filePath){
	return new Promise((resolve, reject) => {
		uni.uploadFile({
			url: BASE_URL + '/api/upload?type=avatar',
			filePath: filePath,
			name: 'file',
			header:{
				Authorization: 'Bearer ' + getToken()
			},
			success:(res) => {
				const data = JSON.parse(res.data)
				if(data.success){
					// UploadVO.url 是相对路径如 /image/upload/uuid.webp
					resolve(data.data.url || data.data)
				}else{
					reject(data)
				}
			},
			fail:(err) => {
				reject(err)
			}
		})
	})
}

// 存头像 URL 到 user.avatar：对应 POST /api/user/avatar（JSON body），返回入库后的路径
export function saveAvatar(url){
	return request('/api/user/avatar', {
		method: 'POST',
		data: { avatar: url }
	})
}
