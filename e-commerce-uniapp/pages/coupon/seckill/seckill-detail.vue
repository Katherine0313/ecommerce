<template>
<view class="page">
		<CustomNavbar title="限时秒杀" :show-back="true" />

		<!-- 券主信息 -->
		<view class="hero" v-if="detail">
			<view class="hero-value">
				<text v-if="detail.type === 'DISCOUNT'" class="hv-num">{{ (Number(detail.discountRate) * 10).toFixed(1) }}</text>
				<text v-else class="hv-num">¥{{ fmt(detail.faceValue) }}</text>
				<text class="hv-unit">{{ detail.type === 'DISCOUNT' ? '折券' : '代金券' }}</text>
			</view>
			<text class="hero-name">{{ detail.name }}</text>
			<view class="hero-price">
				<text class="hp-now">秒杀价 ¥{{ fmt(detail.seckillPrice) }}</text>
				<text class="hp-origin">券面价 ¥{{ fmt(detail.faceValue) }}</text>
			</view>
		</view>

		<!-- 活动状态 -->
		<view class="status-box" v-if="detail">
			<view class="status-row">
				<text class="status-label">活动状态</text>
				<text class="status-value">{{ detail.activityStatusDesc || statusText(detail.activityStatus) }}</text>
			</view>
			<view class="status-row">
				<text class="status-label">活动时间</text>
				<text class="status-value">{{ detail.seckillBeginTime }} ~ {{ detail.seckillEndTime }}</text>
			</view>
			<view class="status-row" v-if="detail.activityStatus === 'ACTIVE'">
				<text class="status-label">距结束</text>
				<text class="status-value countdown">{{ countdownText }}</text>
			</view>
			<view class="status-row">
				<text class="status-label">使用门槛</text>
				<text class="status-value">{{ thresholdText }}</text>
			</view>
			<view class="status-row">
				<text class="status-label">剩余库存</text>
				<text class="status-value">{{ detail.remainQuantity }} / {{ detail.totalQuantity }} 张</text>
			</view>
		</view>

		<!-- 规则说明 -->
		<view class="desc-box" v-if="detail && detail.description">
			<text class="desc-title">使用说明</text>
			<text class="desc-text">{{ detail.description }}</text>
		</view>

		<!-- 规则提示 -->
		<view class="rule-box">
			<text class="rule-title">活动规则</text>
			<text class="rule-text">· 每人限购 1 张，先到先得</text>
			<text class="rule-text">· 下单后 15 分钟内完成支付，超时订单自动关闭</text>
			<text class="rule-text">· 支付成功后优惠券发放到「我的优惠券」</text>
			<text class="rule-text">· 未使用的券可在订单页申请退款，由商家审核</text>
		</view>

		<!-- 底部操作栏 -->
		<view class="bottom-bar">
			<view class="bar-info">
				<text class="bar-price">¥{{ detail ? fmt(detail.seckillPrice) : '0.00' }}</text>
				<text class="bar-tip">{{ tipText }}</text>
			</view>
			<view class="bar-btn" :class="{ disabled: !canBuy }" @click="handleBuy">
				{{ btnText }}
			</view>
		</view>

		<!-- 模拟微信支付弹窗：6 位密码框 + 数字键盘 -->
		<PayPopup :visible="showPayPopup" :amount="pendingOrder ? pendingOrder.payAmount : 0"
			:loading="paying" @close="closePay" @confirm="confirmPay" />
	</view>
</template>

