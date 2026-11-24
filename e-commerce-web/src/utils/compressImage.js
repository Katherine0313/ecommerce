/**
 * 前端图片压缩：上传前把图片压到「展示场景」需要的规格，避免列表/详情页直接加载原图卡顿。
 *
 * 用法：
 *   compressImage(file)                              // 不传规格 → 默认行为（最长边 1200 / jpeg q0.8）
 *   compressImage(file, getImageSpec('banner', 1))   // 按 type / banner 广告位置取规格
 *
 * 各 type 的目标规格见 utils/image-spec.js。
 */

// 不传 spec 时的兜底规格，等价于改造前的行为（最长边 1200、jpeg q0.8）
const DEFAULT_SPEC = { maxEdge: 1200, mime: 'image/jpeg', quality: 0.8, fit: 'contain' }

// webp 不支持动图，gif 原样上传（后端白名单本来就允许 image/gif）
const GIF_MIME = 'image/gif'

// 尺寸已经达标、体积又不大时原样返回：避免「压一遍反而变大或掉画质」
const KEEP_AS_IS_MAX_BYTES = 200 * 1024

/**
 * 压缩图片，返回 Promise<File>：
 * - 非图片 / gif 动图 → 原样返回
 * - 压缩失败 → 兜底返回原图，保证上传不被中断
 *
 * @param {File} file 待上传文件
 * @param {object} [spec] 目标规格，见 utils/image-spec.js
 * @returns {Promise<File>}
 */
export function compressImage(file, spec = DEFAULT_SPEC) {
  if (!file || !file.type.startsWith('image/') || file.type === GIF_MIME) {
    return Promise.resolve(file)
  }
  const target = spec || DEFAULT_SPEC
  return decode(file)
    .then((img) => {
      const w0 = img.naturalWidth || img.width
      const h0 = img.naturalHeight || img.height
      if (!w0 || !h0) return null
      const plan = planDraw(w0, h0, target)
      if (!plan) return null
      // 尺寸不变且体积不大 → 原图已经够好，别再折腾
      if (plan.canvasWidth === w0 && plan.canvasHeight === h0 && file.size <= KEEP_AS_IS_MAX_BYTES) {
        return null
      }
      const canvas = document.createElement('canvas')
      canvas.width = plan.canvasWidth
      canvas.height = plan.canvasHeight
      const ctx = canvas.getContext('2d')
      if (!ctx) return null
      // 先铺白底：webp 保留透明，jpeg 不支持透明会把它压成黑底
      ctx.fillStyle = target.background || '#ffffff'
      ctx.fillRect(0, 0, plan.canvasWidth, plan.canvasHeight)
      ctx.drawImage(img, plan.dx, plan.dy, plan.dw, plan.dh)
      return encode(canvas, file.name, target)
    })
    .then((out) => out || file)
    .catch(() => file)
}

/**
 * 解码图片。
 * 用 createObjectURL 而不是 FileReader 转 base64：base64 会把文件撑大 1/3，
 * 5MB 的原图先变 6.7MB 字符串再解码，浏览器内存吃紧。
 */
function decode(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = (err) => {
      URL.revokeObjectURL(url)
      reject(err)
    }
    img.src = url
  })
}

/**
 * 算画布尺寸与绘制参数。
 *
 * cover   —— 填满目标画幅，按较大的缩放比铺开、多余部分裁掉，裁切居中。
 *            画幅正确优先，运营按建议尺寸传图就不会触发放大。
 * contain —— 等比缩放塞进目标画布，居中留白、不裁内容、不放大。
 */
function planDraw(w0, h0, spec) {
  // 指定了目标画幅（width + height）
  if (spec.width && spec.height) {
    if (spec.fit === 'cover') {
      const scale = Math.max(spec.width / w0, spec.height / h0)
      const dw = w0 * scale
      const dh = h0 * scale
      return {
        canvasWidth: spec.width,
        canvasHeight: spec.height,
        dx: (spec.width - dw) / 2,
        dy: (spec.height - dh) / 2,
        dw,
        dh,
      }
    }
    const scale = Math.min(spec.width / w0, spec.height / h0, 1)
    const dw = w0 * scale
    const dh = h0 * scale
    return {
      canvasWidth: spec.width,
      canvasHeight: spec.height,
      dx: (spec.width - dw) / 2,
      dy: (spec.height - dh) / 2,
      dw,
      dh,
    }
  }
  // 只限制最长边：等比缩到最长边不超过限制，画布就是缩放后的尺寸
  if (spec.maxEdge) {
    const scale = Math.min(1, spec.maxEdge / Math.max(w0, h0))
    const dw = Math.max(1, Math.round(w0 * scale))
    const dh = Math.max(1, Math.round(h0 * scale))
    return { canvasWidth: dw, canvasHeight: dh, dx: 0, dy: 0, dw, dh }
  }
  return null
}

/**
 * 编码画布并包成 File。
 * 浏览器不支持 webp 编码时，toBlob 会按规范静默回落成 image/png —— 这里当成「不支持」，
 * 改用 jpeg 重新编码，别把 png 白白传上去。
 */
function encode(canvas, fileName, spec) {
  const quality = spec.quality == null ? 0.82 : spec.quality
  const want = spec.mime || 'image/webp'
  return toBlob(canvas, want, quality)
    .then((blob) => (blob ? blob : toBlob(canvas, 'image/jpeg', quality)))
    .then((blob) => {
      if (!blob) return null
      const ext = blob.type === 'image/webp' ? '.webp' : '.jpg'
      return new File([blob], renameExtension(fileName, ext), { type: blob.type })
    })
}

/** toBlob 编码；浏览器不支持该 mime 时返回 null */
function toBlob(canvas, mime, quality) {
  return new Promise((resolve) => {
    if (!canvas.toBlob) {
      resolve(null)
      return
    }
    canvas.toBlob((blob) => {
      resolve(blob && blob.type === mime ? blob : null)
    }, mime, quality)
  })
}

/** 换扩展名，保留原文件名主干 */
function renameExtension(name, ext) {
  const base = (name || 'image').replace(/\.[^.]+$/, '')
  return base + ext
}