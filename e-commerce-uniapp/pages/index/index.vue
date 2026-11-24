<template>
	<FloatingChat />
	<view class="content">
		<!-- 自定义顶部导航栏：橙色底 + 搜索框。留在滚动区外，固定不动 -->
		<CustomNavbar :showSearch="true" @heightChange="handleNavHeightChange" @search="goSearch" />

		<!-- 滚动区：从导航栏下方开始，下拉刷新动画才不会被固定导航栏盖住；
		     高度必须是个确定值，用 flex 撑会塌陷、refresher 不工作 -->
		<scroll-view class="scroll-body" :scroll-y="true" :refresher-enabled="true" :refresher-triggered="refreshing"
			refresher-default-style="black" refresher-background="#F8F8F8" :style="{ height: scrollHeight }"
			@refresherrefresh="onRefresh">
			<!-- 内层容器只负责给固定定位的底部导航栏留出空间 -->
			<view class="scroll-inner">
				<!-- 首页轮播图：后台配置的 Banner（position=1），没配时用组件自带的默认图 -->
				<CustomSwiper :list="bannerList.length ? bannerList : undefined" @click="handleBannerClick" />

				<!-- 限时秒杀楼层：只有进行中的活动才显示，没有活动时整个楼层隐藏 -->
				<SeckillFloor />

				<!-- 前台分类面板：手机 / 电脑 / 平板 / 耳机音箱 / 手表 / 相机 / 数码配件 / 智能家居 -->
				<CategoryPanel ref="categoryPanel" :list="categories" :limit="8" @select="handleCategorySelect" />
				<!-- 热门推荐：点商品图跳商品详情页；下拉刷新会换这一批商品 -->
				<HotPanel ref="hotPanel" :list="hotList" @select="handleProductSelect" />

				<!-- 猜你喜欢：全局注册，点商品图复用 handleProductSelect 跳详情 -->
				<Guess ref="guess" :list="recommend" @select="handleProductSelect" />
			</view>
		</scroll-view>

		<!-- 自定义底部导航栏 -->
		<CustomTabBar />
	</view>
</template>

