/**
 * 手机号脱敏：仅用于后台页面展示，避免公开演示环境泄露真实用户手机号。
 * 展示层脱敏，不改变请求/提交的数据。
 */
const MOBILE_RE = /^1[3-9]\d{9}$/

export function maskPhone(value) {
  if (value === null || value === undefined) return ''
  const s = String(value).trim()
  if (!s) return ''
  // 11 位手机号：保留前 3 后 4，如 138****5678
  if (MOBILE_RE.test(s)) return `${s.slice(0, 3)}****${s.slice(7)}`
  // 其他号码（固话/带区号等）：保留首尾各 2 位，中间用 * 覆盖
  if (s.length <= 4) return '*'.repeat(s.length)
  return `${s.slice(0, 2)}${'*'.repeat(s.length - 4)}${s.slice(-2)}`
}
