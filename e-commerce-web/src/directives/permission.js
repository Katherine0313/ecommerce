import { message } from 'ant-design-vue'
import { hasPermi } from '@/utils/auth'

/**
 * 按钮级权限指令：v-permission="'order:order:ship'"
 *
 * 无权限时按钮外观保持正常（不置灰、不禁用），点击时拦截并提示「无权限」，
 * 这样只读账号也能看到完整界面，明确知道存在该操作但不能执行。
 * 真正的拦截仍由后端 @RequirePerm 完成。
 */
export default {
  mounted(el, binding) {
    applyIfNoPermission(el, binding.value)
  },
  updated(el, binding) {
    applyIfNoPermission(el, binding.value)
  },
}

function applyIfNoPermission(el, perm) {
  if (!perm || hasPermi(perm)) return
  if (el.__permGuarded) return
  el.__permGuarded = true
  el.addEventListener(
    'click',
    (e) => {
      e.preventDefault()
      e.stopPropagation()
      message.warning('无权限，无法执行该操作')
    },
    true
  )
}