<script>
	// 前台分类面板：首页金刚区
	import CategoryPanel from './component/category-panel.vue'
	// 热门推荐
	import HotPanel from './component/hot-panel.vue'
	// 限时秒杀楼层（无进行中活动时自动隐藏）
	import SeckillFloor from './component/seckill-floor.vue'
	import { getHome } from '../../api/home.js'
	import { getHotProducts } from '../../api/product.js'
	import { getBanners } from '../../api/banner.js'
	import { toSwiperItems, handleBannerTap } from '../../utils/banner.js'
	// 分享卡片统一文案：胶囊菜单的「转发」「分享到朋友圈」「收藏」共用
	const SHARE_TITLE = '杰米多多小店｜正品3C数码'
	// 分享封面：用代码包内本地 JPG（微信分享图只支持 JPG/PNG），不受开发环境 127.0.0.1 / https 限制
	// 转发给朋友卡片按 5:4 展示
	const SHARE_IMAGE = '/static/share/share-ipad-5x4.jpg'
	// 朋友圈 / 收藏按 1:1 展示，用正方形图避免被裁
	const SHARE_IMAGE_SQUARE = '/static/share/share-ipad-1x1.jpg'

	export default {
		components: {
			CategoryPanel,
			HotPanel,
			SeckillFloor
		},
		data() {
			return {
				refreshing: false, // 滚动区下拉刷新状态，由 scroll-view 的 refresher-triggered 绑定
				navHeight: 0, // 导航栏总高度（px），由 CustomNavbar 算好后抛上来
				// 聚合接口数据：null 表示还没回来（子组件显示骨架），[] 表示已加载但为空
				categories: null,
				recommend: null,
				// 热门推荐下拉刷新换的一批商品；null 时 HotPanel 用自带的默认 8 个 id
				hotList: null,
				// 首页轮播图：后台配置的 Banner（position=1），空数组时用组件自带的默认图
				bannerList: []
			}
		},
		computed: {
			// 滚动区高度 = 整屏 - 导航栏占位高度，必须是确定值（下拉刷新依赖它）
			scrollHeight() {
				return 'calc(100vh - ' + this.navHeight + 'px)'
			}
		},
		// 胶囊菜单「转发给朋友」：定义了它，胶囊里的「转发」自动可用
		onShareAppMessage() {
			return {
				title: SHARE_TITLE,
				imageUrl: SHARE_IMAGE,
				// 分享当前页（首页无参数）
				path: '/pages/index/index'
			}
		},
		// 胶囊菜单「分享到朋友圈」：定义了它，朋友圈分享卡片自动开启
		onShareTimeline() {
			return {
				title: SHARE_TITLE,
				imageUrl: SHARE_IMAGE_SQUARE,
				// 无额外查询参数；页面路径由微信自动取当前页
				query: ''
			}
		},
		// 胶囊菜单「收藏」：定义了它，胶囊里的「收藏」自动可用
		onAddToFavorites() {
			return {
				title: SHARE_TITLE,
				imageUrl: SHARE_IMAGE_SQUARE,
				// 收藏后再次打开时的页面参数（首页无需携带）
				query: ''
			}
		},
		onLoad() {
			// 页面创建即发请求，比等子组件 mounted 再各发一次更早，也只发一个请求
			this.loadHome()
			// 热门推荐：推荐分类优先，不够随机补足（首屏与下拉刷新共用同一接口）
			this.loadHot()
			// 首页轮播图：后台配置的 Banner
			this.loadBanners()
		},
		methods: {
			// 拉首页聚合数据（分类 + 猜你喜欢），返回 Promise 供下拉刷新等待
			loadHome() {
				return getHome(10).then((data) => {
					this.categories = data.categories || []
					this.recommend = data.recommend || []
				}).catch(() => {
					// 请求封装已统一提示；标记为空，避免骨架屏一直转
					if (this.categories === null) this.categories = []
					if (this.recommend === null) this.recommend = []
				})
			},
			// 接导航栏抛上来的高度，用于算滚动区高度
			handleNavHeightChange(height) {
				this.navHeight = height
			},
			// 拉首页轮播图：后台配置了 Banner 就用后台的，没配置保持组件默认图
			loadBanners() {
				return getBanners(1).then((list) => {
					this.bannerList = toSwiperItems(list)
				}).catch(() => {
					// 报错已在 request 封装里统一提示，这里保留默认图
					this.bannerList = []
				})
			},
			// 点轮播图：统计点击量并按跳转类型跳页面
			handleBannerClick(index, item) {
				handleBannerTap(item)
			},
			// 点顶部搜索框：跳商品搜索页
			goSearch() {
				uni.navigateTo({ url: '/pages/search/search' })
			},
			// 热门推荐：推荐分类优先，不够随机补足
			loadHot() {
				return getHotProducts(8).then((list) => {
					if (list && list.length) this.hotList = list
				}).catch(() => {})
			},
			// 下拉刷新：只换「热门推荐」的 8 个商品位，并至少转圈 600ms 再收起，避免动画一闪而过
			onRefresh() {
				this.refreshing = true
				// 当前展示的 8 个 id 传给后端排除，保证换出来的一批和现在不重复
				const excludeIds = this.$refs.hotPanel ? this.$refs.hotPanel.currentIds() : []
				Promise.all([
					getHotProducts(8, excludeIds).then((list) => {
						// 只有拿到新数据才替换，请求失败保留原列表
						if (list && list.length) this.hotList = list
					}).catch(() => {}),
					new Promise((resolve) => setTimeout(resolve, 600))
				]).then(() => {
					this.refreshing = false
				})
			},
			// 点击分类项：跳分类页（tab 页必须用 switchTab）
			handleCategorySelect(item) {
				// switchTab 不能带 query，把选中分类 id 存 Storage，分类页加载后读它定位
				uni.setStorageSync('selectedCategoryId', item.id)
				uni.switchTab({
					url: '/pages/category/category'
				})
			},
			// 点击商品图：跳商品详情页（独立 page，不再弹加购弹窗）
			handleProductSelect(id) {
				uni.navigateTo({
					url: '/pages/order/detail?id=' + id
				})
			}
		}
	}
</script>

<style>
	.content {
		display: flex;
		flex-direction: column;
		/* 不给高度、也不加 justify-content:center：滚动交给内部 scroll-view，它的高度由导航栏高度算出来。
		   不能加 align-items:center，否则轮播图这类全宽子元素会被压成内容宽（0 宽看不见） */
	}

	/* 滚动区高度走 scrollHeight 内联样式，不要用 flex 撑开：会高度塌陷，下拉刷新不触发 */

	/* 给固定定位的底部导航栏留出空间，内容才不会被压住 */
	.scroll-inner {
		padding-bottom: 120rpx;
	}
</style>
