<template>
<view class="page">
		<!-- 页面内容：随页面原生滚动，底部固定栏用 fixed 盖在最上层 -->
		<view class="content">
			<!-- 主图：单图（商品数据只有一张 image，无多图轮播） -->
			<image lazy-load v-if="cover" class="cover" :src="cover" mode="aspectFill"></image>

			<!-- 商品信息卡：价格 / 名称 / 副标题 -->
			<view class="info-card">
				<view class="price-row">
					<text class="price">¥{{ product.price }}</text>
					<text v-if="product.originalPrice" class="origin-price">¥{{ product.originalPrice }}</text>
					<text v-if="product.promotionTag" class="promo-tag">{{ product.promotionTag }}</text>
					<text v-if="product.marketPrice" class="market-price">¥{{ product.marketPrice }}</text>
				</view>
				<text class="name">{{ product.title }}</text>
				<text v-if="product.subTitle" class="sub-title">{{ product.subTitle }}</text>
			</view>

			<!-- 服务保障：和后台 Mall.vue 的 assurance-row 一致，小程序没有 ant 图标，用 uni-ui 的 uni-icons 对应 -->
			<view class="assurance-row">
				<view class="assurance-item">
					<uni-icons type="auth" size="15" color="#FF6700"></uni-icons>
					<text class="assurance-text">正品保证</text>
				</view>
				<view class="assurance-item">
					<uni-icons type="refreshempty" size="15" color="#FF6700"></uni-icons>
					<text class="assurance-text">极速退款</text>
				</view>
				<view class="assurance-item">
					<uni-icons type="calendar" size="15" color="#FF6700"></uni-icons>
					<text class="assurance-text">七天无理由</text>
				</view>
				<view class="assurance-item">
					<uni-icons type="location" size="15" color="#FF6700"></uni-icons>
					<text class="assurance-text">全国联保</text>
				</view>
			</view>

			<!-- 数量选择 -->
			<view class="quantity-row">
				<text class="row-label">数量</text>
				<QuantityStepper :value="quantity" :max="product.stock" @change="setQuantity" />
			</view>


			<!-- 商品评价：放在商品参数之前（先给结论：评分/好评率），默认只展示 3 条 -->
			<view class="review-section" v-if="reviewTotal > 0">
				<view class="review-head">
					<text class="review-title">商品评价</text>
					<text class="review-total">{{ reviewTotal }} 条</text>
				</view>
				<view class="review-summary">
					<view class="review-score">
						<text class="score-num">{{ reviewSummary.avgRating }}</text>
						<text class="score-label">平均分</text>
						<text class="score-good">{{ goodRateText }}</text>
					</view>
					<view class="score-bars">
						<view class="score-bar" v-for="item in scoreBars" :key="item.star">
							<text class="bar-label">{{ item.star }}星</text>
							<view class="bar-track">
								<view class="bar-fill" :style="{ width: item.percent + '%' }"></view>
							</view>
							<text class="bar-count">{{ item.count }}</text>
						</view>
					</view>
				</view>
				<view class="review-list">
					<view class="review-item" v-for="item in visibleReviews" :key="item.id">
						<view class="review-item-head">
							<text class="review-user">{{ item.userName }}</text>
							<text class="review-time">{{ item.createdAt }}</text>
						</view>
						<view class="review-stars">
							<uni-icons v-for="i in 5" :key="i" :type="i <= item.rating ? 'star-filled' : 'star'"
								size="13" :color="i <= item.rating ? '#FF4E2A' : '#e0e0e0'" />
							<text class="review-tag" v-if="item.tag">{{ item.tag }}</text>
							<text class="review-virtual" v-if="item.isVirtual === 1">系统评价</text>
						</view>
						<text class="review-content">{{ item.content }}</text>
						<view class="review-media" v-if="item.media.length">
							<image v-for="(m, idx) in item.media" :key="idx" class="review-media-item"
								:src="m.url" mode="aspectFill" @click="previewMedia(item.media, idx)" />
						</view>
						<!-- 评价视频：一条评价最多一个视频，单独一行播放器 -->
						<video v-if="item.videoUrl" class="review-video-player" :src="item.videoUrl"
							:controls="true" :show-center-play-btn="true" object-fit="contain" />
						<view class="review-reply" v-if="item.reply">
							<text class="reply-label">商家回复：</text>
							<text class="reply-text">{{ item.reply }}</text>
						</view>
					</view>
				</view>
				<view class="review-more" v-if="reviewTotal > 3" @click="toggleReviews">
					{{ reviewExpanded ? '收起评价' : '查看全部评价' }}
				</view>
			</view>

			<!-- 商品参数：specs 有序数组，为空时整块不显示 -->
			<view class="specs-section" v-if="specsList.length">
				<view class="specs-title">商品参数</view>
				<view class="specs-table">
					<view class="spec-row" v-for="(item, index) in specsList" :key="index">
						<text class="spec-name">{{ item.name }}</text>
						<text class="spec-value">{{ item.value }}</text>
					</view>
				</view>
			</view>

			<!-- 图文详情：body 富文本，rich-text 渲染；img 相对路径已在 bodyHtml 里补全 -->
			<view class="body-section">
				<view class="body-title">
					<view class="body-title-line"></view>
					<text class="body-title-text">图文详情</text>
					<view class="body-title-line"></view>
				</view>
				<rich-text v-if="bodyHtml" class="body-detail" :nodes="bodyHtml"></rich-text>
			</view>
		</view>

		<!-- 底部固定栏：收藏 / 购物车 / 加入购物车 / 立即购买 -->
		<view class="footer-bar">
			<view class="footer-icon" @click="toggleFavorite">
				<uni-icons :type="favorited ? 'star-filled' : 'star'" size="22"
					:color="favorited ? '#FF6700' : '#999999'"></uni-icons>
				<text class="footer-icon-text" :class="{ favorited: favorited }">收藏</text>
			</view>
			<view class="footer-icon" @click="goCart">
				<view class="footer-cart-wrap">
					<text class="iconfont icon-gouwuche1 footer-cart"></text>
					<text v-if="cartCount > 0" class="footer-cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</text>
				</view>
				<text class="footer-icon-text">购物车</text>
			</view>
			<view class="footer-btn add-btn" :class="{ loading: adding }" @click="handleAdd">加入购物车</view>
			<view class="footer-btn buy-btn" @click="handleBuy">立即购买</view>
		</view>

		<!-- 登录弹窗：未登录点加购/购买时弹出（打开即静默登录），登录成功自动续做刚才的动作 -->
		<LoginPopup :show="showLoginPopup" @close="handleLoginClose" @success="handleLoginSuccess" />
	</view>
