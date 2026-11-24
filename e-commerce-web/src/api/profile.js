import axios from './request.js'

/** 获取自己的完整资料 */
export function getMyProfile() {
  return axios.get('/api/admin/profile')
}

/** 修改自己的资料（name / phone / adminAvatar，null 字段不更新） */
export function updateMyProfile(data) {
  return axios.put('/api/admin/profile', data)
}

/** 修改自己的密码 */
export function updateMyPassword(data) {
  return axios.put('/api/admin/profile/password', data)
}

/** 自己的登录日志，limit 默认 10 */
export function getMyLoginLogs(limit = 10) {
  return axios.get('/api/admin/profile/logins', { params: { limit } })
}
