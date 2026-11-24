import { ref, unref, onBeforeUnmount, watch } from 'vue'

/**
 * 秒杀倒计时 composable。
 * 用法：const { remainText, remainMs, isEnded } = useCountdown(target)
 * target 支持：时间戳（数字）/ "yyyy-MM-dd HH:mm:ss" 字符串 / 上述值的 ref 或 computed；
 * 传 null / 空则不计时（remainText 返回空串，isEnded 返回 true）。
 */
export function useCountdown(target) {
  const remainMs = ref(0)
  const now = ref(Date.now())
  let timer = null

  // 兼容 iOS Safari：把 "yyyy-MM-dd HH:mm:ss" 的横杠换成斜杠再解析
  function parseTime(t) {
    if (t === null || t === undefined || t === '') return 0
    if (typeof t === 'number') return t
    return new Date(String(t).replace(/-/g, '/')).getTime()
  }

  function tick() {
    const end = parseTime(unref(target))
    if (!end) {
      remainMs.value = 0
      return
    }
    const left = Math.max(0, end - now.value)
    remainMs.value = left
    if (left <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }

  timer = setInterval(() => {
    now.value = Date.now()
    tick()
  }, 1000)
  tick()

  // 目标时间变化（换活动 / 列表刷新）时重新起算
  watch(
    () => unref(target),
    () => {
      if (!timer) {
        timer = setInterval(() => {
          now.value = Date.now()
          tick()
        }, 1000)
      }
      tick()
    }
  )

  onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
    timer = null
  })

  // 剩余时间文案：不足 1 小时只显示分秒，否则显示 时:分:秒
  function remainText() {
    const left = remainMs.value
    if (left <= 0) return ''
    const totalSec = Math.floor(left / 1000)
    const h = Math.floor(totalSec / 3600)
    const m = Math.floor((totalSec % 3600) / 60)
    const s = totalSec % 60
    const pad = (n) => String(n).padStart(2, '0')
    return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
  }

  function isEnded() {
    return remainMs.value <= 0
  }

  return { remainMs, remainText, isEnded }
}
