<template>
	<!-- 订单卡片：头部 + 商品明细 + 物流 + 底部操作区（按状态渲染）。
	     自包含，父页面只传 order + now；底部操作区也提供 #foot 插槽可覆盖（待付款页用） -->
	<view class="order-card" @click="$emit('click', order)">
		<!-- 头部：订单号（可选复制）+ 状态 -->
		<view class="order-head">
			<view class="order-no-wrap">
				<text class="order-no">{{ order.orderNo }}</text>
				<uni-icons v-if="showCopy" type="copy" size="26" color="#999" @click.stop="copyOrderNo"></uni-icons>
			</view>
			<text v-if="isClosed" class="order-status-pill">{{ statusLabel }}</text>
			<text v-else class="order-status">{{ statusLabel }}</text>
		</view>

		<!-- 商品明细：一个订单可能多件，逐件展示 -->
		<view class="goods-item" v-for="item in order.items" :key="item.productId">
			<image lazy-load class="goods-img" :src="goodsImgUrl(item.image, item.productId)" mode="aspectFill"></image>
			<view class="goods-info">
				<text class="goods-name">{{ item.productName }}</text>
				<view class="goods-price-row">
					<text class="goods-price">¥{{ fmtMoney(item.salesPrice) }}</text>
					<!-- 原价只在确实有优惠时展示，避免无活动订单出现两行相同价格 -->
					<text v-if="item.originalPrice && +item.originalPrice > +item.salesPrice"
					      class="goods-origin">¥{{ fmtMoney(item.originalPrice) }}</text>
				</view>
			</view>
			<view class="goods-info-r">
				<text class="goods-qty">×{{ item.quantity }}</text>
				<view>
					<text class="goods-action" v-if="order.afterSale" @click.stop="goAfterSaleDetail">售后详情</text>
					<text class="goods-action" v-else-if="isRefundable || isAfterSaleable" @click.stop="applyAfterSale(item)">申请售后</text>
				</view>
			</view>
		</view>

		<!-- 物流信息：已发货/已完成显示最新一条轨迹，点击进物流详情 -->
		<view class="logistics-row" v-if="order.logisticsStatus" @click.stop="goLogistics">
			<view class="logi-icon">📦</view>
			<view class="logi-text">{{ order.logisticsStatus }} {{ order.latestTrace }}</view>
			<view class="logi-arrow"></view>
		</view>

		<!-- 底部操作区：默认按订单状态渲染，父页面也可用 #foot 插槽覆盖 -->
		<slot name="foot">
			<view class="order-foot">
				<!-- 待付款：倒计时 + 取消 + 付款；超时只留提示 -->
				<template v-if="isPendingPay">
					<template v-if="!isExpired">
						<text class="time-tip">剩余 {{ countdownText }}</text>
						<view class="btn-cancel" @click.stop="$emit('cancel', order)">取消订单</view>
						<view class="btn-pay" @click.stop="$emit('pay', order)">付款</view>
					</template>
					<template v-else>
						<text class="time-tip closed-tip">订单已超时关闭</text>
					</template>
				</template>
				<!-- 已取消：删除 / 再来一单 -->
				<template v-else-if="isCancelled">
					<view class="btn-delete" @click.stop="$emit('delete', order)">删除订单</view>
					<view class="btn-buy-again" @click.stop="$emit('buy-again', order)">再来一单</view>
				</template>
				<!-- 已发货（待收货）：确认收货 -->
				<template v-else-if="isShipped">
					<text class="order-time">{{ formatOrderTime(order.createdAt) }}</text>
					<view class="btn-confirm" @click.stop="$emit('confirm', order)">确认收货</view>
				</template>
				<!-- 已完成：删除 / 再来一单 / 评价商品（已评价则显示「已评价」） -->
				<template v-else-if="isFinished">
					<view class="btn-delete" @click.stop="$emit('delete', order)">删除订单</view>
					<view class="foot-right">
						<view class="btn-buy-again" @click.stop="$emit('buy-again', order)">再来一单</view>
						<view v-if="!order.reviewed" class="btn-review" @click.stop="$emit('review', order)">评价商品</view>
						<text v-else class="reviewed-tag">已评价</text>
					</view>
				</template>
				<!-- 其它（待发货）：下单时间 + 合计 -->
				<template v-else>
					<text class="order-time">{{ formatOrderTime(order.createdAt) }}</text>
					<view class="order-total">
						<text class="total-label">共{{ totalCount }}件 合计</text>
						<text class="total-price">¥{{ fmtMoney(order.payAmount) }}</text>
					</view>
				</template>
			</view>
		</slot>
	</view>
</template>

