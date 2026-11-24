<template>
<view class="page">
		<!-- 自定义顶部导航栏：搜索框 -->
		<CustomNavbar :showSearch="true" @heightChange="handleNavHeightChange" @search="goSearch" />

		<!-- 分类主体：左侧一级分类 + 右侧商品，高度由导航栏算出来（scroll-view 需确定高度） -->
		<view class="category-body" :style="{ height: bodyHeight }">
			<!-- 左侧一级分类：竖排，选中白底 + 橙色竖条 -->
			<scroll-view class="sidebar" scroll-y>
				<view
					v-for="(item, index) in categories"
					:key="item.id"
					class="sidebar-item"
					:class="{ active: index === activeIndex }"
					@click="handleSelect(index)"
				>
					<text class="sidebar-name">{{ item.name }}</text>
				</view>
			</scroll-view>

			<!-- 右侧商品：网格，随左侧选中联动；滚到底部触发上拉加载下一页 -->
			<scroll-view class="main" scroll-y @scrolltolower="loadMore">
				<!-- 顶部轮播图：后台配置的 Banner（position=2），没配时用组件自带的默认图 -->
				<view class="swiper-wrap">
					<CustomSwiper height="200rpx" :list="bannerList.length ? bannerList : undefined" @click="handleBannerClick" />
				</view>
				<!-- 商品网格：每行两个，卡片是 图片 + 名称 + 价格 + 购物车图标 -->
				<view class="goods-grid">
					<view class="goods-card" v-for="item in currentProducts" :key="item.id" @click="handleProductClick(item)">
						<image lazy-load class="goods-img" :src="imgUrl(item)" mode="aspectFill"></image>
						<!-- 促销标签：压在商品图左上角 -->
						<text v-if="item.promotionTag" class="promo-tag">{{ item.promotionTag }}</text>
						<view class="goods-info">
							<text class="goods-name">{{ item.name }}</text>
							<view class="goods-bottom">
								<view class="price-wrap">
									<text class="goods-price">¥{{ item.price }}</text>
									<text v-if="item.originalPrice" class="origin-price">¥{{ item.originalPrice }}</text>
								</view>
								<text class="goods-cart iconfont icon-gouwuche1"></text>
							</view>
						</view>
					</view>
				</view>
				<!-- 分页底部提示：加载中 / 没有更多 / 暂无商品 -->
				<view v-if="loading" class="list-tip">加载中…</view>
				<view v-else-if="currentProducts.length && !hasMore" class="list-tip">没有更多了</view>
				<view v-else-if="!currentProducts.length && categories.length" class="list-tip">暂无商品</view>
			</scroll-view>
		</view>


		<!-- 自定义底部导航栏 -->
		<CustomTabBar />
	</view>
</template>

