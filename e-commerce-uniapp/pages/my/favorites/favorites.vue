<template>
<view class="page">
		<CustomNavbar title="我的收藏" :show-back="true" />

		<!-- 收藏列表：账号级数据，下架商品也保留并打「已下架」标记 -->
		<view v-if="list.length" class="fav-list">
			<view class="fav-card" v-for="item in list" :key="item.productId" @click="goDetail(item)">
				<image lazy-load class="fav-img" :src="goodsImgUrl(item.image, item.productId)" mode="aspectFill"></image>
				<view class="fav-info">
					<text class="fav-name">{{ item.name }}</text>
					<view class="fav-price-row">
						<text class="fav-price">¥{{ fmtMoney(item.price) }}</text>
						<text v-if="item.marketPrice" class="fav-market">¥{{ fmtMoney(item.marketPrice) }}</text>
						<text v-if="item.status !== 1" class="off-tag">已下架</text>
					</view>
				</view>
				<!-- 取消收藏：星标点击后直接从列表移除，不整页重拉 -->
				<view class="fav-remove" @click.stop="handleRemove(item)">
					<uni-icons type="star-filled" size="22" color="#FF6700"></uni-icons>
				</view>
			</view>
		</view>

		<!-- 空态 -->
		<view v-else-if="loaded" class="empty">
			<text class="empty-text">还没有收藏的商品</text>
			<view class="go-btn" @click="goHome">去逛逛</view>
		</view>
	</view>
</template>

<script>
	import { ensureLogin } from '../../../utils/auth.js'
	import { listFavorites, removeFavorite } from '../../../api/favorite.js'
	import { fmtMoney, goodsImgUrl } from '../../../utils/format.js'

	export default {
		data() {
			return {
				list: [], // 收藏列表，字段对齐后端 FavoriteVO
				loaded: false // 首次加载完成后才显示空态，避免闪一下「还没有收藏」
			}
		},
		onShow() {
			// 免登录：直接打开本页时先确保已登录，再拉收藏列表
			ensureLogin()
				.then(() => listFavorites())
				.then((list) => {
					this.list = list || []
				})
				.catch(() => {
					// 错误提示已在 request 封装里统一处理
				})
				.finally(() => {
					this.loaded = true
				})
		},
		methods: {
			fmtMoney,
			goodsImgUrl,
			// 点卡片进商品详情（下架商品也可查看）
			goDetail(item) {
				uni.navigateTo({
					url: '/pages/order/detail?id=' + item.productId
				})
			},
			// 取消收藏：成功后从本地列表移除
			handleRemove(item) {
				removeFavorite(item.productId).then(() => {
					this.list = this.list.filter(i => i.productId !== item.productId)
					uni.showToast({ title: '已取消收藏', icon: 'none' })
				}).catch(() => {})
			},
			// 空态「去逛逛」：回首页
			goHome() {
				uni.switchTab({
					url: '/pages/index/index'
				})
			}
		}
	}
</script>

<style scoped>
	.page {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
		box-sizing: border-box;
		padding-bottom: env(safe-area-inset-bottom);
		background: #F8F8F8;
	}

	.fav-list {
		padding: 20rpx;
	}

	/* 收藏卡片：左图右信息，右上角星标取消收藏 */
	.fav-card {
		position: relative;
		display: flex;
		align-items: center;
		margin-bottom: 20rpx;
		padding: 20rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	.fav-img {
		width: 200rpx;
		height: 200rpx;
		flex-shrink: 0;
		border-radius: 12rpx;
		background: #f0f0f0;
	}

	.fav-info {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		margin-left: 20rpx;
		padding-right: 48rpx;
	}

	.fav-name {
		font-size: 28rpx;
		color: #333333;
		line-height: 1.4;
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		overflow: hidden;
	}

	.fav-price-row {
		display: flex;
		align-items: center;
		margin-top: 16rpx;
	}

	.fav-price {
		font-size: 32rpx;
		font-weight: bold;
		color: #FF6700;
	}

	.fav-market {
		margin-left: 12rpx;
		font-size: 24rpx;
		color: #999999;
		text-decoration: line-through;
	}

	/* 下架标记：红色描边小标签 */
	.off-tag {
		margin-left: 12rpx;
		padding: 2rpx 10rpx;
		font-size: 20rpx;
		color: #ff4d4f;
		border: 1rpx solid #ffb3b3;
		border-radius: 6rpx;
	}

	.fav-remove {
		position: absolute;
		top: 16rpx;
		right: 16rpx;
		padding: 8rpx;
	}

	/* 空态 */
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-top: 200rpx;
	}

	.empty-text {
		font-size: 28rpx;
		color: #999999;
	}

	.go-btn {
		margin-top: 32rpx;
		height: 72rpx;
		line-height: 72rpx;
		padding: 0 56rpx;
		font-size: 28rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 36rpx;
	}
</style>
