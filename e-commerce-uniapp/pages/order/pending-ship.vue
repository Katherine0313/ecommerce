<template>
<view class="page">
		<CustomNavbar title="订单详情" :show-back="true" />

		<view v-if="order">
			<!-- 顶部状态 -->
			<view class="top-status">{{ order.orderStatusDesc }}</view>

			<!-- 物流信息卡：有物流单号（已发货/已完成）时显示，点击进物流详情 -->
			<view class="logistics-card" v-if="order.trackingNumber" @click="goLogistics">
				<view class="logi-icon">📮</view>
				<view class="logi-info">
					<text class="logi-title">{{ order.courier }} {{ order.trackingNumber }}</text>
					<text class="logi-status" v-if="order.logisticsStatus">{{ order.logisticsStatus }} · {{ order.latestTrace }}</text>
				</view>
				<view class="logi-arrow">></view>
			</view>

			<!-- 收货地址 -->
			<view class="address-card">
				<view class="address-row">
					<view class="location-icon">📍</view>
					<view class="address-info">
						<view class="name-phone">
							<text class="name">{{ order.consignee }}</text>
							<text class="phone">{{ order.phone }}</text>
						</view>
						<view class="address-text">{{ fullAddress }}</view>
					</view>
				</view>
			</view>

			<!-- 商品卡片 -->
			<view class="goods-card">
				<view class="goods-row" v-for="item in order.items" :key="item.productId">
					<image lazy-load class="goods-img" :src="goodsImgUrl(item.image, item.productId)" mode="aspectFill"></image>
					<view class="goods-info">
						<view class="goods-name">{{ item.productName }}</view>
						<view class="price-count">
							<text class="price">¥{{ fmtMoney(item.salesPrice) }}</text>
							<text class="count">x{{ item.quantity }}</text>
						</view>
					</view>
				</view>
				<view v-if="Number(order.discountAmount) > 0" class="price-summary">
					<text>优惠合计</text>
					<text class="discount">-¥{{ fmtMoney(order.discountAmount) }}</text>
				</view>
				<view v-if="Number(order.couponAmount) > 0" class="price-summary">
					<text>其中优惠券</text>
					<text class="discount">-¥{{ fmtMoney(order.couponAmount) }}</text>
				</view>
				<view class="price-summary">
					<text>共{{ totalCount }}件 实付款</text>
					<text class="actual">¥{{ fmtMoney(order.payAmount) }}</text>
				</view>
			</view>

			<!-- 订单信息 -->
			<view class="info-card">
				<view class="info-row">
					<text class="label">订单编号</text>
					<view class="value-wrap">
						<text class="value">{{ order.orderNo }}</text>
						<text class="copy-icon" @click="copyOrderNo">复制</text>
					</view>
				</view>
				<view class="info-row">
					<text class="label">下单时间</text>
					<text class="value">{{ formatOrderTime(order.createdAt) }}</text>
				</view>
				<view class="info-row" v-if="order.payAt">
					<text class="label">付款时间</text>
					<text class="value">{{ formatOrderTime(order.payAt) }}</text>
				</view>
				<view class="info-row">
					<text class="label">运费</text>
					<text class="value">¥ 0.00</text>
				</view>
				<view class="info-row" v-if="order.payType">
					<text class="label">支付方式</text>
					<text class="value">{{ order.payTypeDesc }}</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { getOrder } from '../../api/order.js'
	import { goodsImgUrl, fmtMoney, formatOrderTime } from '../../utils/format.js'

	export default {
		data() {
			return {
				orderId: null,
				order: null
			}
		},
		computed: {
			// 完整地址 = 省市区 + 详细地址
			fullAddress() {
				return (this.order.region) + (this.order.detailAddress )
			},
			// 商品总件数
			totalCount() {
				return (this.order.items || []).reduce((s, i) => s + (Number(i.quantity) || 0), 0)
			}
		},
		onLoad(options) {
			this.orderId = options.id
		},
		onShow() {
			this.loadDetail()
		},
		methods: {
			goodsImgUrl,
			fmtMoney,
			formatOrderTime,
			// 拉订单详情
			loadDetail() {
				getOrder(this.orderId).then((order) => {
					this.order = order
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				})
			},
			// 复制订单号
			copyOrderNo() {
				uni.setClipboardData({
					data: this.order.orderNo,
					success: () => uni.showToast({ title: '订单号已复制', icon: 'none' })
				})
			},
			// 跳物流详情页
			goLogistics() {
				uni.navigateTo({
					url: '/pages/order/logistics?id=' + this.order.id
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
		background: #f5f5f5;
	}

	/* ═══ 顶部状态 ═══ */
	.top-status {
		text-align: center;
		font-size: 28rpx;
		color: #666666;
		padding: 30rpx 0;
	}

	/* ═══ 物流信息卡 ═══ */
	.logistics-card {
		display: flex;
		align-items: center;
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.logi-icon {
		font-size: 40rpx;
		margin-right: 16rpx;
	}

	.logi-info {
		flex: 1;
		min-width: 0;
	}

	.logi-title {
		display: block;
		font-size: 28rpx;
		color: #333333;
	}

	.logi-status {
		display: block;
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #999999;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.logi-arrow {
		flex-shrink: 0;
		font-size: 28rpx;
		color: #cccccc;
		margin-left: 8rpx;
	}

	/* ═══ 地址卡片 ═══ */
	.address-card {
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 30rpx;
	}

	.address-row {
		display: flex;
		gap: 20rpx;
	}

	.location-icon {
		font-size: 36rpx;
		color: #f44;
		margin-top: 4rpx;
	}

	.name-phone {
		display: flex;
		gap: 20rpx;
		font-size: 32rpx;
	}

	.name {
		font-weight: 500;
	}

	.phone {
		color: #333333;
	}

	.address-text {
		font-size: 28rpx;
		color: #666666;
		margin-top: 10rpx;
	}

	/* ═══ 商品卡片 ═══ */
	.goods-card {
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 30rpx;
	}

	.goods-row {
		display: flex;
		gap: 20rpx;
	}

	.goods-row+.goods-row {
		margin-top: 20rpx;
	}

	.goods-img {
		width: 160rpx;
		height: 160rpx;
		border-radius: 8rpx;
		flex-shrink: 0;
	}

	.goods-info {
		flex: 1;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
	}

	.goods-name {
		font-size: 30rpx;
		color: #333333;
	}

	.price-count {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.price {
		font-size: 32rpx;
		color: #f44;
	}

	.count {
		font-size: 28rpx;
		color: #666666;
	}

	.price-summary {
		display: flex;
		justify-content: space-between;
		font-size: 26rpx;
		color: #666666;
		margin-top: 20rpx;
		padding-top: 20rpx;
		border-top: 1rpx solid #f0f0f0;
	}

	.actual {
		color: #333333;
	}

	/* 活动优惠：品牌橙 */
	.discount {
		color: #FF6700;
	}

	/* ═══ 订单信息 ═══ */
	.info-card {
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 0 30rpx;
	}

	.info-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 24rpx 0;
		border-bottom: 1rpx solid #eeeeee;
	}

	.info-row:last-child {
		border-bottom: none;
	}

	.label {
		font-size: 28rpx;
		color: #666666;
	}

	.value-wrap {
		display: flex;
		align-items: center;
		gap: 12rpx;
	}

	.value {
		font-size: 28rpx;
		color: #333333;
	}

	.copy-icon {
		font-size: 24rpx;
		color: #999999;
		padding: 4rpx 12rpx;
		border: 1rpx solid #dddddd;
		border-radius: 8rpx;
	}
</style>
