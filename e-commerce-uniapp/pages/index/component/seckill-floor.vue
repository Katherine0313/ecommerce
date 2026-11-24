<template>
	<!-- 首页秒杀楼层：只在有「进行中」活动时出现，没有活动整个楼层隐藏，不占位置 -->
	<view v-if="activeList.length" class="seckill-floor">
		<view class="floor-head">
			<view class="title-box">
				<text class="title">🔥 限时秒杀</text>
				<text class="subtitle">低价抢券</text>
			</view>
			<view class="countdown-box">
				<text class="cd-label">{{ cdLabel }}</text>
				<text class="cd-time">{{ countdownText }}</text>
			</view>
		</view>

		<!-- 横向滑动卡片条 -->
		<scroll-view
			class="card-scroll"
			scroll-x="true"
			:scroll-left="cardScrollLeft"
			:show-scrollbar="false"
			@scroll="onCardScroll"
		>
			<view class="card-row">
				<seckill-card v-for="item in activeList" :key="item.couponId" :coupon="item" @grab="handleGrab" />
			</view>
		</scroll-view>
		<view
			v-if="showCardScrollbar"
			class="card-scrollbar"
			@touchstart.stop="onScrollbarTouchStart"
			@touchmove.stop.prevent="onScrollbarTouchMove"
			@touchend.stop="onScrollbarTouchEnd"
			@touchcancel.stop="onScrollbarTouchEnd"
		>
			<view
				class="card-scrollbar-thumb"
				:style="{ width: cardThumbWidth + '%', left: cardThumbLeft + '%' }"
			></view>
		</view>
	</view>
</template>

<script>
	import { getSeckillList } from '../../../api/seckill.js'
	import SeckillCard from './seckill-card.vue'
	import countdown from './countdown.js'

	export default {
		name: 'SeckillFloor',
		components: {
			SeckillCard
		},
		mixins: [countdown],
		data() {
			return {
				list: [],
				cardViewWidth: 0,
				cardScrollWidth: 0,
				cardScrollLeft: 0,
				showCardScrollbar: false,
				cardThumbWidth: 100,
				cardThumbLeft: 0,
				scrollbarTrack: null,
				scrollbarTouchX: 0
			}
		},
		computed: {
			// 只展示进行中的活动（activityStatus === 'ACTIVE'）
			activeList() {
				return this.list.filter(item => item.activityStatus === 'ACTIVE')
			},
			// 倒计时目标：最早结束的活动
			countdownTarget() {
				let target = null
				this.activeList.forEach(item => {
					if (!target || item.seckillEndTime < target) target = item.seckillEndTime
				})
				return target
			},
			countdownText() {
				return this.countdownTarget ? this.fmtCountdown(this.countdownTarget) : '00:00:00'
			},
			cdLabel() {
				return '距结束'
			}
		},
		mounted() {
			this.load()
		},
		methods: {
			load() {
				getSeckillList(1, 10)
					.then((page) => {
						this.list = (page && page.list) || []
						this.initCardScrollbar()
					})
					.catch(() => {
						this.list = []
					})
			},
			// 微信开发者工具不保证显示原生滚动条，这里绘制常驻滚动条并同步位置
			initCardScrollbar() {
				this.$nextTick(() => {
					const query = uni.createSelectorQuery().in(this)
					query.select('.card-scroll').fields({ size: true, scrollOffset: true })
					query.select('.card-row').boundingClientRect()
					query.exec((res) => {
						const viewport = res && res[0]
						const content = res && res[1]
						if (!viewport) return
						this.cardViewWidth = viewport.width || 0
						const scrollWidth = Math.max(viewport.scrollWidth || 0, content ? content.width || 0 : 0)
						this.updateCardScrollbar(viewport.scrollLeft || 0, scrollWidth)
					})
				})
			},
			onCardScroll(e) {
				const detail = e.detail || {}
				this.updateCardScrollbar(detail.scrollLeft || 0, detail.scrollWidth || 0)
			},
			updateCardScrollbar(scrollLeft, scrollWidth) {
				const viewWidth = this.cardViewWidth
				this.cardScrollLeft = scrollLeft
				this.cardScrollWidth = scrollWidth
				if (!viewWidth || !scrollWidth || scrollWidth <= viewWidth) {
					this.showCardScrollbar = false
					return
				}
				const thumbWidth = Math.max(12, viewWidth / scrollWidth * 100)
				const maxThumbLeft = 100 - thumbWidth
				const maxScrollLeft = scrollWidth - viewWidth
				this.showCardScrollbar = true
				this.cardThumbWidth = thumbWidth
				this.cardThumbLeft = Math.min(maxThumbLeft, Math.max(0, scrollLeft / maxScrollLeft * maxThumbLeft))
			},
			onScrollbarTouchStart(e) {
				const touch = e.touches && e.touches[0]
				if (!touch) return
				this.scrollbarTouchX = touch.clientX
				this.scrollbarTrack = null
				uni.createSelectorQuery().in(this).select('.card-scrollbar').boundingClientRect().exec((res) => {
					const track = res && res[0]
					if (!track) return
					this.scrollbarTrack = track
					this.scrollToScrollbarTouch(this.scrollbarTouchX)
				})
			},
			onScrollbarTouchMove(e) {
				const touch = e.touches && e.touches[0]
				if (!touch) return
				this.scrollbarTouchX = touch.clientX
				this.scrollToScrollbarTouch(touch.clientX)
			},
			onScrollbarTouchEnd() {
				this.scrollbarTrack = null
			},
			scrollToScrollbarTouch(clientX) {
				const track = this.scrollbarTrack
				const maxScrollLeft = this.cardScrollWidth - this.cardViewWidth
				if (!track || !track.width || maxScrollLeft <= 0) return
				const thumbWidth = track.width * this.cardThumbWidth / 100
				const maxThumbLeft = Math.max(0, track.width - thumbWidth)
				const thumbLeft = Math.min(maxThumbLeft, Math.max(0, clientX - track.left - thumbWidth / 2))
				const scrollLeft = maxThumbLeft ? thumbLeft / maxThumbLeft * maxScrollLeft : 0
				this.updateCardScrollbar(scrollLeft, this.cardScrollWidth)
			},
			handleGrab(coupon) {
				this.$emit('grab', coupon)
				uni.navigateTo({
					url: '/pages/coupon/seckill/seckill-detail?id=' + coupon.couponId
				})
			}
		}
	}
