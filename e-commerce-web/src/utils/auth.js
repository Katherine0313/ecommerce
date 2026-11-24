// ==================== 登录态 / 用户角色工具 ====================
// token 由后端 JWT 生成，payload 内含 { id, username, role }
// 前端只存了 token，没有单独存 role，这里直接从 token 里解析出来

import { getToken } from '@/utils/token';

/** 从 JWT token 中解析出当前登录用户信息 */
export function getCurrentUser() {
  try {
    const token = getToken()
    if (!token) return null
    const payload = token.split('.')[1]
    // base64url → base64（JWT 用 -_ 代替 +/）
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    // 正确处理含中文的 payload（避免 atob 直接转成乱码）
    const json = decodeURIComponent(
      atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
    )
    return JSON.parse(json)
  } catch (e) {
    return null
  }
}

/** 当前令牌是否为后台管理员令牌（admin 端所有角色） */
export function isAdminToken() {
  return getCurrentUser()?.type === 'admin'
}

/** 当前登录用户是否为超级管理员（店铺管理员） */
export function isAdmin() {
  const user = getCurrentUser()
  return (user?.roles || []).includes('admin')
      || (user?.permissions || []).includes('*:*:*')
}

/** 是否拥有某个权限标识，例如 product:product:list */
export function hasPermi(permission) {
  const user = getCurrentUser()
  if (!user) return false
  // 历史 token 可能没有 permissions 字段，这里兜底成空数组
  const permissions = Array.isArray(user.permissions) ? user.permissions : []
  return permissions.includes('*:*:*') || permissions.includes(permission)
}

/** 是否拥有任意一个权限 */
export function hasAnyPermi(permissions) {
  const list = Array.isArray(permissions) ? permissions : [permissions]
  return list.some((permission) => hasPermi(permission))
}
