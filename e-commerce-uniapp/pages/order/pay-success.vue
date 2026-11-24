<template>
<view class="page">
		<CustomNavbar  />
		<view class="success-card">
			<view class="header-row">
				<view class="success-icon">✓</view>
				<text class="success-text">支付成功</text>
			</view>
			<!-- 商家名称：杰米多多 -->
			<view class="merchant-name">杰米多多</view>
			<view class="amount">¥{{ amount }}</view>
		</view>
		<view class="btn-group">
			<button class="btn btn-primary" @click="goHome">返回商家</button>
		</view>
	</view>
</template>
<script>
	export default {
		data() {
			return {
				amount: '0.00'
			}
		},
		onLoad(options) {
			this.amount = (Number(options.amount) || 0).toFixed(2)
		},
		methods: {
			goHome() {
				uni.navigateBack()
			}
		}
	}
</script>
<style scoped>
	.page {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
		background: #ffffff;
	}
	.success-card {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin: 80rpx 20rpx 0;
		padding: 0 24rpx;
		background: transparent;
		border-radius: 0;
	}
	.header-row {
		display: flex;
		flex-direction: row;
		align-items: center;
	}
	.success-icon {
		width: 44rpx;
		height: 44rpx;
		border-radius: 50%;
		background: #07C160;
		color: #ffffff;
		font-size: 28rpx;
		line-height: 44rpx;
		text-align: center;
	}
	.success-text {
		margin-left: 12rpx;
		font-size: 32rpx;
		color: #07C160;
		font-weight: 500;
	}
	.merchant-name {
		margin-top: 60rpx;
		font-size: 36rpx;
		color: #333333;
	}
	.amount {
		margin-top: 24rpx;
		font-size: 66rpx;
		font-weight: 400;
		color: #111111;
	}
	.btn-group {
		position: fixed;
		bottom: calc(50rpx + env(safe-area-inset-bottom));
		left: 40rpx;
		right:40rpx;
		display: flex;
	}
	.btn {
		width: 200rpx;
		height: 64rpx;
		line-height: 64rpx;
		border-radius: 12rpx;
		font-size: 32rpx;
		text-align: center;
	}
	.btn::after {
		border: none;
	}
	.btn-primary {
		color: #333;
		background: #f5f5f5;
		border: none;
	}
</style>