</script>

<style scoped>
	.seckill-floor {
		margin: 20rpx 24rpx 0;
		padding: 20rpx 0 24rpx;
		background: linear-gradient(135deg, #2b1a10 0%, #4a2410 100%);
		border-radius: 20rpx;
	}

	.floor-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0 24rpx 16rpx;
	}

	.title-box {
		display: flex;
		flex-direction: column;
	}

	.title {
		font-size: 32rpx;
		font-weight: bold;
		color: #FFD666;
	}

	.subtitle {
		margin-top: 2rpx;
		font-size: 20rpx;
		color: rgba(255, 255, 255, 0.55);
	}

	.countdown-box {
		display: flex;
		align-items: center;
	}

	.cd-label {
		font-size: 22rpx;
		color: rgba(255, 255, 255, 0.75);
	}

	.cd-time {
		margin-left: 8rpx;
		padding: 2rpx 12rpx;
		font-size: 22rpx;
		color: #FFD666;
		background: rgba(0, 0, 0, 0.35);
		border-radius: 20rpx;
	}

	.card-scroll {
		width: 100%;
		white-space: nowrap;
	}

	.card-row {
		display: inline-flex;
		align-items: flex-start;
		padding: 0 24rpx;
	}

	.card-scrollbar {
		position: relative;
		height: 8rpx;
		margin: 16rpx 24rpx 0;
		overflow: hidden;
		background: rgba(255, 255, 255, 0.2);
		border-radius: 8rpx;
	}

	.card-scrollbar-thumb {
		position: absolute;
		top: 0;
		height: 100%;
		background: #FFD666;
		border-radius: 8rpx;
	}
</style>
