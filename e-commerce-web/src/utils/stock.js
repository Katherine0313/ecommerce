/**
 * 库存工具：规格格式化 + SKU 状态判定。
 * ProductSku.specJson 可能是 JSON 字符串或已经解析过的对象数组。
 */

/**
 * 把 specJson 格式化成 "曜石黑/128G" 这样的可读文本。
 * 兼容三种形态：
 *   1. JSON 字符串 '{"颜色":"曜石黑","存储":"128G"}'
 *   2. 对象 { "颜色": "曜石黑", "存储": "128G" }
 *   3. 数组 [{ name: "颜色", value: "曜石黑" }, ...]
 */
export function formatSpec(specJson) {
  if (!specJson) return ''
  let obj = specJson
  if (typeof specJson === 'string') {
    try {
      obj = JSON.parse(specJson)
    } catch {
      return specJson
    }
  }
  if (Array.isArray(obj)) {
    return obj.map(i => i.value || i.name || i).filter(Boolean).join(' / ')
  }
  if (obj && typeof obj === 'object') {
    return Object.values(obj).filter(Boolean).join(' / ')
  }
  return String(obj)
}

/**
 * 根据 SKU 当前库存与上下限计算状态。
 * @param {Object} sku - sku 对象，包含 stock / minStock / maxStock
 * @returns {{ color: string, text: string }} color 可直接给 a-tag 用
 */
export function skuStatus(sku) {
  const stock = Number(sku?.stock) || 0
  const min = Number(sku?.minStock)
  const max = Number(sku?.maxStock)

  if (stock <= 0) return { color: 'red', text: '售罄' }

  // 只配了下限：低于下限算低库存，高于下限算正常
  if (min != null && !Number.isNaN(min) && (max == null || Number.isNaN(max))) {
    if (stock < min) return { color: 'orange', text: '低库存' }
    return { color: 'green', text: '正常' }
  }
  // 只配了上限（不太常见，但还是处理一下）
  if (max != null && !Number.isNaN(max) && (min == null || Number.isNaN(min))) {
    if (stock > max) return { color: 'blue', text: '高库存' }
    return { color: 'green', text: '正常' }
  }
  // 上下限都配齐了
  if (min != null && !Number.isNaN(min) && max != null && !Number.isNaN(max)) {
    if (stock < min) return { color: 'orange', text: '低库存' }
    if (stock > max) return { color: 'blue', text: '高库存' }
    return { color: 'green', text: '正常' }
  }
  // 没配阈值：只分售罄/正常
  return { color: 'green', text: '正常' }
}
