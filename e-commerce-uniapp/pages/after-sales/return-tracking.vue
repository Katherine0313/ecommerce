<template>
<view class="page">
		<CustomNavbar title="回填物流单号" :show-back="true" />

		<!-- 快递公司 + 单号 -->
		<view class="info-card">
			<picker mode="selector" :range="courierNames" @change="onCourierChange">
				<view class="form-item">
					<text class="form-label"><text class="star">*</text>快递公司</text>
					<view class="form-value">
						<text :class="{ placeholder: !courier }">{{ courierName || '请选择' }}</text>
						<view class="arrow"></view>
					</view>
				</view>
			</picker>
			<view class="form-item">
				<text class="form-label"><text class="star">*</text>快递单号</text>
				<input class="form-input" v-model="trackingNumber" placeholder="请输入快递单号" />
			</view>
		</view>

		<!-- 底部提交 -->
		<view class="footer-bar">
			<view class="submit-btn" @click="submit">提交</view>
		</view>
	</view>
</template>

<script>
	import {
		submitAfterSaleReturn
	} from '../../api/after-sale.js'

	export default {
		data() {
			return {
				id: null,
				courier: '',
				trackingNumber: '',
				submitting: false, // 提交中，防连点重复回填
				// 快递公司列表（对齐后端 CourierEnum）
				courierOptions: [
					{ code: 'shunfeng', name: '顺丰速运' },
					{ code: 'yuantong', name: '圆通速递' },
					{ code: 'zhongtong', name: '中通快递' },
					{ code: 'yunda', name: '韵达快递' },
					{ code: 'shentong', name: '申通快递' },
					{ code: 'jitu', name: '极兔速递' },
					{ code: 'debang', name: '德邦快递' },
					{ code: 'youzhengguonei', name: '邮政国内' }
				]
			}
		},
		computed: {
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
		},
		methods: {
			onCourierChange(e) {
				const idx = Number(e.detail.value)
				this.courier = this.courierOptions[idx].code
			},
			submit() {
				if (this.submitting) return
				if (!this.courier) {
					uni.showToast({ title: '请选择快递公司', icon: 'none' })
					return
				}
				if (!this.trackingNumber || !this.trackingNumber.trim()) {
					uni.showToast({ title: '请输入快递单号', icon: 'none' })
					return
				}
				this.submitting = true
				submitAfterSaleReturn(this.id, {
					courier: this.courier,
					trackingNumber: this.trackingNumber.trim()
				}).then(() => {
					uni.showToast({ title: '回填成功', icon: 'success' })
					setTimeout(() => {
						uni.navigateBack()
					}, 600)
				}).catch(() => {
					// 失败允许重试
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

	.info-card {
		background: #ffffff;
		margin: 20rpx;
		border-radius: 16rpx;
		padding: 0 24rpx;
	}

	.form-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 100rpx;
	}

	.form-item + .form-item {
		border-top: 1rpx solid #f5f5f5;
	}

	.form-label {
		font-size: 28rpx;
		color: #333333;
	}

	.star {
		color: #ff4d4f;
		margin-right: 4rpx;
	}

	.form-value {
		display: flex;
		align-items: center;
	}

	.form-value .placeholder {
		color: #999999;
	}

	.form-input {
		flex: 1;
		margin-left: 40rpx;
		text-align: right;
		font-size: 28rpx;
	}

	.arrow {
		margin-left: 12rpx;
		width: 16rpx;
		height: 16rpx;
		border-top: 2rpx solid #cccccc;
		border-right: 2rpx solid #cccccc;
		transform: rotate(45deg);
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

	.submit-btn {
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		background: #FF6700;
		color: #ffffff;
		border-radius: 44rpx;
		font-size: 30rpx;
	}
</style>
