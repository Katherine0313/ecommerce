<template>
<view class="page">
		<CustomNavbar title="退货退款详情" :show-back="true" />

		<!-- 模块1：状态头部 + 进度时间轴 -->
		<view class="status-card">
			<view class="status-header">
				<text class="status-title">{{ detail && detail.statusDesc }}</text>
				<view class="arrow"></view>
			</view>
			<view class="status-amount-row" v-if="detail && detail.status === 'refunded'">
				<text class="status-amount">¥{{ fmtMoney(detail.refundAmount || refundTotal) }}</text>
				<text class="status-bank">{{ detail.refundNo ? '退款单号 ' + detail.refundNo : '已原路退回（模拟）' }}</text>
			</view>
			<!-- 进度时间轴（横向，按状态动态） -->
			<view class="timeline">
				<view class="tl-item" v-for="(node, idx) in timeline" :key="idx" :class="{ lineDone: idx < timeline.length - 1 && timeline[idx + 1].done }">
					<view class="tl-dot" :class="{ done: node.done, check: node.done && idx === timeline.length - 1 && !node.reject, reject: node.reject }"></view>
					<text class="tl-title">{{ node.title }}</text>
					<text class="tl-time">{{ fmtTime(node.time) }}</text>
				</view>
			</view>
		</view>

		<!-- 处理进度（售后变更记录） -->
		<after-sale-log-list :logs="logs" />

		<!-- 模块2：退货方式（自行寄回未回填时可点击去回填） -->
		<view class="row-card" @click="handleReturnWayClick">
			<text class="row-label">{{ returnWayText }}</text>
			<view class="arrow"></view>
		</view>

		<!-- 模块3：退货物流（回填后展示；未回填时点击去回填） -->
		<view class="row-card" v-if="detail && detail.returnTrackingNumber">
			<text class="row-label">{{ returnTrackingText }}</text>
			<view class="arrow"></view>
		</view>
		<view class="row-card" v-else-if="needTracking" @click="goTracking">
			<text class="row-label highlight">回填物流单号</text>
			<view class="arrow"></view>
		</view>

		<!-- 模块4：退款遇到问题 -->
		<view class="block-card">
			<view class="block-header">
				<text class="block-title">退款遇到问题</text>
				<view class="block-more">
					<text class="more-text">查看全部</text>
					<view class="arrow"></view>
				</view>
			</view>
			<text class="qa-line">问：退款什么时候到账，退到哪里？</text>
			<text class="qa-line">问：如何查询退款到账情况？</text>
		</view>

		<!-- 模块5：商品&售后信息 -->
		<view class="info-card">
			<view class="merchant-row">
				<text class="merchant-name">杰米多多</text>
				<text class="contact-btn" @click="contactMerchant">联系商家</text>
			</view>
			<view class="goods-row" v-for="item in items" :key="item.productId">
				<image lazy-load class="goods-img" :src="goodsImgUrl(item.image, item.productId)" mode="aspectFill"></image>
				<view class="goods-info">
					<text class="goods-name">{{ item.productName }}</text>
					<text class="goods-spec">×{{ item.quantity }}</text>
				</view>
			</view>
			<view class="info-line">
				<text class="info-label">订单编号</text>
				<view class="info-value-row">
					<text class="info-value">{{ detail && detail.orderNo }}</text>
					<text class="copy-btn" @click="copyOrderNo">复制</text>
				</view>
			</view>
			<view class="info-line">
				<text class="info-label">申请金额</text>
				<text class="info-value">¥{{ fmtMoney(refundTotal) }}</text>
			</view>
			<view class="info-line">
				<text class="info-label">退款原因</text>
				<text class="info-value">{{ detail && detail.reasonDesc }}</text>
			</view>
		</view>

		<!-- 底部：上门取件待确认 / 自行寄回待回填物流 -->
		<view class="footer-bar" v-if="needPickup || needTracking">
			<view v-if="needPickup" class="btn-primary" @click="confirmPickup">已上门取件</view>
			<view v-else class="btn-primary" @click="goTracking">回填物流单号</view>
		</view>
	</view>
</template>

