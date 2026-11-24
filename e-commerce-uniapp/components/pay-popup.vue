<template>
	<view>
		<!-- 模拟微信支付弹窗：6 位密码框 + 金额 -->
		<view v-if="visible" class="pay-popup-mask" @click="handleClose">
			<view class="pay-popup" @click.stop>
				<view class="popup-header">
					<text class="popup-title">{{ title }}</text>
					<text class="close-icon" @click="handleClose">×</text>
				</view>
				<view class="pay-amount">¥{{ fmtAmount }}</view>
				<view class="pwd-box">
					<view class="pwd-item" v-for="(item, index) in 6" :key="index">
						<text v-if="payPassword[index]">●</text>
					</view>
				</view>
				<text class="tip-text">{{ loading ? '支付中…' : '请输入支付密码' }}</text>
			</view>
		</view>

		<!-- 底部数字键盘：弹窗打开时显示；支付请求中禁用，防止重复输入 -->
		<view v-if="visible" class="keyboard-wrap" :class="{ 'keyboard-disabled': loading }">
			<view class="keyboard-row">
				<view class="key-item" @click="inputNum('1')">1</view>
				<view class="key-item" @click="inputNum('2')">2</view>
				<view class="key-item" @click="inputNum('3')">3</view>
			</view>
			<view class="keyboard-row">
				<view class="key-item" @click="inputNum('4')">4</view>
				<view class="key-item" @click="inputNum('5')">5</view>
				<view class="key-item" @click="inputNum('6')">6</view>
			</view>
			<view class="keyboard-row">
				<view class="key-item" @click="inputNum('7')">7</view>
				<view class="key-item" @click="inputNum('8')">8</view>
				<view class="key-item" @click="inputNum('9')">9</view>
			</view>
			<view class="keyboard-row">
				<view class="key-item empty-key"></view>
				<view class="key-item" @click="inputNum('0')">0</view>
				<!-- 退格删除：自定义 iconfont 图标（clear-icon） -->
				<view class="key-item" @click="deleteNum">
					<text class="clear-icon icon-shanchuqingchuqingkong" style="font-size:44rpx;color:#333333;"></text>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		props: {
			// 弹窗显隐
			visible: { type: Boolean, default: false },
			// 支付金额
			amount: { type: [Number, String], default: 0 },
			// 弹窗标题（商家名）
			title: { type: String, default: '杰米电商' },
			// 支付请求是否进行中：锁键盘、保留已输入的 6 位
			loading: { type: Boolean, default: false }
		},
		data() {
			return {
				payPassword: '' // 模拟支付密码输入
			}
		},
		computed: {
			// 金额统一两位小数展示
			fmtAmount() {
				return (Number(this.amount) || 0).toFixed(2)
			}
		},
		watch: {
			// 弹窗重新打开时清空上次密码
			visible(val) {
				if (val) {
					this.payPassword = ''
				}
			},
			// 支付结束但弹窗还开着，说明支付失败：清空密码允许重输
			// 支付成功时父组件会先关闭弹窗，这里不会误清
			loading(val) {
				if (!val && this.visible) {
					this.payPassword = ''
				}
			}
		},
		methods: {
			// 输入数字：追加到密码，满 6 位自动触发支付
			inputNum(num) {
				// 支付请求中，或已经输满 6 位：不再接受输入
				if (this.loading || this.payPassword.length >= 6) {
					return
				}
				this.payPassword += num
				if (this.payPassword.length === 6) {
					// 通知父组件支付；先保留 6 个圆点，等 loading 结束再决定是否清空
					this.$emit('confirm')
				}
			},
			// 删除一位密码：输满 6 位或支付中都不允许删除
			deleteNum() {
				if (this.loading || this.payPassword.length >= 6) {
					return
				}
				if (this.payPassword.length > 0) {
					this.payPassword = this.payPassword.slice(0, -1)
				}
			},
			// 点遮罩或 ×：关闭弹窗
			handleClose() {
				this.$emit('close')
			}
		}
	}
</script>

<style scoped>
	/* ═══ 模拟微信支付弹窗 ═══ */
	.pay-popup-mask {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		background: rgba(0, 0, 0, 0.6);
		z-index: 1100;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.pay-popup {
		width: 620rpx;
		background: #ffffff;
		border-radius: 20rpx;
		padding: 60rpx 40rpx 30rpx 40rpx;
		box-sizing: border-box;
	}

	.popup-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 30rpx;
		position: relative;
	}

	.popup-title {
		position: absolute;
		font-size: 32rpx;
		color: #333333;
		text-align: center;
		left: 180rpx;
	}

	.close-icon {
		font-size: 40rpx;
		color: #999999;
		line-height: 1;
		position: absolute;
		right: 2rpx;
	}

	.pay-amount {
		text-align: center;
		font-size: 52rpx;
		font-weight: bold;
		color: #333333;
		margin-bottom: 40rpx;
	}

	.pwd-box {
		display: flex;
		gap: 16rpx;
	}

	.pwd-item {
		flex: 1;
		height: 80rpx;
		border: 1rpx solid #dddddd;
		border-radius: 8rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 36rpx;
		color: #333333;
	}

	.tip-text {
		display: block;
		text-align: center;
		font-size: 26rpx;
		color: #999999;
		margin-top: 24rpx;
	}

	/* ═══ 底部数字键盘 ═══ */
	.keyboard-wrap {
		position: fixed;
		bottom: 0;
		left: 0;
		width: 100%;
		background: #e5e5e5;
		z-index: 1101;
		padding: 20rpx;
		padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
		box-sizing: border-box;
	}

	.keyboard-row {
		display: flex;
		gap: 16rpx;
		margin-bottom: 16rpx;
	}

	.keyboard-row:last-child {
		margin-bottom: 0;
	}

	/* 支付请求中：键盘整体置灰并禁止点击 */
	.keyboard-disabled {
		opacity: 0.5;
		pointer-events: none;
	}

	.key-item {
		flex: 1;
		height: 90rpx;
		background: #ffffff;
		border-radius: 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 32rpx;
		color: #333333;
	}

	.empty-key {
		background: transparent;
		height: 90rpx;
		border: 1rpx solid #dddddd;
		border-radius: 12rpx;
	}
</style>
