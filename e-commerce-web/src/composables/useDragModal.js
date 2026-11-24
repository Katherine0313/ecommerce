import { onBeforeUnmount } from 'vue'

/**
 * 让 Ant Design Vue 的 modal 可以通过标题栏拖动
 * @param {string} wrapClassName - modal 的唯一 wrapClassName，用于定位
 */
export function useDragModal(wrapClassName) {
  let isDragging = false
  let startX = 0
  let startY = 0
  let startLeft = 0
  let startTop = 0

  const onMouseDown = (e) => {
    if (!e.target.closest('.ant-modal-header')) return
    const modal = document.querySelector(`.${wrapClassName} .ant-modal`)
    if (!modal) return

    isDragging = true
    startX = e.clientX
    startY = e.clientY
    const rect = modal.getBoundingClientRect()
    startLeft = rect.left
    startTop = rect.top

    modal.style.position = 'absolute'
    modal.style.margin = '0'
    modal.style.left = startLeft + 'px'
    modal.style.top = startTop + 'px'

    // 防止拖拽时选中文字
    e.preventDefault()
  }

  const onMouseMove = (e) => {
    if (!isDragging) return
    const modal = document.querySelector(`.${wrapClassName} .ant-modal`)
    if (!modal) return
    modal.style.left = (startLeft + e.clientX - startX) + 'px'
    modal.style.top = (startTop + e.clientY - startY) + 'px'
  }

  const onMouseUp = () => {
    isDragging = false
  }

  document.addEventListener('mousedown', onMouseDown)
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)

  onBeforeUnmount(() => {
    document.removeEventListener('mousedown', onMouseDown)
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  })
}