<script>
	import {
		getAfterSale,
		getAfterSaleLogs,
		pickupAfterSale
	} from '../../api/after-sale.js'
	import {
		goodsImgUrl,
		fmtMoney,
		formatOrderTime
	} from '../../utils/format.js'
	import afterSaleLogList from '../../components/after-sale-log-list/after-sale-log-list.vue'

	export default {
		components: {
			afterSaleLogList
		},
		data() {
			return {
				id: null,
				detail: null,
				items: [],
				refundTotal: 0,
				logs: [],
				submitting: false
			}
		},
		computed: {
			// 时间轴：提交申请 → 商家审核 → 用户退货 → 退款到账
			timeline() {
				const d = this.detail
				if (!d) return []
				if (d.status === 'rejected') {
					return [
						{ title: '提交申请', time: d.createdAt, done: true },
						{ title: '审核未通过', time: d.rejectedAt, done: true, reject: true }
					]
				}
				return [
					{ title: '提交申请', time: d.createdAt, done: true },
					{ title: '商家审核', time: d.reviewAt, done: !!d.reviewAt },
					{ title: '用户退货', time: d.pickupAt || d.returnAt, done: !!(d.pickupAt || d.returnAt) },
					{ title: '退款到账', time: d.refundedAt, done: !!d.refundedAt }
				]
			},
			returnWayText() {
				const d = this.detail
				if (!d || !d.returnWay) return '退货方式：—'
				if (d.returnWay === 'pickup') {
					const slot = d.returnTimeSlot === 'morning' ? '09:00 – 13:00' : (d.returnTimeSlot === 'afternoon' ? '13:00 – 19:00' : '')
					return '退货方式：上门取件' + (slot ? ' · ' + slot : '')
				}
				return '退货方式：自行寄回'
			},
			returnTrackingText() {
				const d = this.detail
				const courier = d.returnCourierDesc || '—'
				const tracking = d.returnTrackingNumber || '—'
				return '退货物流：' + courier + ' ' + tracking
			},
			// 是否需要回填物流（自行寄回 + 退货中 + 未回填）
			needTracking() {
				const d = this.detail
				return d && d.status === 'returning' && d.returnWay === 'self' && !d.returnTrackingNumber
			},
			// 是否等待买家确认上门取件（上门取件 + 退货中 + 未确认）
			needPickup() {
				const d = this.detail
				return d && d.status === 'returning' && d.returnWay === 'pickup' && !d.pickupAt
			}
		},
		onLoad(options) {
			this.id = options.id
			if (this.id) {
				this.loadDetail()
			}
		},
		methods: {
			goodsImgUrl,
			fmtMoney,
			loadDetail() {
				getAfterSale(this.id).then((detail) => {
					this.detail = detail
					this.items = detail.items || []
					this.refundTotal = this.items.reduce((sum, i) => sum + (Number(i.refundAmount) || 0), 0)
				}).catch(() => {})
				// 变更记录单独拉，失败不影响详情展示
				getAfterSaleLogs(this.id).then((logs) => {
					this.logs = logs || []
				}).catch(() => {})
			},
			fmtTime(t) {
				const s = formatOrderTime(t)
				return s ? s.slice(5) : ''
			},
			copyOrderNo() {
				if (!this.detail || !this.detail.orderNo) return
				uni.setClipboardData({
					data: this.detail.orderNo,
					success: () => uni.showToast({ title: '已复制', icon: 'none' })
				})
			},
			contactMerchant() {
				uni.showToast({ title: '联系商家待接入', icon: 'none' })
			},
			// 跳回填物流单号页
			goTracking() {
				uni.navigateTo({ url: '/pages/after-sales/return-tracking?id=' + this.id })
			},
			// 点退货方式行：自行寄回未回填则去回填
			handleReturnWayClick() {
				if (this.needTracking) {
					this.goTracking()
				}
			},
			// 确认上门取件：点一下即代表快递员已取走包裹
			confirmPickup() {
				if (this.submitting) return
				this.submitting = true
				uni.showLoading({ title: '确认中...' })
				pickupAfterSale(this.id).then(() => {
					uni.hideLoading()
					this.submitting = false
					uni.showToast({ title: '已确认上门取件', icon: 'none' })
					this.loadDetail()
				}).catch(() => {
					uni.hideLoading()
					this.submitting = false
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
		padding-bottom: 160rpx;
	}

	.arrow {
		width: 16rpx;
		height: 16rpx;
		border-top: 2rpx solid #cccccc;
		border-right: 2rpx solid #cccccc;
		transform: rotate(45deg);
		flex-shrink: 0;
	}

	.status-card {
		background: #ffffff;
		margin: 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.status-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.status-title {
		font-size: 32rpx;
		font-weight: bold;
		color: #333333;
	}

	.status-amount-row {
		display: flex;
		align-items: baseline;
		margin-top: 16rpx;
	}

	.status-amount {
		font-size: 56rpx;
		font-weight: bold;
		color: #333333;
	}

	.status-bank {
		margin-left: 16rpx;
		font-size: 24rpx;
		color: #00b42a;
	}

	.timeline {
		display: flex;
		margin-top: 28rpx;
	}

	.tl-item {
		flex: 1;
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
	}

	.tl-item:not(:last-child)::after {
		content: '';
		position: absolute;
		top: 10rpx;
		left: 50%;
		width: 100%;
		height: 2rpx;
		background: #e5e5e5;
	}

	.tl-item.lineDone:not(:last-child)::after {
		background: #00b42a;
	}

	.tl-dot {
		width: 20rpx;
		height: 20rpx;
		border-radius: 50%;
		background: #e5e5e5;
		position: relative;
		z-index: 1;
	}

	.tl-dot.done {
		background: #00b42a;
	}

	.tl-dot.check::after {
		content: '✓';
		display: block;
		color: #ffffff;
		font-size: 14rpx;
		text-align: center;
		line-height: 20rpx;
	}

	/* 拒绝态：红色圆点 + 叉 */
	.tl-dot.reject {
		background: #ff4d4f;
	}

	.tl-dot.reject::after {
		content: '×';
		display: block;
		color: #ffffff;
		font-size: 14rpx;
		text-align: center;
		line-height: 20rpx;
	}

	.tl-title {
		margin-top: 12rpx;
		font-size: 24rpx;
		color: #333333;
	}

	.tl-time {
		margin-top: 4rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.row-card {
		display: flex;
		align-items: center;
		justify-content: space-between;
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 28rpx 24rpx;
	}

	.row-label {
		font-size: 28rpx;
		color: #333333;
	}

	.row-label.highlight {
		color: #FF6700;
	}

	.block-card {
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.block-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 16rpx;
	}

	.block-title {
		font-size: 28rpx;
		font-weight: bold;
		color: #333333;
	}

	.block-more {
		display: flex;
		align-items: center;
	}

	.more-text {
		font-size: 24rpx;
		color: #666666;
		margin-right: 8rpx;
	}

	.qa-line {
		display: block;
		font-size: 24rpx;
		color: #666666;
		line-height: 1.8;
	}

	.info-card {
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.merchant-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-bottom: 20rpx;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.merchant-name {
		font-size: 28rpx;
		font-weight: bold;
		color: #333333;
	}

	.contact-btn {
		padding: 6rpx 24rpx;
		font-size: 24rpx;
		color: #FF6700;
		border: 1rpx solid #FF6700;
		border-radius: 28rpx;
	}

	.goods-row {
		display: flex;
		align-items: center;
		padding: 20rpx 0;
		border-bottom: 1rpx solid #f0f0f0;
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
		display: flex;
		flex-direction: column;
	}

	.goods-name {
		font-size: 26rpx;
		color: #333333;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.goods-spec {
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #999999;
	}

	.info-line {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 20rpx 0;
	}

	.info-label {
		font-size: 26rpx;
		color: #666666;
	}

	.info-value-row {
		display: flex;
		align-items: center;
	}

	.info-value {
		font-size: 26rpx;
		color: #333333;
	}

	.copy-btn {
		margin-left: 16rpx;
		padding: 2rpx 16rpx;
		font-size: 22rpx;
		color: #FF6700;
		border: 1rpx solid #FF6700;
		border-radius: 20rpx;
	}

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

	.btn-primary {
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		background: #FF6700;
		color: #ffffff;
		border-radius: 44rpx;
		font-size: 30rpx;
	}
</style>