<script>
	import {
		fmtMoney,
		goodsImgUrl,
		formatOrderTime,
		parseOrderTime
	} from '../../utils/format.js'
	import {
		getAfterSaleByOrder
	} from '../../api/after-sale.js'

	// 待付款超时时长：与后端 OrderServiceImpl.PENDING_PAY_TIMEOUT_SECONDS（1800 秒 = 30 分钟）一致
	const PAY_TIMEOUT_MS = 30 * 60 * 1000

	export default {
		name: 'OrderCard',
		props: {
			// 后端 OrderVO
			order: {
				type: Object,
				required: true
			},
			// 是否显示「复制订单号」图标（待付款页用）
			showCopy: {
				type: Boolean,
				default: false
			},
			// 倒计时当前时间戳：父页面每秒刷新传进来，卡片共享一个定时器，避免每卡一个 setInterval
			now: {
				type: Number,
				default: () => Date.now()
			}
		},
		computed: {
			// 待付款订单的超时时间戳（下单时间 + 30 分钟）
			expireTime() {
				if (Number(this.order.orderStatus) !== 1) {
					return null
				}
				return parseOrderTime(this.order.createdAt) + PAY_TIMEOUT_MS
			},
			// 是否待付款超时
			isExpired() {
				return this.expireTime != null && this.expireTime <= this.now
			},
			// 是否「关闭」展示（灰色胶囊）：已取消(6) 或 待付款超时
			isClosed() {
				return Number(this.order.orderStatus) === 6 || this.isExpired
			},
			// 状态文案：超时显示「已关闭」，否则后端中文描述
			statusLabel() {
				return this.isExpired ? '已关闭' : this.order.orderStatusDesc
			},
			// 待付款（1）
			isPendingPay() {
				return Number(this.order.orderStatus) === 1
			},
			// 已取消（6）
			isCancelled() {
				return Number(this.order.orderStatus) === 6
			},
			// 已发货（4，用户视角待收货）
			isShipped() {
				return Number(this.order.orderStatus) === 4
			},
			// 已完成（5）
			isFinished() {
				return Number(this.order.orderStatus) === 5
			},
			// 待发货（3）可申请售后
			isRefundable() {
				const s = Number(this.order.orderStatus)
				return s === 3
			},
			// 已发货（4）可申请售后
			isAfterSaleable() {
				return Number(this.order.orderStatus) === 4
			},
			// 剩余时间文案：MM : SS
			countdownText() {
				const remain = this.expireTime - this.now
				if (remain <= 0) {
					return '0分0秒'
				}
				const m = Math.floor(remain / 60000)
				const s = Math.floor((remain % 60000) / 1000)
				return String(m).padStart(2, '0') + ' : ' + String(s).padStart(2, '0')
			},
			// 订单商品总件数
			totalCount() {
				return (this.order.items || []).reduce((s, i) => s + (Number(i.quantity) || 0), 0)
			}
		},
		methods: {
			fmtMoney,
			goodsImgUrl,
			formatOrderTime,
			// 复制订单号到剪贴板
			copyOrderNo() {
				uni.setClipboardData({
					data: this.order.orderNo,
					success: () => uni.showToast({
						title: '已复制',
						icon: 'none'
					})
				})
			},
			// 跳物流详情页
			goLogistics() {
				uni.navigateTo({
					url: '/pages/order/logistics?id=' + this.order.id
				})
			},
			// 申请售后：跳售后服务页（单品售后，传订单项 ID）
			applyAfterSale(item) {
				uni.navigateTo({
					url: '/pages/after-sales/apply?orderId=' + this.order.id + '&orderItemId=' + (item.orderItemId || '')
				})
			},
			// 查看售后详情：按订单查售后单，按类型跳对应详情页
			goAfterSaleDetail() {
				getAfterSaleByOrder(this.order.id).then((afterSale) => {
					if (!afterSale) return
					let page = '/pages/after-sales/only-refund-detail'
					if (afterSale.afterSaleType === 'return_refund') {
						page = '/pages/after-sales/return-refund-detail'
					} else if (afterSale.afterSaleType === 'exchange') {
						page = '/pages/after-sales/exchange-detail'
					}
					uni.navigateTo({ url: page + '?id=' + afterSale.id })
				}).catch(() => {})
			}
		}
	}
</script>

