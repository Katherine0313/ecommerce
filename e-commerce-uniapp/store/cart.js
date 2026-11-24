import { defineStore } from 'pinia'
import { getCartCount } from '../api/cart.js'

// 角标本地缓存键：首页启动时先用缓存显示，避免等登录接口回来才从 0 跳变
const CART_COUNT_KEY = 'cartCount'

// 购物车全局状态：底部 tabBar 购物车图标角标显示的总件数
export const useCartStore = defineStore('cart', {
	state: () => ({
		count: Number(uni.getStorageSync(CART_COUNT_KEY)) || 0, // 所有商品数量之和（不是商品种类数）
		checkoutItems: [] // 去结算的选中商品快照（下单确认页读取）
	}),
	actions: {
		// 直接设置件数，同时落本地缓存供下次启动秒显
		setCount(n) {
			this.count = n
			uni.setStorageSync(CART_COUNT_KEY, n)
		},
		// 设置待结算商品：购物车「去结算」时写入，下单确认页读取
		setCheckoutItems(items) {
			this.checkoutItems = items || []
		},
		// 从后端取购物车总件数（启动/兜底同步，首页角标不需要完整列表）
		async refresh() {
			try {
				const count = await getCartCount()
				this.setCount(Number(count) || 0)
			} catch (e) {
				// 未登录/请求失败保持原值，角标不闪
			}
		},
		// 加购成功后累加件数
		addQuantity(n) {
			this.setCount(this.count + n)
		}
	}
})
