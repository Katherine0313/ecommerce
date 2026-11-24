<template>
	<view class="seckill-card" @click="onGrab">
		<view class="card-top">
			<view class="value">
				<text v-if="coupon.type === 'DISCOUNT'" class="v-num">{{ (Number(coupon.discountRate) * 10).toFixed(1) }}</text>
				<text v-else class="v-num">¥{{ fmtInt(coupon.faceValue) }}</text>
				<text class="v-unit">{{ coupon.type === 'DISCOUNT' ? '折券' : '代金券' }}</text>
			</view>
			<text class="tag">秒杀</text>
		</view>
		<text class="threshold">{{ thresholdText }}</text>
		<view class="bottom">
			<view class="price-col">
				<text class="price">¥{{ fmtInt(coupon.seckillPrice) }}</text>
				<text class="origin">¥{{ fmtInt(coupon.faceValue) }}</text>
			</view>
			<view class="grab-btn">马上抢</view>
		</view>
	</view>
</template>

<script>
	export default {
		name: 'SeckillCard',
		props: {
			coupon: {
				type: Object,
				default: () => ({})
			}
		},
		computed: {
			thresholdText() {
				const t = Number(this.coupon.thresholdAmount) || 0
				return t > 0 ? '满¥' + this.fmtInt(t) + '可用' : '无门槛'
			}
		},
		methods: {
			fmtInt(v) {
				const n = Number(v)
				return Number.isFinite(n) ? n.toFixed(2).replace(/\.00$/, '') : '0'
			},
			onGrab() {
				this.$emit('grab', this.coupon)
			}
		}
	}
</script>

<style scoped>
	.seckill-card {
		position: relative;
		flex: 0 0 230rpx;
		width: 230rpx;
		margin-right: 16rpx;
		padding: 18rpx 16rpx;
		background: #ffffff;
		border-radius: 16rpx;
		box-sizing: border-box;
	}

	.card-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.value {
		display: flex;
		align-items: baseline;
		color: #FF6700;
	}

	.v-num {
		font-size: 40rpx;
		font-weight: bold;
	}

	.v-unit {
		margin-left: 4rpx;
		font-size: 20rpx;
	}

	.tag {
		position: absolute;
		top: 10rpx;
		right: 10rpx;
		z-index: 1;
		padding: 2rpx 10rpx;
		font-size: 18rpx;
		line-height: 24rpx;
		color: #ffffff;
		background: linear-gradient(90deg, #FF8A00, #FF4D00);
		border-radius: 16rpx;
	}

	.threshold {
		display: block;
		margin-top: 4rpx;
		font-size: 20rpx;
		color: #999999;
	}

	.bottom {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 12rpx;
	}

	.price-col {
		display: flex;
		flex-direction: column;
	}

	.price {
		font-size: 34rpx;
		font-weight: bold;
		color: #E4393C;
		line-height: 1.1;
	}

	.origin {
		margin-top: 2rpx;
		font-size: 20rpx;
		color: #bbbbbb;
		text-decoration: line-through;
	}

	.grab-btn {
		padding: 8rpx 18rpx;
		font-size: 22rpx;
		color: #ffffff;
		background: linear-gradient(90deg, #FF8A00, #FF4D00);
		border-radius: 24rpx;
	}
</style>
