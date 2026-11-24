<template>
	<!-- 底部导航栏：整条由本组件渲染，图标用 iconfont 字体，矢量任意倍率不糊 -->
	<view class="tabbar">
		<view class="tab-item" :class="{ active: activeIndex === 0 }" @click="handleSwitch(0)">
			<text class="iconfont icon-shouyexianxing"></text>
			<text class="tab-text">首页</text>
		</view>
		<view class="tab-item" :class="{ active: activeIndex === 1 }" @click="handleSwitch(1)">
			<text class="iconfont icon-fenlei"></text>
			<text class="tab-text">分类</text>
		</view>
		<view class="tab-item" :class="{ active: activeIndex === 2 }" @click="handleSwitch(2)">
			<view class="icon-wrap">
				<text class="iconfont icon-gouwuche"></text>
				<!-- 购物车件数角标：超过 99 显示 99+，0 件不显示 -->
				<text v-if="cartCount > 0" class="cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</text>
			</view>
			<text class="tab-text">购物车</text>
		</view>
		<view class="tab-item" :class="{ active: activeIndex === 3 }" @click="handleSwitch(3)">
			<text class="iconfont icon-wode"></text>
			<text class="tab-text">我的</text>
		</view>
	</view>
</template>

<script>
	// 购物车全局状态：底部购物车图标的件数角标从这读
	import {
		useCartStore
	} from '../../../store/cart.js'

	// 与 pages.json 的 tabBar.list 顺序一一对应，switchTab 和路由匹配都从这里取
	const PATHS = ['/pages/index/index', '/pages/category/category', '/pages/cart/cart', '/pages/my/my']

	export default {
		name: 'CustomTabBar',
		props: {
			// 当前选中项，不传就按当前页面路径自动判断
			current: {
				type: [Number, String],
				default: ''
			}
		},
		data() {
			return {
				autoIndex: 0 // 页面没传 current 时，按路由算出的选中项
			}
		},
		computed: {
			activeIndex() {
				return this.current === '' ? this.autoIndex : Number(this.current)
			},
			// 购物车角标件数：读全局 cart store，加购/购物车页改动都会自动同步到这里
			cartCount() {
				return useCartStore().count
			}
		},
		mounted() {
			this.autoIndex = this.matchCurrentPage()
		},
		methods: {
			// 每个页面的组件实例一一对应，算一次就固定，不必在 onShow 里刷新
			matchCurrentPage() {
				const pages = getCurrentPages()
				if (!pages.length) {
					return 0
				}
				const idx = PATHS.indexOf('/' + pages[pages.length - 1].route)
				return idx < 0 ? 0 : idx
			},
			// 必须用 switchTab，navigateTo 不会激活 tab 页
			handleSwitch(idx) {
				if (idx === this.activeIndex) {
					return
				}
				uni.switchTab({
					url: PATHS[idx]
				})
			}
		}
	}
</script>

<style scoped>
	.tabbar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 999;
		display: flex;
		background: #ffffff;
		/* 适配 iPhone 底部安全区 */
		padding-bottom: env(safe-area-inset-bottom);
	}

	.tab-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		height: 100rpx;
	}

	/* 图标容器：相对定位，角标绝对定位叠在图标右上角 */
	.icon-wrap {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	/* 购物车件数角标：红底白字，吸在图标右上角 */
	.cart-badge {
		position: absolute;
		top: -10rpx;
		right: -18rpx;
		min-width: 30rpx;
		height: 30rpx;
		padding: 0 8rpx;
		border-radius: 15rpx;
		background: #ff4d4f;
		color: #ffffff;
		font-size: 18rpx;
		line-height: 30rpx;
		text-align: center;
		box-sizing: border-box;
	}

	/* 图标：iconfont 自带的 16px(≈32rpx) 偏小，这里放大 */
	.tab-item .iconfont {
		font-size: 40rpx;
		line-height: 1;
	}

	.tab-text {
		margin-top: 6rpx;
		font-size: 20rpx;
		line-height: 1;
	}

	/* 配色只在这两处，改主题色动这里就行 */
	.tab-item .iconfont,
	.tab-text {
		color: #000;

	}

	.tab-item.active .iconfont,
	.tab-item.active .tab-text {
		color: #FF7D00;
	}
</style>