<script>
	import {
		createSeckillOrder,
		getSeckillDetail,
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
				couponId: null,
				detail: null,
				loading: false,
				submitting: false,
				paying: false,
				showPayPopup: false,
				pendingOrder: null
			}
		},
		computed: {
			countdownText() {
				return this.detail ? this.fmtCountdown(this.detail.seckillEndTime) : '00:00:00'
			},
			thresholdText() {
				if (!this.detail) return '无门槛'
				const t = Number(this.detail.thresholdAmount) || 0
				return t > 0 ? '满 ¥' + this.fmt(t) + ' 可用' : '无门槛'
			},
			// 只有进行中且有库存才能买
			canBuy() {
				return !!this.detail &&
					this.detail.activityStatus === 'ACTIVE' &&
					(Number(this.detail.remainQuantity) || 0) > 0
			},
			btnText() {
				if (!this.detail) return '加载中'
				if (this.detail.activityStatus === 'NOT_STARTED') return '活动未开始'
				if (this.detail.activityStatus === 'ENDED') return '活动已结束'
				if (this.detail.activityStatus === 'STOPPED') return '活动已停止'
				if ((Number(this.detail.remainQuantity) || 0) <= 0) return '已抢光'
				return '立即抢购'
			},
			tipText() {
				if (!this.detail) return ''
				if (this.detail.activityStatus === 'ACTIVE') {
					return '已有 ' + ((Number(this.detail.totalQuantity) || 0) - (Number(this.detail.remainQuantity) || 0)) + ' 人抢'
				}
				return '每人限购 1 张'
			}
		},
		onLoad(options) {
			this.couponId = options && options.id ? options.id : null
			if (!this.couponId) {
				uni.showToast({ title: '缺少券参数', icon: 'none' })
				return
			}
			this.load()
		},
		methods: {
			load() {
				this.loading = true
				getSeckillDetail(this.couponId)
					.then((data) => {
						this.detail = data || null
					})
					.catch(() => {
						this.detail = null
					})
					.finally(() => {
						this.loading = false
					})
			},
			statusText(status) {
				return {
					NOT_STARTED: '未开始',
					ACTIVE: '进行中',
					ENDED: '已结束',
					STOPPED: '已停止'
				}[status] || '—'
			},
			handleBuy() {
				if (!this.canBuy || this.submitting) return
				this.submitting = true
				ensureLogin()
					.then(() => createSeckillOrder(this.couponId))
					.then((order) => {
						// 下单成功：拉起模拟支付弹窗，输满 6 位密码后调 /pay
						this.pendingOrder = order
						this.showPayPopup = true
					})
					.catch(() => {})
					.finally(() => {
						this.submitting = false
					})
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
						uni.showToast({ title: '抢购成功', icon: 'success' })
						// 支付后券已发到「我的优惠券」，直接跳过去让用户看到券到账
						// 延迟一点再跳，等 toast 显示完（成功图标约 1.5s）
						setTimeout(() => {
							uni.redirectTo({ url: '/pages/coupon/coupons' })
						}, 1500)
					})
					.catch(() => {})
					.finally(() => {
						this.paying = false
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
		padding-bottom: 160rpx;
		background: #f8f8f8;
	}

	.hero {
		padding: 40rpx 32rpx;
		background: linear-gradient(135deg, #FF8A00 0%, #FF4D00 100%);
		color: #ffffff;
	}

	.hero-value {
		display: flex;
		align-items: baseline;
	}

	.hv-num {
		font-size: 64rpx;
		font-weight: bold;
	}

	.hv-unit {
		margin-left: 8rpx;
		font-size: 24rpx;
	}

	.hero-name {
		display: block;
		margin-top: 8rpx;
		font-size: 30rpx;
	}

	.hero-price {
		display: flex;
		align-items: baseline;
		margin-top: 20rpx;
	}

	.hp-now {
		font-size: 40rpx;
		font-weight: bold;
	}

	.hp-origin {
		margin-left: 16rpx;
		font-size: 24rpx;
		text-decoration: line-through;
		opacity: 0.75;
	}

	.status-box,
	.desc-box,
	.rule-box {
		margin: 20rpx 24rpx 0;
		padding: 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	.status-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 12rpx 0;
	}

	.status-label {
		font-size: 26rpx;
		color: #999999;
	}

	.status-value {
		font-size: 26rpx;
		color: #333333;
	}

	.status-value.countdown {
		font-weight: bold;
		color: #FF4D00;
	}

	.desc-title,
	.rule-title {
		display: block;
		margin-bottom: 12rpx;
		font-size: 28rpx;
		font-weight: bold;
		color: #333333;
	}

	.desc-text {
		display: block;
		font-size: 26rpx;
		color: #666666;
		line-height: 1.6;
	}

	.rule-text {
		display: block;
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #999999;
		line-height: 1.6;
	}

	.bottom-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 20rpx 24rpx;
		padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
		background: #ffffff;
		box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
	}

	.bar-info {
		display: flex;
		flex-direction: column;
	}

	.bar-price {
		font-size: 40rpx;
		font-weight: bold;
		color: #FF4D00;
	}

	.bar-tip {
		margin-top: 2rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.bar-btn {
		padding: 20rpx 60rpx;
		font-size: 30rpx;
		font-weight: bold;
		color: #ffffff;
		background: linear-gradient(90deg, #FF8A00, #FF4D00);
		border-radius: 44rpx;
	}

	.bar-btn.disabled {
		background: #cccccc;
	}
</style>