<script>
	import { getCategories } from '../../api/categories.js'
	import { getProductPage } from '../../api/product.js'
	import { getBanners } from '../../api/banner.js'
	import { toSwiperItems, handleBannerTap } from '../../utils/banner.js'
	import { BASE_URL } from '../../utils/server-api.js'

	// 分类页每页条数：两列网格，12 条正好 6 行
	const PAGE_SIZE = 12

	export default {
		data() {
			return {
				activeIndex: 0, // 当前选中一级分类下标，右侧商品跟着它联动
				navHeight: 0, // 导航栏总高度（px），由 CustomNavbar 抛上来
				// 一级父分类：来自后端 GET /api/categories（树形，children 是二级分类）
				categories: [],
				// 当前父类名下商品：按选中的一级分类分页加载（GET /api/products/page）
				currentProducts: [],
				// 分页状态：当前页 / 总条数 / 是否还有下一页 / 是否请求中
				page: 1,
				total: 0,
				hasMore: false,
				loading: false,
				// 首屏缓存：{ [categoryId]: { list, total, expireAt } }，只缓存第一页，来回切分类先秒显再续拉
				productCache: {},
				// 请求序号：快速切分类时只应用最后一次请求的结果
				loadSeq: 0,
				// 分类页顶部轮播图：后台配置的 Banner（position=2），空数组时用组件默认图
				bannerList: [],
			}
		},
		computed: {
			// 分类主体高度 = 整屏 - 导航栏占位高度，必须是确定值
			bodyHeight() {
				return 'calc(100vh - ' + this.navHeight + 'px)'
			}
		},
		mounted() {
			this.fetchCategories()
			// 分类页顶部轮播图
			this.loadBanners()
		},
		onShow() {
			// 从首页分类面板切回来时页面已加载，重新定位选中分类并加载其商品；
			// 分类没变且已有数据时不重刷，避免从商品详情页返回时打断已翻到的分页列表
			const prevIndex = this.activeIndex
			this.applySelectedCategory()
			if (this.activeIndex !== prevIndex || !this.currentProducts.length) {
				this.loadProducts()
			}
		},
		methods: {
			// 拉分类：后端返回树形列表，每个元素就是一级父分类，children 是它的二级分类
			fetchCategories() {
				getCategories().then((list) => {
					// 一级父分类：只展示/筛选一级，二级分类由用户后续自行扩展
					this.categories = (list || []).map((item) => {
						return {
							id: item.id,
							name: item.name,
							icon: this.imgUrl(item)
						}
					})
					// 若从首页分类面板带过来选中分类，定位到对应下标
					this.applySelectedCategory()
					// 分类就绪后按当前父类懒加载商品
					this.loadProducts()
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				})
			},
			// 拉分类页顶部轮播图：后台配置了 Banner 就用后台的，没配置保持组件默认图
			loadBanners() {
				return getBanners(2).then((list) => {
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
			// 切换分类 / 首次进入：重置分页并加载第一页（命中首屏缓存则先显示）
			loadProducts() {
				const cat = this.categories[this.activeIndex]
				if (!cat) {
					this.resetList()
					return
				}
				const filterId = cat.id
				// 命中首屏缓存：直接显示第一页，后续靠上拉续拉；60 秒过期，库存/价格变更能较快反映
				const cached = this.productCache[filterId]
				if (cached && Date.now() < cached.expireAt) {
					this.loadSeq++ // 作废进行中的旧请求
					this.currentProducts = cached.list
					this.total = cached.total
					this.page = 1
					this.hasMore = cached.list.length < cached.total
					this.loading = false
					return
				}
				this.resetList()
				this.fetchPage(filterId, 1, this.loadSeq, true)
			},
			// 滚动到底：按当前分类加载下一页
			loadMore() {
				const cat = this.categories[this.activeIndex]
				if (!cat || this.loading || !this.hasMore) return
				this.fetchPage(cat.id, this.page + 1, this.loadSeq, false)
			},
			// 拉一页商品：reset=true 覆盖列表，否则追加；seq 不一致说明期间切了分类，丢弃结果
			fetchPage(categoryId, page, seq, reset) {
				this.loading = true
				getProductPage({ page: page, pageSize: PAGE_SIZE, categoryId: categoryId }).then((res) => {
					if (seq !== this.loadSeq) return
					const list = (res && res.list) || []
					this.total = (res && res.total) || 0
					this.page = page
					this.currentProducts = reset ? list : this.currentProducts.concat(list)
					this.hasMore = this.currentProducts.length < this.total
					// 只缓存首屏：切回该分类时先展示第一页，再靠上拉续拉
					if (reset) {
						this.productCache[categoryId] = {
							list: list,
							total: this.total,
							expireAt: Date.now() + 60 * 1000
						}
					}
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).then(() => {
					if (seq === this.loadSeq) this.loading = false
				})
			},
			// 清空分页状态，并换新的请求序号让旧响应失效
			resetList() {
				this.loadSeq++
				this.currentProducts = []
				this.page = 1
				this.total = 0
				this.hasMore = false
				this.loading = false
			},
			// 分类图/商品图共用：后端 image 是相对路径，补 BASE_URL
			imgUrl(item) {
				const img = item.image
				return img ? (img.indexOf('http') === 0 ? img : BASE_URL + img) : ''
			},
			// 定位首页分类面板传来的选中分类：switchTab 不能带 query，靠 Storage 传 id
			applySelectedCategory() {
				const selectedId = uni.getStorageSync('selectedCategoryId')
				if (!selectedId) return
				const idx = this.categories.findIndex((c) => c.id === selectedId)
				if (idx !== -1) {
					this.activeIndex = idx
					// 用完即清，避免下次直接进分类页还停在旧分类
					uni.removeStorageSync('selectedCategoryId')
				}
			},
			// 接导航栏抛上来的高度
			handleNavHeightChange(height) {
				this.navHeight = height
			},
			// 点顶部搜索框：跳商品搜索页
			goSearch() {
				uni.navigateTo({ url: '/pages/search/search' })
			},
			// 点左侧一级分类：切换选中并加载该一级下第一页商品
			handleSelect(index) {
				// 点已选中的分类不重复请求（列表为空时除外，比如上次加载失败）
				if (index === this.activeIndex && this.currentProducts.length) return
				this.activeIndex = index
				this.loadProducts()
			},
			// 点击商品：跳商品详情页（独立 page，不再弹加购弹窗）
			handleProductClick(item) {
				uni.navigateTo({
					url: '/pages/order/detail?id=' + item.id
				})
			}
		}
	}
</script>

<style>
	.page {
		display: flex;
		flex-direction: column;
	}

	/* 分类主体高度走内联 bodyHeight，不用 flex 撑开：scroll-view 需要确定高度才能滚动 */
	.category-body {
		display: flex;
	}

	/* ===== 左侧一级分类 ===== */
	.sidebar {
		width: 176rpx;
		height: 100%;
		background: #f5f5f5;
	}

	.sidebar-item {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 28rpx 0;
		/* 默认透明左边框占位，选中时才上橙色，避免切换时内容左右抖动 */
		border-left: 6rpx solid transparent;
	}

	.sidebar-item.active {
		background: #ffffff;
		border-left-color: #FF6700;
	}
	.sidebar-name {
		margin-top: 10rpx;
		font-size: 24rpx;
		line-height: 2;
		color: #333333;
	}

	.sidebar-item.active .sidebar-name {
		color: #FF6700;
		font-weight: bold;
	}

	/* ===== 右侧商品 ===== */
	.main {
		flex: 1;
		height: 100%;
		background: #ffffff;
	}

	/* 右侧顶部轮播图容器：左右留边，让轮播图不贴边 */
	.swiper-wrap {
		padding: 20rpx 20rpx 0;
	}

	/* 商品网格：每行两个，卡片内部 图片 + 名称 + 价格 + 购物车图标 */
	.goods-grid {
		display: flex;
		flex-wrap: wrap;
		justify-content: space-between;
		padding: 0 16rpx;
		padding-bottom: 20rpx;
		margin-top:30rpx;
	}

	/* 列表底部提示：加载中 / 没有更多；底部留出固定导航栏的高度 */
	.list-tip {
		padding: 20rpx 0 160rpx;
		text-align: center;
		font-size: 24rpx;
		color: #999999;
	}

	.goods-card {
		box-sizing: border-box;
		/* 固定高度 + 纵向排列：让不同名称长度的卡片等高，商品对齐 */
		display: flex;
		flex-direction: column;
		position: relative;
		width: calc(50% - 8rpx);
		height: 440rpx;
		margin-bottom: 20rpx;
		background: #ffffff;
		border-radius: 12rpx;
		overflow: hidden;
	}

	/* 促销标签：商品图左上角角标 */
	.promo-tag {
		position: absolute;
		top: 0;
		left: 0;
		z-index: 2;
		padding: 2rpx 10rpx;
		font-size: 24rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 0 0 8rpx 0;
	}

	.price-wrap {
		display: flex;
		flex-direction: column;
	}

	/* 活动前原价：划线展示 */
	.origin-price {
		font-size: 20rpx;
		line-height: 1.1;
		color: #999999;
		text-decoration: line-through;
	}

	.goods-img {
		width: 100%;
		height: 260rpx;
		/* flex 子项默认 flex-shrink:1，卡片空间不足时图片会被压矮；禁掉保证图片高度一致 */
		flex-shrink: 0;
	}

	.goods-info {
		display: flex;
		flex-direction: column;
		/* 占满图片下方剩余高度，价格行始终贴卡片底部 */
		flex: 1;
		padding: 12rpx 16rpx 16rpx;
	}

	.goods-name {
		font-size: 24rpx;
		color: #333333;
		line-height: 1.8;
		/* 名字最多两行，超出省略 */
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		overflow: hidden;
		height:80rpx;
	}

	/* 价格 + 购物车图标一行：价格靠左，购物车靠右 */
	.goods-bottom {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 4rpx;
	}

	/* 价格用品牌橙，和 tabBar 选中色一致 */
	.goods-price {
		font-size: 30rpx;
		font-weight: bold;
		color: #FF6700;
	}

	/* 购物车图标：复用全局 iconfont，橙色圆底做成加购按钮的样子 */
	.goods-cart {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 56rpx;
		height: 56rpx;
		border-radius: 50%;
		background: #FF6700;
		color: #ffffff;
		font-size: 30rpx;
	}
</style>
