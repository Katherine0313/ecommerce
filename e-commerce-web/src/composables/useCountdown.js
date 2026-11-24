import {ref, onBeforeUnmount} from 'vue'

// 待付款订单超时关闭时长（秒），与后端 server/constants/orderStatus.js 的 PENDING_PAY_TIMEOUT_SECONDS 保持一致
export const PAY_TIMEOUT_SECONDS = 1800

/**
 * 倒计时 composable：每秒刷新当前时间，提供剩余秒数计算与文案格式化
 * @returns {{ remainingSeconds: Function, formatCountdown: Function }}
 */
export function useCountdown() {
  // 当前时间（秒），每秒刷新一次驱动倒计时
  const now = ref(Math.floor(Date.now() / 1000))
  const timer = setInterval(() => {
    now.value = Math.floor(Date.now() / 1000)
  }, 1000)
  onBeforeUnmount(() => clearInterval(timer))

  // created_at（"YYYY-MM-DD HH:mm:ss" 本地时间）→ Unix 秒
  const parseCreatedAt = (str) => {
    if (!str) return null
    const t = new Date(String(str).replace(' ', 'T')).getTime()
    return Number.isFinite(t) ? Math.floor(t / 1000) : null
  }

  // 剩余秒数
  const remainingSeconds = (createdAt) => {
    const created = parseCreatedAt(createdAt)
    if (created == null) return 0
    return created + PAY_TIMEOUT_SECONDS - now.value
  }

  // 文案：MM:SS，超时显示「已超时」
  const formatCountdown = (createdAt) => {
    const sec = remainingSeconds(createdAt)
    if (sec <= 0) return '已超时'
    const m = Math.floor(sec / 60)
    const s = sec % 60
    return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
  }

  return { remainingSeconds, formatCountdown }
}
