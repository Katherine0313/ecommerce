<template>
	<!-- 猜你喜欢：随机上架商品，列表来自后端 GET /api/products/random（首页走后端聚合接口 /api/home） -->
	<view class="guess">
		<view class="guess-header">
			<view class="header-line"></view>
			<view class="header-dot"></view>
			<text class="guess-title">猜你喜欢</text>
			<view class="header-dot"></view>
			<view class="header-line"></view>
		</view>

		<view class="guess-grid">
			<view class="goods-card" v-for="item in products" :key="item.id" @click="handleSelect(item.id)">
				<image lazy-load class="goods-img" :src="imgUrl(item)" mode="aspectFill"></image>
				<view class="goods-info">
					<text v-if="item.promotionTag" class="promo-tag">{{ item.promotionTag }}</text>
					<text class="goods-name">{{ item.name }}</text>
					<view class="price-row">
						<text class="goods-price">¥{{ item.price }}</text>
						<text v-if="item.originalPrice" class="origin-price">¥{{ item.originalPrice }}</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { getRandomProducts } from '../api/product.js'
	import { BASE_URL } from '../utils/server-api.js'

	export default {
		name: 'Guess',
		props: {
			// 首页聚合接口传入的推荐列表：undefined=没传（自请求），null=加载中，数组=已加载
			list: { type: Array, default: undefined }
		},
		data() {
			return {
				// 自请求时的推荐列表（购物车等页面单独使用组件时）
				internalProducts: [],
				selfLoaded: false
			}
		},
		computed: {
			// 外部传入优先，否则用自请求数据
			products() {
				return this.list !== undefined ? (this.list || []) : this.internalProducts
			}
		},
		mounted() {
			// 首页会传 list，不再重复请求
			if (this.list === undefined) {
				this.fetchRecommend()
			}
		},
		methods: {
			// 拉猜你喜欢：随机上架商品，与 GET /api/products/random 同一口径
			// 返回 Promise，供页面下拉刷新时等请求结束
			fetchRecommend() {
				return getRandomProducts(10).then((list) => {
					this.internalProducts = list || []
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).finally(() => {
					this.selfLoaded = true
				})
			},
			// 商品主图：后端 image 是 /image/upload/N.webp 相对路径，小程序把开头的 / 当包内路径，需补 BASE_URL；没有则回退后端同命名商品图
			imgUrl(item) {
				const img = item.image
				if (img) {
					return img.indexOf('http') === 0 ? img : BASE_URL + img
				}
				return item.id ? BASE_URL + '/image/upload/' + item.id + '.webp' : ''
			},
			// 点击商品：只把 id 抛给页面，拉详情和弹预览由页面统一处理（和 HotPanel 一致）
			handleSelect(id) {
				this.$emit('select', id)
			}
		}
	}
</script>

<style scoped>
	.guess {
		width: 100%;
		box-sizing: border-box;
		margin-top: 24rpx;
		padding: 0 20rpx;
	}

	.guess-header {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12rpx;
		padding: 20rpx 0;
	}

	/* 左右渐变装饰线：从透明过渡到品牌橙，指向标题 */
	.header-line {
		width: 120rpx;
		height: 2rpx;
		background: linear-gradient(to right, transparent, #FF6700);
	}

	/* 右边那条反向渐变，让两端都从透明开始 */
	.header-line:last-child {
		background: linear-gradient(to left, transparent, #FF6700);
	}

	/* 标题两侧的小圆点 */
	.header-dot {
		width: 10rpx;
		height: 10rpx;
		border-radius: 50%;
		background: #FF6700;
	}

	.guess-title {
		font-size: 34rpx;
		font-weight: bold;
		color: #333333;
	}

	.guess-grid {
		display: flex;
		flex-wrap: wrap;
		justify-content: space-between;
	}

	.goods-card {
		box-sizing: border-box;
		width: calc(50% - 10rpx);
		margin-bottom: 20rpx;
		background: #ffffff;
		border-radius: 16rpx;
		overflow: hidden;
	}

	.goods-img {
		width: 100%;
		height: 300rpx;
		/* 顶部圆角由卡片 overflow:hidden 裁出，底部在卡片内部要单独设圆角 */
		border-radius: 0 0 16rpx 16rpx;
	}

	.goods-info {
		display: flex;
		flex-direction: column;
		padding: 16rpx 20rpx 20rpx;
	}

	.goods-name {
		font-size: 28rpx;
		color: #333333;
		line-height: 1.4;
		/* 名字最多两行，超出省略 */
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		overflow: hidden;
	}

	/* 促销标签：活动商品在名称上方展示 */
	.promo-tag {
		align-self: flex-start;
		margin-bottom: 6rpx;
		padding: 0 12rpx;
		height: 34rpx;
		line-height: 34rpx;
		font-size: 24rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 6rpx;
	}

	.price-row {
		display: flex;
		align-items: baseline;
		margin-top: 12rpx;
	}

	/* 价格用品牌橙，和 tabBar 选中色、弹窗价格一致 */
	.goods-price {
		font-size: 32rpx;
		font-weight: bold;
		color: #FF6700;
	}

	/* 活动前原价：划线展示 */
	.origin-price {
		margin-left: 10rpx;
		font-size: 22rpx;
		color: #999999;
		text-decoration: line-through;
	}
</style>
