// 图片懒加载全局指令 v-lazy：元素滚入视口才加载真实 src，避免列表缩略图全量请求
// 用法：<img v-lazy="url" />  或  <img :data-src="url" v-lazy="url" />
// 说明：antd 的 <a-image> 不透传 loading="lazy"，故统一用本指令（用于原生 <img>）

// 占位图：1 个浅灰 80x80 SVG，加载前显示灰块，避免出现裂图图标
const PLACEHOLDER =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="100%" height="100%" fill="#f0f0f0"/></svg>'
  )

// 真实 src 存 WeakMap，卸载时自动回收，不污染 DOM 属性
const srcMap = new WeakMap()

// 全站共用一个 observer，避免每个图片各建一个
const observer =
  typeof IntersectionObserver !== 'undefined'
    ? new IntersectionObserver(onIntersect, { rootMargin: '120px' })
    : null

function onIntersect(entries) {
  entries.forEach((entry) => {
    if (!entry.isIntersecting) return
    const el = entry.target
    const src = srcMap.get(el)
    if (src) el.src = src
    observer.unobserve(el)
  })
}

export default {
  mounted(el, binding) {
    const src = binding.value
    srcMap.set(el, src || '')
    if (!src) return
    if (!observer) {
      // 不支持 IntersectionObserver 的环境（极老浏览器）：直接加载，保证可用
      el.src = src
      return
    }
    el.src = PLACEHOLDER
    observer.observe(el)
  },
  updated(el, binding) {
    const src = binding.value
    if (!src) return
    srcMap.set(el, src)
    // 已经加载过（src 不再是占位图）时，数据变化直接同步 src
    if (!observer || el.src !== PLACEHOLDER) {
      el.src = src
    }
  },
  unmounted(el) {
    observer && observer.unobserve(el)
  },
}
