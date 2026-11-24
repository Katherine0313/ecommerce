<template>
<view class="page">
		<CustomNavbar title="售后服务" :show-back="true" />

		<!-- 退款商品卡片：整单商品逐件展示 -->
		<view class="goods-card">
			<view></view>
			<text>退款商品</text>
			<view class="goods-item" v-for="item in items" :key="item.productId">
				<image lazy-load class="goods-img" :src="goodsImgUrl(item.image, item.productId)" mode="aspectFill"></image>
				<view class="goods-info">
					<text class="goods-name">{{ item.productName }}</text>
					<view class="goods-bottom">
						<text class="goods-price">¥{{ fmtMoney(item.salesPrice) }}</text>
						<text class="goods-qty">×{{ item.quantity }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 选择服务类型 -->
		<view class="type-list">
			<view v-for="t in availableTypes" :key="t.code" class="type-item" :class="{ active: selectedType === t.code }"
				@click="selectedType = t.code">
				<view class="type-icon">{{ t.icon }}</view>
				<view class="type-body">
					<text class="type-title">{{ t.title }}</text>
					<text class="type-desc">{{ t.desc }}</text>
				</view>
				<view class="type-arrow"></view>
			</view>
		</view>

		<!-- 底部下一步 -->
		<view class="footer-bar">
			<view class="next-btn" @click="next">下一步</view>
		</view>
	</view>
</template>

<script>
	import {
		getOrder
	} from '../../api/order.js'
	import {
		goodsImgUrl,
		fmtMoney
	} from '../../utils/format.js'

	export default {
		data() {
			return {
				orderId: null,
				orderItemId: null,
				items: [],
				selectedType: '',
				orderStatus: 0, // 订单状态（未发货 3 只能仅退款）
				// 三种服务类型对应后端 after_sale_type：only_refund / return_refund / exchange
				typeOptions: [{
						code: 'only_refund',
						icon: '¥',
						title: '我要退款（无需退货）',
						desc: '没收到货，或与卖家协商同意不用退货只退款'
					},
					{
						code: 'return_refund',
						icon: '退',
						title: '我要退货退款',
						desc: '已收到货，需要退还收到的货物'
					},
					{
						code: 'exchange',
						icon: '换',
						title: '换货',
						desc: '有非人为损坏的质量问题，联系卖家协商换货'
					}
				]
			}
		},
		computed: {
			// 按订单状态过滤服务类型：未发货(3)只能仅退款，已发货(4)三种都可选
			availableTypes() {
				if (this.orderStatus === 3) {
					return this.typeOptions.filter(t => t.code === 'only_refund')
				}
				return this.typeOptions
			}
		},
		onLoad(options) {
			this.orderId = options.orderId
			this.orderItemId = options.orderItemId ? Number(options.orderItemId) : null
			if (this.orderId) {
				this.loadOrder()
			}
		},
		methods: {
			goodsImgUrl,
			fmtMoney,
			loadOrder() {
				getOrder(this.orderId).then((order) => {
					this.orderStatus = Number(order.orderStatus) || 0
					const all = order.items || []
					// 传了 orderItemId 只展示该商品，否则整单
					this.items = this.orderItemId
						? all.filter(i => i.orderItemId === this.orderItemId)
						: all
				}).catch(() => {})
			},
			next() {
				if (!this.selectedType) {
					uni.showToast({
						title: '请选择服务类型',
						icon: 'none'
					})
					return
				}
				// 按服务类型跳对应表单页：仅退款 / 退货退款 / 换货
				const query = '?orderId=' + this.orderId + '&orderItemId=' + (this.orderItemId || '')
				if (this.selectedType === 'only_refund') {
					uni.navigateTo({ url: '/pages/after-sales/only-refund' + query })
				} else if (this.selectedType === 'return_refund') {
					uni.navigateTo({ url: '/pages/after-sales/return-refund' + query })
				} else if (this.selectedType === 'exchange') {
					uni.navigateTo({ url: '/pages/after-sales/exchange' + query })
				} else {
					uni.showToast({
						title: '该类型页面待开发',
						icon: 'none'
					})
				}
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
		padding-bottom: 160rpx;
	}

	/* 商品卡片 */
	.goods-card {
		background: #ffffff;
		margin: 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.goods-card .goods-item {
		display: flex;
		align-items: center;
		margin-top: 10rpx;
	}

	.goods-item+.goods-item {
		border-top: 1rpx solid #f5f5f5;
		margin-top: 18rpx;
		padding-top: 20rpx;
	}

	.goods-img {
		width: 120rpx;
		height: 120rpx;
		border-radius: 12rpx;
		flex-shrink: 0;
	}

	.goods-info {
		flex: 1;
		min-width: 0;
		margin-left: 20rpx;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
		height: 120rpx;
	}

	.goods-name {
		font-size: 28rpx;
		color: #333333;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.goods-bottom {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.goods-price {
		font-size: 30rpx;
		color: #333333;
	}

	.goods-qty {
		font-size: 26rpx;
		color: #666666;
	}

	/* 服务类型列表 */
	.type-list {
		background: #ffffff;
		margin: 0 20rpx;
		border-radius: 16rpx;
	}

	.type-item {
		display: flex;
		align-items: center;
		padding: 30rpx 24rpx;
	}

	.type-item+.type-item {
		border-top: 1rpx solid #f5f5f5;
	}

	.type-icon {
		width: 80rpx;
		height: 80rpx;
		border-radius: 50%;
		background: #f2f2f2;
		color: #FF6700;
		font-size: 36rpx;
		text-align: center;
		line-height: 80rpx;
		flex-shrink: 0;
	}

	.type-item.active .type-icon {
		background: #FF6700;
		color: #ffffff;
	}

	.type-body {
		flex: 1;
		min-width: 0;
		margin-left: 24rpx;
		display: flex;
		flex-direction: column;
		gap: 8rpx;
	}

	.type-title {
		font-size: 30rpx;
		color: #333333;
		font-weight: bold;
	}

	.type-desc {
		font-size: 24rpx;
		color: #666666;
	}

	.type-arrow {
		flex-shrink: 0;
		margin-left: 12rpx;
		width: 16rpx;
		height: 16rpx;
		border-top: 2rpx solid #cccccc;
		border-right: 2rpx solid #cccccc;
		transform: rotate(45deg);
	}

	/* 底部按钮 */
	.footer-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		padding: 16rpx 20rpx;
		padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
		background: #ffffff;
		box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	.next-btn {
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		background: #FF6700;
		color: #ffffff;
		border-radius: 44rpx;
		font-size: 30rpx;
	}
</style>
