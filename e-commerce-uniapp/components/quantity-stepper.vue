<template>
	<!-- 数量步进器：- 按钮 / 可输入数字框 / + 按钮。抽成全局组件，购物车页与加购弹窗共用。
	     受控：数量由父组件持有，这里只抛 change 事件，父组件异步决定是否接受 -->
	<view class="qty-stepper">
		<view class="qty-btn" :class="{ disabled: value <= min }" :hover-class="value <= min ? '' : 'qty-btn-hover'" @click="handleMinus">−</view>
		<input class="qty-input" type="number" :value="display" @input="handleInput" @blur="commit" @confirm="commit" />
		<view class="qty-btn" hover-class="qty-btn-hover" @click="handlePlus">+</view>
	</view>
</template>

<script>
	export default {
		name: 'QuantityStepper',
		props: {
			// 当前数量（默认 1），由父组件持有并回传
			value: { type: Number, default: 1 },
			// 最小值，减到这就不能再减
			min: { type: Number, default: 1 },
			// 最大值（库存上限），null 表示不限制
			max: { type: Number, default: null }
		},
		data() {
			return {
				// 输入框本地显示值：编辑中间态不直接回写受控 value，避免输入体验被打断
				display: String(this.value)
			}
		},
		watch: {
			// 外部 value 变化（+/- 或父组件异步更新）时同步输入框显示
			value(val) {
				this.display = String(val)
			}
		},
		methods: {
			// 减：不小于 min
			handleMinus() {
				if (this.value <= this.min) {
					return
				}
				this.$emit('change', this.value - 1)
			},
			// 加：不超过库存上限
			handlePlus() {
				const next = this.value + 1
				if (this.max != null && next > this.max) {
					uni.showToast({ title: '已达库存上限 ' + this.max + ' 件', icon: 'none' })
					return
				}
				this.$emit('change', next)
			},
			// 输入中间态：只更新本地显示，不提交
			handleInput(e) {
				this.display = e.detail.value
			},
			// 失焦/键盘确认提交：校验为正整数、>= min 且 <= max，合法才 emit；非法恢复原值并提示
			commit() {
				const n = parseInt(this.display, 10)
				if (!n || n < this.min || n === this.value) {
					this.display = String(this.value)
					return
				}
				if (this.max != null && n > this.max) {
					uni.showToast({ title: '库存不足，最多 ' + this.max + ' 件', icon: 'none' })
					this.display = String(this.value)
					return
				}
				// 先恢复显示，父组件异步决定是否接受；接受后 value 回传会再同步
				this.display = String(this.value)
				this.$emit('change', n)
			}
		}
	}
</script>

<style scoped>
	.qty-stepper {
		display: flex;
		align-items: center;
		border: 1rpx solid #e0e0e0;
		border-radius: 8rpx;
		background: #ffffff;
	}

	.qty-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 42rpx;
		font-size: 36rpx;
		color: #333333;
	}

	.qty-btn.disabled {
		color: #cccccc;
	}

	/* 按下反馈：浅灰底（background-clip 限制在视觉按钮内），点中了有肉眼可见的变化 */
	.qty-btn-hover {
		background: #f0f0f0;
	}

	.qty-input {
		width: 42rpx;
		height: 32rpx;
		text-align: center;
		font-size: 24rpx;
		font-weight: 600;
		color: #111111;
	}
</style>
