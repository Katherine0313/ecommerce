<template>
<view class="page">
		<CustomNavbar title="秒杀订单" :show-back="true" />

		<!-- 状态页签 -->
		<view class="tabs">
			<view v-for="tab in tabs" :key="tab.value" class="tab" :class="{ active: current === tab.value }"
			      @click="switchTab(tab.value)">
				<text>{{ tab.label }}</text>
			</view>
		</view>

		<!-- 订单列表 -->
		<view v-if="list.length" class="order-list">
			<view v-for="order in list" :key="order.orderNo" class="order-card">
				<view class="card-head">
					<text class="order-no">{{ order.orderNo }}</text>
					<text class="order-status" :class="statusClass(order.status)">{{ order.statusDesc }}</text>
				</view>

				<view class="card-body">
					<view class="coupon-icon">
						<text class="ci-num">券</text>
					</view>
					<view class="card-info">
						<text class="coupon-name">{{ order.couponName }}</text>
						<text class="order-time">下单时间：{{ order.createTime || '-' }}</text>
						<text v-if="order.status === 2" class="order-time">支付时间：{{ order.payTime || '-' }}</text>
					</view>
					<view class="card-price">
						<text class="price">¥{{ fmt(order.payAmount) }}</text>
					</view>
				</view>

				<!-- 底部操作 -->
				<view class="card-foot">
					<!-- 待支付：15 分钟倒计时 -->
					<text v-if="order.status === 1" class="time-tip"
					      :class="{ expired: isExpired(order) }">
						{{ isExpired(order) ? '订单已超时关闭' : '剩余 ' + fmtCountdown(order.expireTime) }}
					</text>
					<text v-else class="time-tip">{{ statusHint(order) }}</text>

					<view class="foot-actions">
						<template v-if="order.status === 1">
							<view class="btn cancel" @click="handleCancel(order)">取消订单</view>
							<view v-if="!isExpired(order)" class="btn pay" @click="handlePay(order)">去支付</view>
						</template>
						<template v-else-if="order.status === 2">
							<view class="btn refund" @click="handleRefund(order)">申请退款</view>
						</template>
						<template v-else>
							<view class="btn detail" @click="goDetail(order)">查看详情</view>
						</template>
					</view>
				</view>
			</view>
		</view>

		<view v-else-if="!loading" class="empty">
			<text class="empty-text">暂无秒杀订单</text>
		</view>

		<!-- 模拟微信支付弹窗 -->
		<PayPopup :visible="showPayPopup" :amount="pendingOrder ? pendingOrder.payAmount : 0"
			:loading="paying" @close="closePay" @confirm="confirmPay" />

		<!-- 退款申请弹窗 -->
		<view v-if="refundVisible" class="refund-mask" @click="refundVisible = false">
			<view class="refund-box" @click.stop>
				<text class="refund-title">申请退款</text>
				<textarea class="refund-input" v-model="refundReason" placeholder="请填写退款原因（选填）"
					maxlength="200" placeholder-class="input-ph"></textarea>
				<view class="refund-btns">
					<view class="btn cancel" @click="refundVisible = false">取消</view>
					<view class="btn pay" @click="submitRefund">提交申请</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import {
		applySeckillRefund,
		cancelSeckillOrder,
		getMySeckillOrders,
		paySeckillOrder
	} from '../../../api/seckill.js'
	import { ensureLogin } from '../../../utils/auth.js'
	import PayPopup from '../../../components/pay-popup.vue'
	import countdown from '../../index/component/countdown.js'

	export default {
		components: {
			PayPopup
		},
		mixins: [countdown],
		data() {
			return {
				tabs: [
					{ value: '', label: '全部' },
					{ value: 1, label: '待支付' },
					{ value: 2, label: '已支付' },
					{ value: 3, label: '已取消' },
					{ value: 4, label: '已退款' }
				],
				current: '',
				list: [],
				loading: false,
				operating: false,
				paying: false,
				showPayPopup: false,
				pendingOrder: null,
				refundVisible: false,
				refundTarget: null,
				refundReason: ''
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			load() {
				this.loading = true
				ensureLogin()
					.then(() => getMySeckillOrders(1, 50, this.current === '' ? undefined : this.current))
					.then((page) => {
						this.list = (page && page.list) || []
					})
					.catch(() => {
						this.list = []
					})
					.finally(() => {
						this.loading = false
					})
			},
			switchTab(value) {
				if (this.current === value) return
				this.current = value
				this.load()
			},
			isExpired(order) {
				return this.isEnded(order.expireTime)
			},
			statusClass(status) {
				return {
					1: 'pending',
					2: 'paid',
					3: 'canceled',
					4: 'refunded'
				}[status] || ''
			},
			statusHint(order) {
				return {
					2: '优惠券已发放到「我的优惠券」',
					3: '订单已取消',
					4: '退款已完成，优惠券已作废'
				}[order.status] || ''
			},
			handleCancel(order) {
				if (this.operating) return
				uni.showModal({
					title: '取消订单',
					content: '取消后订单关闭，库存回补，确定取消吗？',
					success: (res) => {
						if (!res.confirm) return
						this.operating = true
						cancelSeckillOrder(order.orderNo)
							.then(() => {
								uni.showToast({ title: '已取消', icon: 'success' })
								this.load()
							})
							.catch(() => {})
							.finally(() => {
								this.operating = false
							})
					}
				})
			},
			handlePay(order) {
				if (this.operating) return
				this.pendingOrder = order
				this.showPayPopup = true
			},
			closePay() {
				this.showPayPopup = false
				this.pendingOrder = null
			},
			confirmPay() {
				if (this.paying || !this.pendingOrder) return
				this.paying = true
				paySeckillOrder(this.pendingOrder.orderNo, 'MOCK')
					.then(() => {
						this.showPayPopup = false
						this.pendingOrder = null
						uni.showToast({ title: '支付成功', icon: 'success' })
						this.load()
					})
					.catch(() => {})
					.finally(() => {
						this.paying = false
					})
			},
			handleRefund(order) {
				this.refundTarget = order
				this.refundReason = ''
				this.refundVisible = true
			},
			submitRefund() {
				if (this.operating || !this.refundTarget) return
				this.operating = true
				applySeckillRefund(this.refundTarget.orderNo, { reason: this.refundReason, images: '' })
					.then(() => {
						this.refundVisible = false
						uni.showToast({ title: '已提交，等待审核', icon: 'none' })
						this.load()
					})
					.catch(() => {})
					.finally(() => {
						this.operating = false
					})
			},
			goDetail(order) {
				uni.navigateTo({
					url: '/pages/coupon/seckill/seckill-detail?id=' + order.couponId
				})
			},
			fmt(value) {
				const n = Number(value)
				return Number.isFinite(n) ? n.toFixed(2) : '0.00'
			}
		}
	}
</script>

<style scoped>
	.page {
		min-height: 100vh;
		background: #f8f8f8;
	}

	.tabs {
		display: flex;
		background: #ffffff;
	}

	.tab {
		flex: 1;
		padding: 24rpx 0;
		text-align: center;
		font-size: 28rpx;
		color: #666666;
		border-bottom: 4rpx solid transparent;
	}

	.tab.active {
		color: #FF6700;
		font-weight: bold;
		border-bottom-color: #FF6700;
	}

	.order-list {
		padding: 20rpx 24rpx 60rpx;
	}

	.order-card {
		margin-bottom: 20rpx;
		padding: 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	.card-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-bottom: 16rpx;
		border-bottom: 1rpx solid #f2f2f2;
	}

	.order-no {
		font-size: 22rpx;
		color: #999999;
	}

	.order-status {
		font-size: 24rpx;
		font-weight: bold;
	}

	.order-status.pending {
		color: #FF6700;
	}

	.order-status.paid {
		color: #52c41a;
	}

	.order-status.canceled {
		color: #999999;
	}

	.order-status.refunded {
		color: #bbbbbb;
	}

	.card-body {
		display: flex;
		align-items: center;
		padding: 20rpx 0;
	}

	.coupon-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 96rpx;
		height: 96rpx;
		background: linear-gradient(135deg, #FF8A00, #FF4D00);
		border-radius: 12rpx;
	}

	.ci-num {
		font-size: 34rpx;
		font-weight: bold;
		color: #ffffff;
	}

	.card-info {
		flex: 1;
		display: flex;
		flex-direction: column;
		padding-left: 20rpx;
	}

	.coupon-name {
		font-size: 28rpx;
		font-weight: bold;
		color: #333333;
	}

	.order-time {
		margin-top: 6rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.card-price {
		display: flex;
		align-items: center;
	}

	.price {
		font-size: 34rpx;
		font-weight: bold;
		color: #E4393C;
	}

	.card-foot {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-top: 16rpx;
		border-top: 1rpx solid #f2f2f2;
	}

	.time-tip {
		font-size: 22rpx;
		color: #FF6700;
	}

	.time-tip.expired {
		color: #999999;
	}

	.foot-actions {
		display: flex;
		gap: 16rpx;
	}

	.btn {
		padding: 12rpx 28rpx;
		font-size: 24rpx;
		border-radius: 28rpx;
	}

	.btn.cancel,
	.btn.detail {
		color: #666666;
		border: 1rpx solid #dddddd;
	}

	.btn.pay {
		color: #ffffff;
		background: linear-gradient(90deg, #FF8A00, #FF4D00);
	}

	.btn.refund {
		color: #FF6700;
		border: 1rpx solid #FF6700;
	}

	.empty {
		display: flex;
		justify-content: center;
		padding-top: 220rpx;
	}

	.empty-text {
		font-size: 28rpx;
		color: #999999;
	}

	/* 退款申请弹窗 */
	.refund-mask {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(0, 0, 0, 0.5);
		z-index: 1200;
	}

	.refund-box {
		width: 600rpx;
		padding: 32rpx;
		background: #ffffff;
		border-radius: 20rpx;
		box-sizing: border-box;
	}

	.refund-title {
		display: block;
		margin-bottom: 20rpx;
		font-size: 32rpx;
		font-weight: bold;
		color: #333333;
		text-align: center;
	}

	.refund-input {
		width: 100%;
		height: 160rpx;
		padding: 16rpx;
		font-size: 26rpx;
		color: #333333;
		background: #f8f8f8;
		border-radius: 12rpx;
		box-sizing: border-box;
	}

	.refund-btns {
		display: flex;
		justify-content: flex-end;
		gap: 20rpx;
		margin-top: 24rpx;
	}
</style>
