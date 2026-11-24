<template>
<view class="page">
		<!-- 自定义导航栏：二级页，带返回按钮 -->
		<CustomNavbar title="物流信息" :show-back="true" />

		<!-- 快递信息卡片：快递公司 + 单号 + 复制 -->
		<view class="card" v-if="logistics">
			<view class="express-row">
				<view class="express-left">
					<view class="express-logo">📮</view>
					<view class="express-text">{{ logistics.courier }} {{ logistics.trackingNumber }}</view>
				</view>
				<view class="copy-btn" @click="copyTracking">复制</view>
			</view>
		</view>

		<!-- 物流轨迹时间线：最新在前，第一条高亮 -->
		<view class="card timeline-card" v-if="logistics">
			<view class="timeline-item" v-for="(trace, idx) in traces" :key="idx">
				<view class="timeline-left">
					<view class="dot" :class="{ active: idx === 0 }"></view>
					<view class="line" v-if="idx !== traces.length - 1"></view>
				</view>
				<view class="timeline-right">
					<text class="trace-status" :class="{ active: idx === 0 }">{{ trace.status }}</text>
					<text class="trace-desc" :class="{ active: idx === 0 }">{{ trace.desc }}</text>
					<text class="trace-time">{{ trace.time }}</text>
				</view>
			</view>
		</view>

		<!-- 空态：无物流信息（未发货） -->
		<view v-else-if="loaded" class="empty">
			<text class="empty-text">暂无物流信息</text>
		</view>
	</view>
</template>

<script>
	import {
		getLogistics
	} from '../../api/order.js'

	export default {
		data() {
			return {
				orderId: null, // 从路由带进来的订单 id
				logistics: null, // 物流轨迹数据
				loaded: false // 是否完成首次加载
			}
		},
		computed: {
			// 轨迹倒序：后端按时间升序返回，这里最新在前展示
			traces() {
				const list = this.logistics && this.logistics.traces ? this.logistics.traces : []
				return list.slice().reverse()
			}
		},
		onLoad(options) {
			this.orderId = options.id
		},
		onShow() {
			this.load()
		},
		methods: {
			// 拉物流轨迹
			load() {
				getLogistics(this.orderId).then((logistics) => {
					this.logistics = logistics
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).finally(() => {
					this.loaded = true
				})
			},
			// 复制「快递公司 + 单号」
			copyTracking() {
				const text = (this.logistics.courier || '') + ' ' + (this.logistics.trackingNumber || '')
				uni.setClipboardData({
					data: text.trim(),
					success: () => uni.showToast({
						title: '已复制',
						icon: 'none'
					})
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

	.card {
		background: #ffffff;
		margin: 20rpx 20rpx 0;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	/* ═══ 快递信息卡片 ═══ */
	.express-row {
		display: flex;
		align-items: center;
	}

	.express-left {
		flex: 1;
		display: flex;
		align-items: center;
		min-width: 0;
	}

	.express-logo {
		font-size: 40rpx;
		margin-right: 16rpx;
	}

	.express-text {
		font-size: 28rpx;
		color: #333333;
	}

	.copy-btn {
		flex-shrink: 0;
		padding: 4rpx 20rpx;
		border: 1rpx solid #dddddd;
		border-radius: 24rpx;
		font-size: 24rpx;
		color: #666666;
	}

	/* ═══ 物流轨迹时间线 ═══ */
	.timeline-card {
		padding: 30rpx 24rpx;
	}

	.timeline-item {
		display: flex;
	}

	.timeline-left {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 40rpx;
		flex-shrink: 0;
	}

	.dot {
		width: 14rpx;
		height: 14rpx;
		border-radius: 50%;
		background: #cccccc;
		margin-top: 8rpx;
	}

	.dot.active {
		background: #FF6700;
	}

	.line {
		flex: 1;
		width: 2rpx;
		background: #e5e5e5;
		margin: 6rpx 0;
	}

	.timeline-right {
		flex: 1;
		min-width: 0;
		padding: 0 0 40rpx 20rpx;
	}

	.trace-status {
		display: block;
		font-size: 26rpx;
		color: #999999;
		line-height: 1.4;
	}

	.trace-status.active {
		color: #FF6700;
		font-weight: bold;
	}

	.trace-desc {
		display: block;
		margin-top: 4rpx;
		font-size: 28rpx;
		color: #999999;
		line-height: 1.5;
	}

	.trace-desc.active {
		color: #333333;
		font-weight: bold;
	}

	.trace-time {
		display: block;
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #bbbbbb;
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
