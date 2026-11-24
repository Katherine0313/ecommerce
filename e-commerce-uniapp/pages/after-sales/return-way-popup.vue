<template>
	<!-- 退货方式弹窗：底部弹出，官方退货（上门取件+时段）或自行寄回，确认后 emit confirm { returnWay, returnTimeSlot } -->
	<uni-popup ref="popup" type="bottom" @change="handleChange">
		<view class="popup-body">
			<!-- 模块1：官方退货（上门取件）卡片 -->
			<view class="pickup-card">
				<view class="pickup-header">
					<text class="pickup-header-icon">📦</text>
					<text class="pickup-header-title">杰米多多官方退货</text>
					<text class="pickup-header-sub">丢损必赔·免填地址</text>
				</view>
				<view class="pickup-body">
					<view class="pickup-option" @click="returnWay = 'pickup'">
						<view class="pickup-title-row">
							<text class="pickup-title">上门取件</text>
							<text class="tag">商家指定服务</text>
							<text class="tag">官方保障</text>
							<view class="check" :class="{ checked: returnWay === 'pickup' }"></view>
						</view>
						<text class="pickup-desc">商家同意退货后，将为您上门取件，具体日期将以短信形式通知，您可随时修改或取消</text>
					</view>
					<view v-if="returnWay === 'pickup'" class="slot-row">
						<view class="slot" :class="{ active: returnTimeSlot === 'morning' }"
							@click="returnTimeSlot = 'morning'">
							<text>商家同意退货后</text>
							<text>09:00 – 13:00</text>
						</view>
						<view class="slot" :class="{ active: returnTimeSlot === 'afternoon' }"
							@click="returnTimeSlot = 'afternoon'">
							<text>商家同意退货后</text>
							<text>13:00 – 19:00</text>
						</view>
					</view>
				</view>
			</view>

			<!-- 模块2：自行寄回 -->
			<view class="self-card" @click="returnWay = 'self'">
				<view class="self-title-row">
					<text class="self-title">自行寄回</text>
					<view class="check" :class="{ checked: returnWay === 'self' }"></view>
				</view>
				<text class="self-desc">放弃丢损必赔保障服务，运费自行支付/垫付</text>
			</view>

			<!-- 底部确定 -->
			<view class="confirm-btn" @click="confirm">确定</view>
		</view>
	</uni-popup>
</template>

<script>
	export default {
		name: 'ReturnWayPopup',
		data() {
			return {
				returnWay: '',
				returnTimeSlot: ''
			}
		},
		methods: {
			// 父组件通过 ref 调 open 打开
			open() {
				// 默认选中上门取件（官方退货推荐项），时间段直接展示
				this.returnWay = 'pickup'
				this.returnTimeSlot = ''
				this.$refs.popup.open()
			},
			close() {
				this.$refs.popup.close()
			},
			// 点遮罩关闭：uni-popup 自己关掉后只发 change，这里通知页面
			handleChange(e) {
				if (!e.show) {
					this.$emit('close')
				}
			},
			confirm() {
				if (!this.returnWay) {
					uni.showToast({
						title: '请选择退货方式',
						icon: 'none'
					})
					return
				}
				if (this.returnWay === 'pickup' && !this.returnTimeSlot) {
					uni.showToast({
						title: '请选择取件时段',
						icon: 'none'
					})
					return
				}
				this.$emit('confirm', {
					returnWay: this.returnWay,
					returnTimeSlot: this.returnTimeSlot
				})
				this.close()
			}
		}
	}
</script>

<style scoped>
	.popup-body {
		background: #ffffff;
		border-radius: 24rpx 24rpx 0 0;
		padding: 24rpx;
		padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
	}

	/* 选中圆圈 */
	.check {
		width: 36rpx;
		height: 36rpx;
		border: 2rpx solid #cccccc;
		border-radius: 50%;
		box-sizing: border-box;
		background: #ffffff;
		flex-shrink: 0;
	}

	.check.checked {
		border-color: #FF6700;
		background: #FF6700;
	}

	.check.checked::after {
		content: '✓';
		display: block;
		color: #ffffff;
		font-size: 22rpx;
		text-align: center;
		line-height: 32rpx;
	}

	/* 模块1：官方退货卡片 */
	.pickup-card {
		border: 2rpx solid #FF6700;
		border-radius: 16rpx;
		overflow: hidden;
	}

	.pickup-header {
		display: flex;
		align-items: center;
		background: #FF6700;
		padding: 20rpx 24rpx;
	}

	.pickup-header-icon {
		font-size: 30rpx;
		margin-right: 12rpx;
	}

	.pickup-header-title {
		font-size: 28rpx;
		font-weight: bold;
		color: #ffffff;
	}

	.pickup-header-sub {
		margin-left: auto;
		font-size: 22rpx;
		color: #ffffff;
		opacity: 0.9;
	}

	.pickup-body {
		padding: 24rpx;
	}

	.pickup-title-row {
		display: flex;
		align-items: center;
	}

	.pickup-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
		margin-right: 12rpx;
	}

	.tag {
		padding: 2rpx 12rpx;
		font-size: 20rpx;
		color: #FF6700;
		background: #fff5f2;
		border: 1rpx solid #ffd0c0;
		border-radius: 6rpx;
		margin-right: 8rpx;
	}

	.check {
		margin-left: auto;
	}

	.pickup-desc {
		display: block;
		margin-top: 16rpx;
		font-size: 24rpx;
		color: #666666;
		line-height: 1.6;
	}

	.slot-row {
		display: flex;
		gap: 20rpx;
		margin-top: 20rpx;
	}

	.slot {
		display:flex;
		flex-direction: column;
		flex: 1;
		padding: 20rpx 16rpx;
		text-align: center;
		font-size: 24rpx;
		color: #666666;
		border: 2rpx solid #dddddd;
		border-radius: 12rpx;
	}

	.slot.active {
		border-color: #FF6700;
		color: #FF6700;
		background: #fff5f2;
	}

	/* 模块2：自行寄回 */
	.self-card {
		margin-top: 24rpx;
		padding: 24rpx;
		border: 2rpx solid #eeeeee;
		border-radius: 16rpx;
	}

	.self-title-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.self-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}

	.self-desc {
		display: block;
		margin-top: 12rpx;
		font-size: 24rpx;
		color: #666666;
		line-height: 1.6;
	}

	/* 底部确定 */
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