</template>

<script>
	import { BASE_URL } from '../../utils/server-api.js'
	import { getProductDetail } from '../../api/product.js'
	import { addToCart } from '../../api/cart.js'
	import { checkFavorite, addFavorite, removeFavorite } from '../../api/favorite.js'
	import { getProductReviews, getReviewSummary } from './api/review.js'
	import { parseReviewMedia, reviewMediaUrl } from './api/review-media.js'
	import { ensureLogin } from '../../utils/auth.js'
	import { useUserStore } from '../../store/user.js'
	import { useCartStore } from '../../store/cart.js'

	export default {
		name: 'GoodsDetail',
		data() {
			return {
				product: {}, // 商品详情，onLoad 按 id 拉取
				quantity: 1, // 购买数量，最少 1 件
				selectedInsurance: [], // 已勾选的增值保障项 code（可选项由后端返回）
				adding: false, // 正在请求加购接口，按钮置灰防重复点
				favorited: false, // 是否已收藏（后端账号级数据）
				favoriteLoading: false, // 收藏请求中，防重复点击
				showLoginPopup: false, // 登录弹窗显隐
				reviewSummary: { // 评价统计：总数/平均分/好评率/星级分布
					totalCount: 0,
					avgRating: 0,
					goodRate: 0,
					fiveStar: 0,
					fourStar: 0,
					threeStar: 0,
					twoStar: 0,
					oneStar: 0
				},
				reviewList: [], // 评价列表（已加工：媒体补全域名、日期只留到天）
				reviewExpanded: false, // 是否展开全部评价（默认只显示 3 条）
				store: useUserStore() // 登录态，加购/购买前先判断
			}
		},
		onLoad(options) {
			const id = options.id
			if (!id) {
				return
			}
			this.loadDetail(id)
			// 已登录才查收藏态；未登录点收藏时会静默登录
			this.loadFavorite(id)
			// 商品评价（免登录只读）
			this.loadReviews(id)
		},
		// 胶囊菜单「转发给朋友」：定义了它，胶囊里的「转发」自动可用
		// 详情未加载完时退回默认文案，避免分享出空标题
		onShareAppMessage() {
			return {
				title: this.product.name || '杰米多多小店｜全新正品3C数码',
				imageUrl: this.cover,
				// 带上商品 id，好友点开直接进同一个商品
				path: '/pages/goods/detail?id=' + this.product.id
			}
		},
		// 胶囊菜单「分享到朋友圈」
		onShareTimeline() {
			return {
				title: this.product.name || '杰米多多小店｜全新正品3C数码',
				imageUrl: this.cover,
				query: 'id=' + this.product.id
			}
		},
		// 胶囊菜单「收藏」
		onAddToFavorites() {
			return {
				title: this.product.name || '杰米多多小店｜全新正品3C数码',
				imageUrl: this.cover,
				query: 'id=' + this.product.id
			}
		},
		computed: {
			// 购物车角标件数：读全局 cart store，加购后自动同步到详情页底部购物车 icon
			cartCount() {
				return useCartStore().count
			},
			// 主图：后端 image 是相对路径，补上网关域名才能取到图
			cover() {
				const img = this.product.image
				return img ? (img.indexOf('http') === 0 ? img : BASE_URL + img) : ''
			},
			// 商品参数：后端 specs 是有序数组 [{name,value}]，空值过滤掉
			specsList() {
				return Array.isArray(this.product.specs)
					? this.product.specs.filter((item) => item && item.name && item.value != null && item.value !== '')
					: []
			},
			// 图文详情富文本：body 里 img 的 src 是相对路径 /image/body/xxx，rich-text 不会自动补域名，
			// 统一补成完整 URL；body 已清成只剩 <img>，不需要剥参数/段落
			bodyHtml() {
				let body = this.product.body
				if (!body) return ''
				return body
					.replace(/style=""/g, 'style="width:100%;display:block;"')
					.replace(/\/image\//g, BASE_URL + '/image/')
			},
			// 可选增值保障项：后端按商品属性算出（配件类、低价商品返回空数组）
			insuranceOptions() {
				return Array.isArray(this.product.insuranceOptions) ? this.product.insuranceOptions : []
			},
			// 选中的增值保障明细（数组，随加购/下单一起交给后端）
			insuranceDetail() {
				return this.insuranceOptions
					.filter((opt) => this.isInsuranceSelected(opt.code))
					.map((opt) => ({ code: opt.code, name: opt.name, fee: Number(opt.fee) }))
			},
			// 单件保障费合计
			insuranceFee() {
				const fee = this.insuranceDetail.reduce((sum, opt) => sum + Number(opt.fee), 0)
				return fee.toFixed(2)
			},
			// 评价总条数
			reviewTotal() {
				return this.reviewSummary.totalCount || 0
			},
			// 好评率文案：后端 goodRate 是 0~1 的比例
			goodRateText() {
				const rate = Math.round((this.reviewSummary.goodRate || 0) * 100)
				return '好评率 ' + rate + '%'
			},
			// 星级分布条：每条星级的占比
			scoreBars() {
				const s = this.reviewSummary
				const total = this.reviewTotal
				const list = [
					{ star: 5, count: s.fiveStar || 0 },
					{ star: 4, count: s.fourStar || 0 },
					{ star: 3, count: s.threeStar || 0 },
					{ star: 2, count: s.twoStar || 0 },
					{ star: 1, count: s.oneStar || 0 }
				]
				return list.map((it) => ({
					star: it.star,
					count: it.count,
					percent: total ? Math.round((it.count / total) * 100) : 0
				}))
			},
			// 默认只展示 3 条，展开后展示全部（接口一次取 20 条）
			visibleReviews() {
				return this.reviewExpanded ? this.reviewList : this.reviewList.slice(0, 3)
			}
		},
		methods: {
			// 该保障项是否已勾选
			isInsuranceSelected(code) {
				return this.selectedInsurance.indexOf(code) > -1
			},
			// 点卡片切换勾选状态
			toggleInsurance(code) {
				const index = this.selectedInsurance.indexOf(code)
				if (index > -1) {
					this.selectedInsurance.splice(index, 1)
				} else {
					this.selectedInsurance.push(code)
				}
			},
			// 拉商品详情
			loadDetail(id) {
				getProductDetail(id).then((res) => {
					this.product = res || {}
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				})
			},
			// 拉评价：列表 + 统计分开两个接口，失败都不影响详情页其他内容
			loadReviews(id) {
				getProductReviews(id, 1, 20).then((res) => {
					this.reviewList = (res && res.list ? res.list : []).map((item) => {
						const media = parseReviewMedia(item.images)
						// 一条评价最多一个视频：图片进九宫格，视频单独用 <video> 播
						const video = media.find((m) => m.type === 'video')
						return {
							id: item.id,
							rating: item.rating,
							tag: item.tag,
							content: item.content,
							userName: item.userName,
							isVirtual: item.isVirtual,
							reply: item.reply,
							// 时间后端给的是 yyyy-MM-dd HH:mm:ss，详情页只展示到天
							createdAt: item.createdAt ? item.createdAt.slice(0, 10) : '',
							// 图片补上网关域名（小程序把相对路径当包内路径）
							media: media
								.filter((m) => m.type !== 'video')
								.map((m) => ({ url: reviewMediaUrl(m.url, BASE_URL), type: m.type })),
							videoUrl: video ? reviewMediaUrl(video.url, BASE_URL) : ''
						}
					})
				}).catch(() => {
					this.reviewList = []
				})
				getReviewSummary(id).then((res) => {
					if (res) {
						this.reviewSummary = res
					}
				}).catch(() => {})
			},
			// 展开 / 收起全部评价
			toggleReviews() {
				this.reviewExpanded = !this.reviewExpanded
			},
			// 点评价晒图放大预览
			previewMedia(media, index) {
				uni.previewImage({
					urls: media.map((m) => m.url),
					current: index
				})
			},
			// 数量变化（+/- 或输入框提交）：直接更新本地数量，加购/下单时再提交
			setQuantity(n) {
				this.quantity = n
			},
			// 查收藏态：未登录不查，点收藏时再静默登录
			loadFavorite(id) {
				if (!this.store.isLogin) {
					return
				}
				checkFavorite(id).then((flag) => {
					this.favorited = !!flag
				}).catch(() => {})
			},
			// 点收藏：未登录先静默登录，再按当前状态调收藏/取消收藏
			toggleFavorite() {
				if (this.favoriteLoading) {
					return
				}
				const id = this.product.id
				if (!id) {
					return
				}
				this.favoriteLoading = true
				ensureLogin()
					.then(() => this.favorited ? removeFavorite(id) : addFavorite(id))
					.then(() => {
						this.favorited = !this.favorited
						uni.showToast({
							title: this.favorited ? '已收藏' : '已取消收藏',
							icon: 'none'
						})
					})
					.catch(() => {
						// 错误提示已在 request 封装里统一处理
					})
					.finally(() => {
						this.favoriteLoading = false
					})
			},
			// 点购物车：切到购物车 tab（switchTab 只能切 tab 页，会关掉当前详情页）
			goCart() {
				uni.switchTab({
					url: '/pages/cart/cart'
				})
			},
			// 点加入购物车：未登录先弹登录弹窗；已登录调后端落库，成功 toast + 角标累加
			handleAdd() {
				if (this.adding) {
					return
				}
				if (!this.store.isLogin) {
					this.showLoginPopup = true
					return
				}
				if (!this.product.id) {
					return
				}
				this.adding = true
				addToCart(this.product.id, this.quantity, this.insuranceDetail, Number(this.insuranceFee)).then(() => {
					this.adding = false
					// 同步底部购物车角标件数（详情页没有购物车 tab 落点，不再做飞入动画）
					const cartStore = useCartStore()
					console.log('[加购] 加购前 count:', cartStore.count, '本次加:', this.quantity, '类型:', typeof this.quantity)
					cartStore.addQuantity(this.quantity)
					console.log('[加购] 加购后 count:', cartStore.count)
					uni.showToast({
						title: '已加入购物车',
						icon: 'success'
					})
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
					this.adding = false
				})
			},
			// 点立即购买：未登录先弹登录；已登录组装单个商品跳下单确认页
			handleBuy() {
				if (!this.store.isLogin) {
					this.showLoginPopup = true
					return
				}
				if (!this.product.id) {
					return
				}
				// 组装单个商品的待结算项，字段对齐购物车 CartVO（下单确认页统一读 store.checkoutItems）
				const unitPrice = Number(this.product.price) || 0
				const fee = Number(this.insuranceFee) || 0
				useCartStore().setCheckoutItems([{
					productId: this.product.id,
					name: this.product.name,
					image: this.product.image,
					price: unitPrice,
					quantity: this.quantity,
					insuranceDetail: this.insuranceDetail,
					insuranceFee: fee,
					subTotal: (unitPrice + fee) * this.quantity
				}])
				uni.navigateTo({
					url: '/pages/order/confirm'
				})
			},
			// 登录成功：收掉登录弹窗，把刚才被打断的加购接着做完
			handleLoginSuccess() {
				if (!this.showLoginPopup) {
					return
				}
				this.showLoginPopup = false
				this.handleAdd()
			},
			// 关闭登录弹窗（用户手动关或点遮罩）
			handleLoginClose() {
				this.showLoginPopup = false
			}
		}
	}
</script>

<style scoped>
	.page {
		background: #F8F8F8;
	}

	/* 内容区：自然高度随页面滚动，底部 padding 留出 fixed 栏的高度 */
	.content {
		padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
	}

	/* 主图：全宽正方形，aspectFill 裁切居中 */
	.cover {
		width: 100%;
		height: 750rpx;
	}

	/* ===== 商品信息卡 ===== */
	.info-card {
		display: flex;
		flex-direction: column;
		padding: 20rpx 30rpx;
		background: #ffffff;
	}

	.price-row {
		display: flex;
		align-items: baseline;
	}

	.price {
		font-size: 44rpx;
		font-weight: bold;
		color: #FF6700;
	}

	.market-price {
		margin-left: 16rpx;
		font-size: 26rpx;
		color: #999999;
		text-decoration: line-through;
	}

	/* 活动前原价：划线展示 */
	.origin-price {
		margin-left: 16rpx;
		font-size: 26rpx;
		color: #999999;
		text-decoration: line-through;
	}

	/* 促销标签：品牌橙小角标 */
	.promo-tag {
		margin-left: 16rpx;
		padding: 0 12rpx;
		height: 36rpx;
		line-height: 36rpx;
		font-size: 24rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 6rpx;
	}

	.name {
		margin-top: 12rpx;
		font-size: 32rpx;
		font-weight: bold;
		color: #333333;
		line-height: 1.4;
	}

	.sub-title {
		margin-top: 8rpx;
		font-size: 26rpx;
		color: #999999;
	}

	/* ===== 服务保障 ===== */
	.assurance-row {
		display: flex;
		margin-top: 16rpx;
		padding: 20rpx 30rpx;
		background: #ffffff;
	}

	.assurance-item {
		display: flex;
		align-items: center;
		width: 50%;
		padding: 8rpx 0;
	}

	.assurance-text {
		margin-left: 8rpx;
		font-size: 24rpx;
		color: #666666;
	}

	/* ===== 数量 / 增值保障 通用行 ===== */
	.quantity-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 16rpx;
		padding: 20rpx 30rpx;
		background: #ffffff;
	}

	.row-label {
		font-size: 26rpx;
		color: #333333;
	}

	.section-title {
		font-size: 26rpx;
		font-weight: 600;
		color: #333333;
	}

	.insurance-section {
		display: flex;
		flex-direction: column;
		margin-top: 16rpx;
		padding: 20rpx 30rpx;
		background: #ffffff;
	}

	.insurance-cards {
		display: flex;
		margin-top: 16rpx;
	}

	.insurance-card {
		display: flex;
		flex-direction: column;
		align-items: center;
		flex: 1;
		margin-right: 16rpx;
		padding: 14rpx 8rpx;
		background: #ffffff;
		border: 3rpx solid #e8e8e8;
		border-radius: 24rpx;
	}

	.insurance-card:last-child {
		margin-right: 0;
	}

	.insurance-card.active {
		border-color: #FF6700;
		background: #FFF7F0;
	}

	.insurance-name {
		font-size: 24rpx;
		color: #555555;
	}

	.insurance-price {
		margin-top: 8rpx;
		font-size: 26rpx;
		font-weight: 700;
		color: #FF6700;
	}

	/* ===== 商品评价 ===== */
	.review-section {
		margin: 20rpx 24rpx 0;
		padding: 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}
	.review-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
	}
	.review-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}
	.review-total {
		font-size: 24rpx;
		color: #999999;
	}
	.review-summary {
		display: flex;
		align-items: center;
		padding: 20rpx 0;
		border-bottom: 1rpx solid #f0f0f0;
	}
	.review-score {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 160rpx;
		flex-shrink: 0;
	}
	.score-num {
		font-size: 52rpx;
		font-weight: 700;
		color: #FF4E2A;
		line-height: 1.1;
	}
	.score-label {
		font-size: 22rpx;
		color: #999999;
	}
	.score-good {
		margin-top: 8rpx;
		font-size: 22rpx;
		color: #666666;
	}
	.score-bars {
		flex: 1;
		padding-left: 20rpx;
	}
	.score-bar {
		display: flex;
		align-items: center;
		margin-bottom: 6rpx;
	}
	.bar-label {
		width: 60rpx;
		font-size: 20rpx;
		color: #999999;
		flex-shrink: 0;
	}
	.bar-track {
		flex: 1;
		height: 10rpx;
		background: #f0f0f0;
		border-radius: 5rpx;
		overflow: hidden;
	}
	.bar-fill {
		height: 100%;
		background: #FF4E2A;
	}
	.bar-count {
		width: 60rpx;
		text-align: right;
		font-size: 20rpx;
		color: #999999;
		flex-shrink: 0;
	}
	.review-item {
		padding: 20rpx 0;
		border-bottom: 1rpx solid #f0f0f0;
	}
	.review-item:last-child {
		border-bottom: none;
	}
	.review-item-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.review-user {
		font-size: 26rpx;
		font-weight: 600;
		color: #333333;
	}
	.review-time {
		font-size: 22rpx;
		color: #bbbbbb;
	}
	.review-stars {
		display: flex;
		align-items: center;
		margin-top: 6rpx;
	}
	.review-tag {
		margin-left: 12rpx;
		font-size: 22rpx;
		color: #FF6700;
	}
	.review-virtual {
		margin-left: 12rpx;
		font-size: 20rpx;
		color: #999999;
		background: #f5f5f5;
		border-radius: 6rpx;
		padding: 2rpx 10rpx;
	}
	.review-content {
		display: block;
		margin-top: 8rpx;
		font-size: 26rpx;
		color: #555555;
		line-height: 1.6;
	}
	.review-media {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		margin-top: 12rpx;
	}
	.review-media-item {
		width: 140rpx;
		height: 140rpx;
		margin-right: 12rpx;
		border-radius: 8rpx;
		background: #f5f5f5;
	}
	/* 评价视频播放器：宽度撑满，高度按 16:9 */
	.review-video-player {
		width: 100%;
		height: 420rpx;
		margin-top: 12rpx;
		border-radius: 8rpx;
		background: #f5f5f5;
	}
	.review-reply {
		margin-top: 12rpx;
		padding: 12rpx 16rpx;
		background: #fafafa;
		border-radius: 8rpx;
	}
	.reply-label {
		font-size: 24rpx;
		color: #333333;
		font-weight: 600;
	}
	.reply-text {
		font-size: 24rpx;
		color: #666666;
		line-height: 1.6;
	}
	.review-more {
		margin-top: 16rpx;
		text-align: center;
		font-size: 26rpx;
		color: #FF6700;
	}

	/* ===== 图文详情 ===== */
	/* ===== 商品参数 ===== */
	.specs-section {
		margin: 20rpx 24rpx 0;
		padding: 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}
	.specs-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
		margin-bottom: 8rpx;
	}
	.spec-row {
		display: flex;
		align-items: flex-start;
		padding: 14rpx 0;
		border-bottom: 1rpx solid #f0f0f0;
	}
	.spec-row:last-child {
		border-bottom: none;
	}
	.spec-name {
		width: 180rpx;
		flex-shrink: 0;
		font-size: 26rpx;
		color: #999999;
		line-height: 1.6;
	}
	.spec-value {
		flex: 1;
		font-size: 26rpx;
		color: #333333;
		line-height: 1.6;
		word-break: break-all;
	}

	.body-section {
		display: flex;
		flex-direction: column;
		margin-top: 16rpx;
		padding: 20rpx 0rpx;
		background: #ffffff;
	}

	.body-detail {
		width: 100%;
		margin-top: 16rpx;
		padding: 0 30rpx;
		box-sizing: border-box;
	}

	/* 图文详情标题：居中 + 左右装饰线，和增值保障的普通小标题区分开 */
	.body-title {
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 8rpx;
	}

	.body-title-line {
		flex: 1;
		height: 1rpx;
		background: #e0e0e0;
	}

	.body-title-text {
		margin: 0 24rpx;
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}

	/* ===== 底部固定栏 ===== */
	.footer-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 999;
		display: flex;
		align-items: center;
		height: 100rpx;
		padding-bottom: env(safe-area-inset-bottom);
		background: #ffffff;
		box-shadow: 0 -2rpx 8rpx rgba(0, 0, 0, 0.05);
	}

	/* 收藏 / 购物车 图标项：竖排 图标 + 文字 */
	.footer-icon {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		width: 100rpx;
	}

	.footer-cart-wrap {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.footer-cart-badge {
		position: absolute;
		top: -8rpx;
		right: -16rpx;
		min-width: 28rpx;
		height: 28rpx;
		padding: 0 6rpx;
		border-radius: 14rpx;
		background: #ff4d4f;
		color: #ffffff;
		font-size: 18rpx;
		line-height: 28rpx;
		text-align: center;
		box-sizing: border-box;
	}
	.footer-cart {
		font-size: 40rpx;
		color: #333333;
	}

	.footer-icon-text {
		margin-top: 4rpx;
		font-size: 20rpx;
		color: #999999;
		line-height: 1;
	}

	.footer-icon-text.favorited {
		color: #FF6700;
	}

	/* 加入购物车 / 立即购买：占满剩余宽度 */
	.footer-btn {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		height: 76rpx;
		font-size: 28rpx;
		font-weight: 600;
	}

	.add-btn {
		margin: 0 12rpx 0 8rpx;
		border-radius: 40rpx;
		background: #FFF3E8;
		color: #FF6700;
	}

	/* 加购请求中置灰 */
	.add-btn.loading {
		opacity: 0.6;
	}

	.buy-btn {
		margin-right: 20rpx;
		border-radius: 40rpx;
		background: linear-gradient(135deg, #FF4E2A, #FF7A00);
		color: #ffffff;
	}
</style>
