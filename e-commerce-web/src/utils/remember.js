// ==================== 后台登录「记住密码」 ====================
// 只用于登录页回填账号密码，不参与鉴权（鉴权仍走 token）。
// 注意：浏览器本地存储本质上都可被同源 JS 读取，这里做 Base64 + 反转只是避免明文直读，
// 不能替代真正的加密；30 天过期 + 退出可清除，降低长期泄露风险。

const REMEMBER_KEY = 'admin_remember_login'
const REMEMBER_TTL = 30 * 24 * 60 * 60 * 1000 // 30 天

function encode(data) {
  const json = JSON.stringify(data)
  const base64 = btoa(unescape(encodeURIComponent(json)))
  return base64.split('').reverse().join('')
}

function decode(text) {
  try {
    const base64 = text.split('').reverse().join('')
    return JSON.parse(decodeURIComponent(escape(atob(base64))))
  } catch {
    return null
  }
}

/** 保存账号密码（登录成功后调用） */
export function saveRemember(username, password) {
  localStorage.setItem(REMEMBER_KEY, encode({
    username,
    password,
    expireAt: Date.now() + REMEMBER_TTL,
  }))
}

/** 读取记住的账号密码；过期/损坏自动清除并返回 null */
export function loadRemember() {
  const text = localStorage.getItem(REMEMBER_KEY)
  if (!text) return null
  const data = decode(text)
  if (!data || !data.username || !data.password || !data.expireAt || data.expireAt < Date.now()) {
    clearRemember()
    return null
  }
  return data
}

/** 清除记住的账号密码（取消勾选或主动清除时调用） */
export function clearRemember() {
  localStorage.removeItem(REMEMBER_KEY)
}
