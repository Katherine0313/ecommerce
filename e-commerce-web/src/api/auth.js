import axios from './request.js'

// 登录
export function login(data) {
  return axios.post('/api/login', data)
}

// 退出登录
export function logout() {
  return axios.post('/api/logout')
}

// 获取图形验证码（返回 captchaId + base64 图片）
export function getCaptcha() {
  return axios.get('/api/auth/captcha')
}

// 获取当前登录管理员信息（含 adminAvatar 头像字段）
export function getAdminInfo() {
  return axios.get('/api/admin/info')
}