<style scoped>
	/* ═══ 订单卡片 ═══ */
	.order-card {
		margin: 20rpx 0 0;
		padding: 0 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	/* ═══ 头部 ═══ */
	.order-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 80rpx;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.order-no-wrap {
		display: flex;
		align-items: center;
	}

	.order-no {
		margin-right: 10rpx;
		font-size: 24rpx;
		color: #999999;
	}

	/* 正常状态：品牌橙字 */
	.order-status {
		font-size: 26rpx;
		font-weight: bold;
	}

	/* 已取消/超时关闭状态：灰色胶囊标签 */
	.order-status-pill {
		padding: 6rpx 24rpx;
		background: #f2f2f2;
		color: #888888;
		border-radius: 32rpx;
		font-size: 24rpx;
	}

	/* ═══ 商品明细 ═══ */
	.goods-item {
		display: flex;
		align-items: center;
		padding: 18rpx 0;
	}

	.goods-item+.goods-item {
		border-top: 1rpx solid #f5f5f5;
	}

	.goods-img {
		width: 100rpx;
		height: 100rpx;
		border-radius: 12rpx;
		flex-shrink: 0;
	}

	.goods-info {
		flex: 1;
		min-width: 0;
		margin-left: 20rpx;
	}

	.goods-info-r {
		display: flex;
		flex-direction: column;
	}

	.goods-name {
		display: block;
		font-size: 28rpx;
		color: #333333;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.goods-price-row {
		display: flex;
		align-items: baseline;
		margin-top: 8rpx;
	}

	/* 活动前原价：划线展示 */
	.goods-origin {
		margin-left: 10rpx;
		font-size: 22rpx;
		color: #bbbbbb;
		text-decoration: line-through;
	}

	.goods-price {
		display: block;
		margin-top: 10rpx;
		font-size: 26rpx;
		color: #999999;
	}

	.goods-qty {
		flex-shrink: 0;
		margin: 20rpx 20rpx 14rpx;
		font-size: 26rpx;
		color: #999999;
	}

	.goods-action {
		flex-shrink: 0;
		font-size: 24rpx;
		color: #ccc;
		border-radius: 40rpx;
		border: 1rpx solid #ccc;
		padding: 2rpx 10rpx;
	}

	/* ═══ 物流信息行 ═══ */
	.logistics-row {
		display: flex;
		align-items: center;
		padding: 20rpx 0;
		border-top: 1rpx solid #f5f5f5;
	}

	.logi-icon {
		font-size: 32rpx;
		margin-right: 12rpx;
	}

	.logi-text {
		flex: 1;
		min-width: 0;
		font-size: 24rpx;
		color: #666666;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.logi-arrow {
		flex-shrink: 0;
		margin-left: 8rpx;
		width: 12rpx;
		height: 12rpx;
		border-top: 2rpx solid #cccccc;
		border-right: 2rpx solid #cccccc;
		transform: rotate(45deg);
	}

	/* ═══ 底部操作区 ═══ */
	.order-foot {
		display: flex;
		justify-content: space-between;
		padding: 18rpx 0 20rpx;
		border-top: 1rpx solid #f0f0f0;
	}

	.order-time {
		margin-right: auto;
		font-size: 22rpx;
		color: #999999;
	}

	.order-total {
		display: flex;
		align-items: baseline;
	}

	.total-label {
		font-size: 24rpx;
		color: #666666;
	}

	.total-price {
		margin-left: 8rpx;
		font-size: 32rpx;
		font-weight: bold;
		color: #FF4E2A;
	}

	/* 待付款倒计时 */
	.time-tip {
		margin-right: auto;
		font-size: 26rpx;
		color: #f56c6c;
	}

	.time-tip.closed-tip {
		color: #999999;
	}

	/* 各按钮 */
	.btn-delete {
		padding: 10rpx 32rpx;
		font-size: 22rpx;
		color: #333333;
	}

	.btn-buy-again {
		padding: 4rpx 26rpx;
		border: 1rpx solid #cccccc;
		border-radius: 32rpx;
		font-size: 26rpx;
		color: #333333;
	}

	.btn-cancel {
		padding: 4rpx 26rpx;
		margin-right: 20rpx;
		color: #333333;
		font-size: 22rpx;
	}

	.btn-pay {
		padding: 10rpx 52rpx;
		background: #FF6700;
		color: #ffffff;
		border-radius: 40rpx;
		font-size: 28rpx;
	}

	.btn-confirm {
		padding: 4rpx 26rpx;
		background: #FF6700;
		color: #ffffff;
		border-radius: 32rpx;
		font-size: 26rpx;
	}

	.btn-review {
		padding: 4rpx 26rpx;
		border: 1rpx solid #cccccc;
		border-radius: 32rpx;
		font-size: 26rpx;
		color: #333333;
	}

	/* 已完成状态右侧按钮组：再来一单 + 评价商品 靠右相邻 */
	.foot-right {
		display: flex;
		align-items: center;
	}

	.foot-right .btn-buy-again {
		margin-right: 20rpx;
	}

	/* 已评价：纯文本占位，与「评价商品」按钮位置对齐 */
	.reviewed-tag {
		font-size: 24rpx;
		color: #999999;
	}
</style>
