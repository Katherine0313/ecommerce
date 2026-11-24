<template>
	<view class="log-card" v-if="logs && logs.length">
		<text class="log-card-title">处理进度</text>
		<view class="log-item" v-for="(log, idx) in logs" :key="log.id || idx">
			<view class="log-dot-col">
				<view class="log-dot" :class="{ reject: log.afterStatus === 'rejected' }"></view>
				<view class="log-line" v-if="idx < logs.length - 1"></view>
			</view>
			<view class="log-body">
				<view class="log-head">
					<text class="log-title">{{ log.operateTypeDesc }}</text>
					<text class="log-time">{{ fmtTime(log.createdAt) }}</text>
				</view>
				<text class="log-content">{{ log.content }}</text>
				<text class="log-actor" v-if="log.userTypeDesc">{{ log.userTypeDesc }}</text>
			</view>
		</view>
	</view>
</template>

<script>
	import {
		formatOrderTime
	} from '../../utils/format.js'

	// 售后变更记录（纵向时间线）。买家端不展示商家内部备注，后端接口已过滤。
	export default {
		name: 'after-sale-log-list',
		props: {
			logs: {
				type: Array,
				default: () => []
			}
		},
		methods: {
			// 后端 LocalDateTime 序列化串 → "MM-dd HH:mm"
			fmtTime(t) {
				const s = formatOrderTime(t)
				return s ? s.slice(5) : ''
			}
		}
	}
</script>

<style scoped>
	.log-card {
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.log-card-title {
		display: block;
		font-size: 28rpx;
		font-weight: bold;
		color: #333333;
		margin-bottom: 8rpx;
	}

	.log-item {
		display: flex;
		flex-direction: row;
	}

	.log-dot-col {
		width: 40rpx;
		display: flex;
		flex-direction: column;
		align-items: center;
		flex-shrink: 0;
	}

	.log-dot {
		width: 16rpx;
		height: 16rpx;
		border-radius: 50%;
		background: #00b42a;
		margin-top: 10rpx;
	}

	.log-dot.reject {
		background: #ff4d4f;
	}

	.log-line {
		flex: 1;
		width: 2rpx;
		background: #e5e5e5;
		margin: 6rpx 0;
	}

	.log-body {
		flex: 1;
		min-width: 0;
		padding-bottom: 24rpx;
	}

	.log-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
	}

	.log-title {
		font-size: 26rpx;
		color: #333333;
	}

	.log-time {
		font-size: 22rpx;
		color: #999999;
		margin-left: 16rpx;
	}

	.log-content {
		display: block;
		margin-top: 6rpx;
		font-size: 24rpx;
		color: #666666;
		line-height: 1.6;
	}

	.log-actor {
		display: inline-block;
		margin-top: 6rpx;
		padding: 2rpx 12rpx;
		font-size: 20rpx;
		color: #999999;
		border: 1rpx solid #e5e5e5;
		border-radius: 18rpx;
	}
</style>
