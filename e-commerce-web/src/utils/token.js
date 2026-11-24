// ==================== token 存取工具 ====================
// token 由后端 JWT 生成，前端只负责存取，不依赖 pinia
//
// 存放位置用 sessionStorage（不是 localStorage）：关闭标签页/浏览器即被清空，
// 下次打开必须重新登录 —— 后台管理系统的安全要求（离开工位关掉页面就登出）。
// 同一标签页内刷新仍保持登录，不会打断正在填的表单。
//
// 注意：sessionStorage 按标签页隔离，新开一个标签页也要重新登录，这是预期行为。

const TOKEN_KEY = 'token'

// 清理旧版本遗留在 localStorage 里的 token（改用 sessionStorage 前存的），避免残留
try {
  localStorage.removeItem(TOKEN_KEY)
} catch (e) {
  // 隐私模式等场景 localStorage 不可用，忽略即可
}

/** 读取 token（未登录时返回 null） */
export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

/** 写入 token；传空值等价于清除 */
export function setToken(token) {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(TOKEN_KEY)
  }
}

/** 清除 token（登出用） */
export function removeToken() {
  sessionStorage.removeItem(TOKEN_KEY)
}
