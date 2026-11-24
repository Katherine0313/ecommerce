<template>
<view class="page">
		<CustomNavbar title="换货详情" :show-back="true" />

		<!-- 模块1：状态头部 + 进度时间轴（换货不退款，无金额） -->
		<view class="status-card">
			<view class="status-header">
				<text class="status-title">{{ detail && detail.statusDesc }}</text>
				<view class="arrow"></view>
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

		<!-- 模块2：退货方式（自行寄回点击打开回填物流弹窗；上门取件无需回填） -->
		<view class="row-card" @click="handleReturnWayClick">
			<text class="row-label">{{ returnWayText }}</text>
			<view class="arrow"></view>
		</view>

		<!-- 模块3：退货物流（自行寄回回填后展示） -->
		<view class="row-card" v-if="detail && detail.returnTrackingNumber">
			<text class="row-label">{{ returnTrackingText }}</text>
			<view class="arrow"></view>
		</view>

		<!-- 模块4：商品&售后信息 -->
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
				<text class="info-label">换货金额</text>
				<text class="info-value">¥{{ fmtMoney(refundTotal) }}</text>
			</view>
			<view class="info-line" v-if="detail && detail.shipTrackingNumber">
				<text class="info-label">补发物流</text>
				<text class="info-value">{{ detail.shipCourier || '—' }} {{ detail.shipTrackingNumber }}</text>
			</view>
			<view class="info-line">
				<text class="info-label">换货原因</text>
				<text class="info-value">{{ detail && detail.reasonDesc }}</text>
			</view>
		</view>

		<!-- 底部：自行寄回未回填物流 / 上门取件待确认 -->
		<view class="footer-bar" v-if="needPickup || needTracking">
			<view v-if="needPickup" class="btn-primary" @click="confirmPickup">已上门取件</view>
			<view v-else class="btn-primary" @click="openTracking">回填物流单号</view>
		</view>

		<!-- 回填物流弹窗 -->
		<uni-popup ref="trackingPopup" type="bottom">
			<view class="popup-body">
				<view class="popup-header">
					<text class="popup-title">回填物流单号</text>
					<text class="popup-close" @click="closeTracking">×</text>
				</view>
				<picker mode="selector" :range="courierNames" @change="onCourierChange">
					<view class="popup-item">
						<text class="popup-label">快递公司</text>
						<text :class="{ placeholder: !courier }">{{ courierName }}</text>
						<view class="choose">
						<text>请选择</text>
						<view class="arrow"></view>
						</view>
					</view>
				</picker>
				<view class="popup-item">
					<text class="popup-label">快递单号</text>
					<input class="popup-input" v-model="trackingNumber" placeholder="请输入快递单号" />
				</view>
				<view class="confirm-btn" @click="submitTracking">确定</view>
			</view>
		</uni-popup>
	</view>
</template>

<script>
	import {
		getAfterSale,
		getAfterSaleLogs,
		submitAfterSaleReturn,
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
				courier: '',
				trackingNumber: '',
				courierOptions: [
					{ code: 'shunfeng', name: '顺丰速运' },
					{ code: 'yuantong', name: '圆通速递' },
					{ code: 'zhongtong', name: '中通快递' },
					{ code: 'yunda', name: '韵达快递' },
					{ code: 'shentong', name: '申通快递' },
					{ code: 'jitu', name: '极兔速递' },
					{ code: 'debang', name: '德邦快递' },
					{ code: 'youzhengguonei', name: '邮政国内' }
				],
				submitting: false
			}
		},
		computed: {
			// 时间轴：提交申请 → 商家审核 → 用户退货 → 商家换货 → 换货完成
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
					{ title: '用户退货', time: d.returnAt, done: !!d.returnAt },
					{ title: '商家换货', time: d.exchangeAt, done: !!d.exchangeAt },
					{ title: '换货完成', time: d.exchangedAt, done: !!d.exchangedAt }
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
			needTracking() {
				const d = this.detail
				return d && d.status === 'returning' && d.returnWay === 'self' && !d.returnTrackingNumber
			},
			// 上门取件：退货中且尚未确认取件
			needPickup() {
				const d = this.detail
				return d && d.status === 'returning' && d.returnWay === 'pickup' && !d.pickupAt
			},
			courierNames() {
				return this.courierOptions.map(c => c.name)
			},
			courierName() {
				const c = this.courierOptions.find(c => c.code === this.courier)
				return c ? c.name : ''
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
			// 点退货方式行：上门取件由商家安排，不弹回填物流；其余情况打开弹窗
			handleReturnWayClick() {
				if (this.detail && this.detail.returnWay === 'pickup') {
					uni.showToast({
						title: '上门取件无需回填物流单号',
						icon: 'none'
					})
					return
				}
				this.openTracking()
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
			},
			openTracking() {
				this.courier = ''
				this.trackingNumber = ''
				this.$refs.trackingPopup.open()
			},
			closeTracking() {
				this.$refs.trackingPopup.close()
			},
			onCourierChange(e) {
				const idx = Number(e.detail.value)
				this.courier = this.courierOptions[idx].code
			},
			submitTracking() {
				if (!this.courier) {
					uni.showToast({ title: '请选择快递公司', icon: 'none' })
					return
				}
				if (!this.trackingNumber || !this.trackingNumber.trim()) {
					uni.showToast({ title: '请输入快递单号', icon: 'none' })
					return
				}
				submitAfterSaleReturn(this.id, {
					returnWay: this.detail.returnWay,
					returnTimeSlot: this.detail.returnTimeSlot,
					courier: this.courier,
					trackingNumber: this.trackingNumber.trim()
				}).then(() => {
					uni.showToast({ title: '回填成功', icon: 'success' })
					this.closeTracking()
					this.loadDetail()
				}).catch(() => {})
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
		font-size: 22rpx;
		color: #333333;
	}

	.tl-time {
		margin-top: 4rpx;
		font-size: 20rpx;
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

	.popup-body {
		background: #ffffff;
		border-radius: 24rpx 24rpx 0 0;
		padding: 24rpx;
		padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
	}

	.popup-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-bottom: 20rpx;
	}

	.popup-title {
		font-size: 32rpx;
		font-weight: bold;
		color: #333333;
	}

	.popup-close {
		font-size: 40rpx;
		color: #999999;
		padding: 0 12rpx;
	}

	.popup-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 100rpx;
		border-bottom: 1rpx solid #f5f5f5;
	}
	.popup-item .choose{
		display:flex;
		gap:20rpx;
		align-items:center;
	}

	.popup-label {
		font-size: 28rpx;
		color: #333333;
	}

	.popup-item .placeholder {
		color: #999999;
	}

	.popup-input {
		flex: 1;
		margin-left: 40rpx;
		text-align: right;
		font-size: 28rpx;
	}

	.confirm-btn {
		margin-top: 24rpx;
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		background: #FF6700;
		color: #ffffff;
		border-radius: 44rpx;
		font-size: 30rpx;
	}
</style>
