/**
 * 文件流下载工具
 *
 * 导出类接口返回的是二进制流（responseType: 'blob'），
 * 但出错时后端仍然返回 JSON（Result 结构），
 * 所以下载前要先判断流的类型，避免把「没有权限」之类的错误提示当成文件存下来。
 */

/** 判断 blob 是否为后端返回的 JSON 错误体 */
function isJsonBlob(blob) {
  return !!blob && typeof blob.type === 'string' && blob.type.includes('application/json')
}

/** 读取 blob 文本（失败返回空串） */
function readBlobText(blob) {
  if (!blob) return Promise.resolve('')
  if (typeof blob.text === 'function') {
    return blob.text().catch(() => '')
  }
  return new Promise((resolve) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => resolve('')
    reader.readAsText(blob)
  })
}

/**
 * 读取 blob 里的后端错误信息（正常文件返回空串）
 * 用于导出失败时给用户展示真实原因，例如「导出条数超过 5 万」
 */
export async function readBlobErrorMessage(blob) {
  if (!isJsonBlob(blob)) return ''
  const text = await readBlobText(blob)
  if (!text) return ''
  try {
    const json = JSON.parse(text)
    return json?.message || json?.msg || ''
  } catch (e) {
    return ''
  }
}

/**
 * 触发浏览器下载
 * @param {Blob} blob 文件内容
 * @param {string} filename 下载文件名
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 立即回收会让部分浏览器下载中断，延后一拍再释放
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
