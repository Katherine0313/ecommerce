import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCart, addCartItem, updateCartItem, removeCartItem, clearCart } from '@/api/cart.js'
import { isAdminToken } from '@/utils/auth'

// 购物车状态仓库，数据存后端数据库（不再用 localStorage）
export const useCartStore
    = defineStore('cart', () => {
  const items = ref([])

  // 从数据库拉取当前用户购物车
  const load = async () => {
    // 后台管理端（admin）没有购物车：商品列表页复用了 C 端 Mall 组件做详情预览，
    // 会带上 Cart/Settlement 连带触发这里的 load()；admin token 调 /api/cart 会被后端
    // 按 user 身份拦截（403）。商家身份直接跳过，避免无意义的报错请求。
    if (isAdminToken()) {
      items.value = []
      return
    }
    try {
      const res = await getCart()
      items.value = res.data.data || []
    } catch (e) {
      console.error('购物车加载失败', e)
    }
  }

  // 购物车总件数
  const totalCount = computed(() =>
    items.value.reduce((sum, i) => sum + i.quantity, 0)
  )

  // 购物车商品种类数
  const itemCount = computed(() => items.value.length)

  // 加入购物车：已存在的商品累加数量，不存在的追加新条目
  const addItem = async (product_id, product, quantity = 1) => {
    const insuranceDetail = product.insurance_detail || []
    const insuranceFee = Number(product.insurance_fee) || 0
    // 本地先更新，界面即时响应
    const exist = items.value.find(i => i.product_id === product_id)
    if (exist) {
      exist.quantity += quantity
      exist.insurance_detail = insuranceDetail
      exist.insurance_fee = insuranceFee
    } else {
      items.value.push({
        product_id,
        name: product.name,
        image: product.image || '',
        price: product.price || 0,
        description: product.description || '',
        quantity,
        insurance_detail: insuranceDetail,
        insurance_fee: insuranceFee
      })
    }

    try {
      await addCartItem({
        product_id,
        name: product.name,
        image: product.image || '',
        price: product.price || 0,
        description: product.description || '',
        quantity,
        insurance_detail: insuranceDetail,
        insurance_fee: insuranceFee
      })
    } catch (e) {
      load() // 失败回滚到数据库真实状态
    }
  }

  // 移除某个商品
  const removeItem = async (product_id) => {
    items.value = items.value.filter(i => i.product_id !== product_id)
    try {
      await removeCartItem(product_id)
    } catch (e) {
      load()
    }
  }

  // 更新某个商品的数量
  const updateQuantity = async (product_id, quantity) => {
    const exist = items.value.find(i => i.product_id === product_id)
    if (!exist) return
    const qty = Math.max(1, quantity)
    exist.quantity = qty
    try {
      await updateCartItem(product_id, { quantity: qty })
    } catch (e) {
      load()
    }
  }

  // 清空购物车
  const clear = async () => {
    items.value = []
    try {
      await clearCart()
    } catch (e) {
      load()
    }
  }

  // 首次使用仓库时加载数据
  load()

  return { items, totalCount, itemCount, addItem, removeItem, updateQuantity, clear }
})
