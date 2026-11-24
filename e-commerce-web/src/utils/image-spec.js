/**
 * 后台图片规格表：按上传 type 给出「目标尺寸 + 适配方式」。
 *
 * 为什么要按 type 分：以前所有图片共用一套「最长边 1200」，但展示位差别很大 ——
 * banner 是宽幅轮播坑（小程序 375×180px / PC 798×350px），商品卡是小方图（177×150px），
 * 正文图是长图。尺寸不贴展示位，就会白下载一堆像素（小程序解码内存直接翻几倍），
 * 画幅不对还会被 object-fit:cover / aspectFill 裁掉内容。
 *
 * 目标尺寸 = 展示像素 × 2（retina），依据见 findings.md「图片规格表」一节。
 * 修改这里时记得同步改各页面上的「建议尺寸」提示（由 specLabel 生成）。
 */

// 统一输出 webp：同画质比 jpeg 小约 30%，且保留 PNG 透明通道
// （分类图标这类带透明底的图，转 jpeg 会被压成黑底）
const MIME = 'image/webp'

// 输出质量：webp 的 0.82 与 jpeg 的 0.8 观感接近
const QUALITY = 0.82

export const IMAGE_SPEC = {
  /** Banner 轮播图：按广告位置分档，一条 banner 只服务一个位置 */
  banner: {
    byPosition: {
      1: { width: 750, height: 375, fit: 'cover' },   // 小程序首页 swiper 375×180px
      2: { width: 750, height: 200, fit: 'cover' },   // 小程序分类页 swiper 375×100px
      11: { width: 1600, height: 700, fit: 'cover' }, // PC 商城首页 hero 798×350px
    },
    fallback: { width: 750, height: 375, fit: 'cover' },
  },
  /** 文章封面：PC 详情页铺满 1200 容器，16:9 */
  article: { width: 1200, height: 675, fit: 'cover' },
  /** 分类图标：小方图 */
  category: { width: 400, height: 400, fit: 'cover' },
  /** 商品主图：800×800 白底方，与 python 脚本 to_square() 口径一致 */
  upload: { width: 800, height: 800, fit: 'contain', background: '#ffffff' },
  /** 富文本正文图：满宽展示，限制最长边 */
  body: { maxEdge: 1200, fit: 'contain' },
  /** 售后凭证图：列表缩略只有 88px，但点开预览要看清晰 */
  'after-sale': { maxEdge: 1200, fit: 'contain' },
  /** 评价晒图：详情页缩略图只有 ~100px，点开看大图，限制最长边 800 足够 */
  review: { maxEdge: 800, fit: 'contain' },
  /** 快递公司 logo：后台列表缩略 48px，白底方图，别压太多 */
  express: { width: 120, height: 120, fit: 'contain', background: '#ffffff' },
}

/**
 * 取某个 type 的压缩规格。
 *
 * @param {string} type 上传的 type（upload/body/category/banner/article/after-sale/review/express）
 * @param {number} [position] banner 用：广告位置，决定目标画幅
 * @returns {object|null} 规格对象；type 未知返回 null（调用方应走默认压缩）
 */
export function getImageSpec(type, position) {
  const entry = IMAGE_SPEC[type]
  if (!entry) return null
  const size = entry.byPosition ? (entry.byPosition[position] || entry.fallback) : entry
  if (!size) return null
  return {
    mime: MIME,
    quality: QUALITY,
    fit: 'contain',
    background: '#ffffff',
    ...size,
  }
}

/**
 * 生成「建议尺寸 xxx」文案，用于表单提示。
 *
 * @param {object} spec getImageSpec 的返回值
 * @returns {string}
 */
export function specLabel(spec) {
  if (!spec) return ''
  if (spec.width && spec.height) return `${spec.width} × ${spec.height}`
  if (spec.maxEdge) return `最长边 ${spec.maxEdge}`
  return ''
}