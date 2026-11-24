<template>
<view class="page">
		<!-- 自定义导航栏：二级页，带返回按钮 -->
		<CustomNavbar title="待付款" :show-back="true" />

		<!-- 待付款订单列表：复用 OrderCard，底部填实付金额 + 倒计时 + 取消/付款 -->
		<view v-if="orders.length" class="order-list">
			<OrderCard
				v-for="order in orders"
				:key="order.id"
				:order="order"
				:show-copy="true"
			>
				<template #foot>
					<!-- 优惠合计（促销 + 券） -->
					<view v-if="Number(order.discountAmount) > 0" class="pay-row">
						<text class="pay-label">优惠合计：</text>
						<text class="pay-discount">-¥{{ fmtMoney(order.discountAmount) }}</text>
					</view>
					<view v-if="Number(order.couponAmount) > 0" class="pay-row">
						<text class="pay-label">其中优惠券：</text>
						<text class="pay-discount">-¥{{ fmtMoney(order.couponAmount) }}</text>
					</view>

					<!-- 实付金额 -->
					<view class="pay-row">
						<text class="pay-label">实付款：</text>
						<text class="pay-amount">¥{{ fmtMoney(order.payAmount) }}</text>
					</view>

					<!-- 底部按钮：未超时给倒计时 + 取消 + 付款；超时关闭只留提示 -->
					<view class="btn-row">
						<template v-if="!isExpired(order)">
							<text class="time-tip">剩余 {{ countdownText(order) }}</text>
							<view class="btn cancel" @click="handleCancel(order)">取消订单</view>
							<view class="btn pay" @click="handlePay(order)">付款</view>
						</template>
						<template v-else>
							<text class="time-tip closed-tip">订单已超时关闭</text>
						</template>
					</view>
				</template>
			</OrderCard>
		</view>

		<!-- 空态：从订阅消息带 orderNo 进来时，订单可能已支付/已取消 -->
		<view v-else-if="loaded" class="empty">
			<text class="empty-text">{{ orderNo ? '该订单已支付或已取消' : '暂无待付款订单' }}</text>
		</view>
	</view>
</template>

<script>
	import {
		listOrders,
		payOrder,
		cancelOrder
	} from '../../api/order.js'
	import {
		ensureLogin
	} from '../../utils/auth.js'
	import {
		fmtMoney,
		parseOrderTime
	} from '../../utils/format.js'
	import OrderCard from '../../components/order-card/order-card.vue'

	// 待付款超时时长：与后端 OrderServiceImpl.PENDING_PAY_TIMEOUT_SECONDS（1800 秒 = 30 分钟）一致
	const PAY_TIMEOUT_MS = 30 * 60 * 1000

	export default {
		components: {
			OrderCard
		},
		data() {
			return {
				orders: [], // 待付款订单（status=1）
				loaded: false, // 是否完成首次加载
				now: Date.now(), // 当前时间戳，每秒刷新驱动所有倒计时
				operating: false, // 付款/取消处理中，防连点重复提交
				orderNo: '' // 订阅消息带过来的订单号，非空时只显示这一单
			}
		},
		onLoad(options) {
			// 订阅消息跳转：pages/order/pending-pay?orderNo=xxx
			this.orderNo = options.orderNo || ''
		},
		onShow() {
			this.loadOrders()
			this.startTimer()
		},
		onHide() {
			this.stopTimer()
		},
		onUnload() {
			this.stopTimer()
		},
		methods: {
			fmtMoney,
			// 拉订单，只留待付款（status=1），并预计算每单的超时时间戳
			loadOrders() {
				ensureLogin()
					.then(() => listOrders())
					.then((list) => {
						list = list || []
						this.orders = list
							.filter(o => Number(o.orderStatus) === 1)
							// 订阅消息带 orderNo 时只保留这一单，方便用户直达
							.filter(o => !this.orderNo || o.orderNo === this.orderNo)
							.map(o => {
								o._expireTime = parseOrderTime(o.createdAt) + PAY_TIMEOUT_MS
								return o
							})
					})
					.catch(() => {
						// 错误提示已在 request 封装里统一处理
					})
					.finally(() => {
						this.loaded = true
					})
			},
			// 倒计时：每秒把 now 更新成当前时间，模板据此重算剩余时间
			startTimer() {
				this.stopTimer()
				this.now = Date.now()
				this._timer = setInterval(() => {
					this.now = Date.now()
				}, 1000)
			},
			stopTimer() {
				if (this._timer) {
					clearInterval(this._timer)
					this._timer = null
				}
			},
			// 是否已超时（前端临时标记，后端定时任务每分钟扫一次才真正改状态）
			isExpired(order) {
				return order._expireTime <= this.now
			},
			// 剩余时间文案：MM : SS，分秒都补零到两位数
			countdownText(order) {
				const remain = order._expireTime - this.now
				if (remain <= 0) {
					return '0分0秒'
				}
				const m = Math.floor(remain / 60000)
				const s = Math.floor((remain % 60000) / 1000)
				return String(m).padStart(2, '0') + ' : ' + String(s).padStart(2, '0')
			},
			// 取消订单：直接调接口（不弹确认），成功后刷新
			handleCancel(order) {
				if (this.operating) return
				this.operating = true
				cancelOrder(order.id).then(() => {
					uni.showToast({
						title: '订单已取消',
						icon: 'success'
					})
					this.loadOrders()
				}).catch(() => {}).finally(() => {
					this.operating = false
				})
			},
			// 付款：调支付接口（暂未接微信收银台，后端记账），成功后从待付款列表消失
			handlePay(order) {
				if (this.operating) return
				this.operating = true
				payOrder(order.id).then(() => {
					uni.showToast({
						title: '支付成功',
						icon: 'success'
					})
					this.loadOrders()
				}).catch(() => {}).finally(() => {
					this.operating = false
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
		background: #f8f8f8;
		padding-bottom: env(safe-area-inset-bottom);
	}

	.order-list {
		padding: 0 20rpx;
	}

	/* ═══ 实付金额 ═══ */
	.pay-row {
		display: flex;
		align-items: baseline;
		justify-content: flex-end;
		padding-top: 16rpx;
		border-top: 1rpx solid #f0f0f0;
	}

	.pay-label {
		font-size: 26rpx;
		color: #666666;
	}

	.pay-amount {
		font-size: 34rpx;
		font-weight: bold;
		color: #FF4E2A;
	}

	/* 活动优惠：品牌橙 */
	.pay-discount {
		font-size: 28rpx;
		color: #FF6700;
	}

	/* ═══ 底部按钮区 ═══ */
	.btn-row {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		margin-top: 18rpx;
	}

	.time-tip {
		margin-right: auto;
		font-size: 26rpx;
		color: #f56c6c;
	}

	.time-tip.closed-tip {
		color: #999999;
	}

	.btn.cancel {
		padding: 14rpx 22rpx;
		margin-right: 20rpx;
		color: #333333;
		font-size: 22rpx;
	}

	.btn.pay {
		padding: 10rpx 52rpx;
		background: #FF6700;
		color: #ffffff;
		border-radius: 40rpx;
		font-size: 28rpx;
	}

	/* ═══ 空态 ═══ */
	.empty {
		display: flex;
		align-items: center;
		justify-content: center;
		padding-top: 200rpx;
	}

	.empty-text {
		font-size: 28rpx;
		color: #999999;
	}
</style>
