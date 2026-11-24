<template>
<view class="page">
		<CustomNavbar title="领券中心" :show-back="true" />

		<!-- 页签：普通券免费领 / 秒杀券付费抢 -->
		<view class="tabs">
			<view v-for="tab in tabs" :key="tab.value" class="tab" :class="{ active: current === tab.value }"
			      @click="switchTab(tab.value)">
				<text>{{ tab.label }}</text>
			</view>
		</view>

		<!-- 普通券 -->
		<block v-if="current === 'RECEIVE'">
			<view v-if="list.length" class="coupon-list">
				<view v-for="item in list" :key="item.templateId" class="coupon-card">
					<view class="coupon-left">
						<view class="coupon-value" v-if="item.type === 'DISCOUNT'">
							<text class="value-num">{{ (Number(item.discountRate) * 10).toFixed(1) }}</text>
							<text class="value-unit">折</text>
						</view>
						<view class="coupon-value" v-else>
							<text class="value-unit">¥</text>
							<text class="value-num">{{ fmt(item.faceValue) }}</text>
						</view>
						<text class="coupon-threshold">{{ thresholdText(item) }}</text>
					</view>
					<view class="coupon-right">
						<view class="coupon-name-row">
							<text class="coupon-name">{{ item.name }}</text>
							<text v-if="item.minMemberLevel > 0" class="member-tag">会员专享</text>
						</view>
						<text class="coupon-scope">{{ item.scopeTypeDesc }} · {{ item.targetGroupDesc }}</text>
						<text class="coupon-time">{{ item.validityDesc }}</text>
						<text class="coupon-desc" v-if="item.description">{{ item.description }}</text>
					</view>
					<view class="coupon-action">
						<text v-if="item.claimed" class="claimed-btn">已领取</text>
						<text v-else class="claim-btn" @click="handleClaim(item)">立即领取</text>
						<text class="remain">剩 {{ item.remainQuantity }} 张</text>
					</view>
				</view>
			</view>

			<view v-else-if="!loading" class="empty">
				<text class="empty-text">暂时没有可领取的优惠券</text>
			</view>
		</block>

		<!-- 秒杀券：只展示进行中的活动 -->
		<block v-else>
			<view v-if="seckillList.length" class="seckill-wrap">
				<scroll-view class="seckill-scroll" scroll-x="true" :enhanced="true" :show-scrollbar="true">
					<view class="seckill-row">
						<seckill-card v-for="item in seckillList" :key="item.couponId" :coupon="item"
						               @grab="goSeckill" />
					</view>
				</scroll-view>
				<view class="seckill-tip">只展示进行中的活动，点击卡片查看详情</view>
			</view>

			<view v-else-if="!loading" class="empty">
				<text class="empty-text">当前没有进行中的秒杀活动</text>
			</view>
		</block>
	</view>
</template>

<script>
	import { claimCoupon, getCouponCenter } from '../../api/coupon.js'
	import { getSeckillList } from '../../api/seckill.js'
	import { ensureLogin } from '../../utils/auth.js'
	import SeckillCard from '../index/component/seckill-card.vue'

	export default {
		components: {
			SeckillCard
		},
		data() {
			return {
				tabs: [
					{ value: 'RECEIVE', label: '可领取' },
					{ value: 'SECKILL', label: '限时秒杀' }
				],
				current: 'RECEIVE',
				list: [],
				seckillList: [],
				loading: false,
				claiming: false
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			load() {
				this.loading = true
				ensureLogin()
					.then(() => Promise.all([getCouponCenter(), getSeckillList(1, 20)]))
					.then(([list, page]) => {
						this.list = list || []
						// 秒杀 tab 只展示进行中的活动
						this.seckillList = ((page && page.list) || []).filter(i => i.activityStatus === 'ACTIVE')
					})
					.catch(() => {})
					.finally(() => {
						this.loading = false
					})
			},
			switchTab(value) {
				if (this.current === value) return
				this.current = value
			},
			handleClaim(item) {
				if (this.claiming) return
				this.claiming = true
				claimCoupon(item.templateId)
					.then(() => {
						uni.showToast({ title: '领取成功', icon: 'success' })
						this.load()
					})
					.catch(() => {})
					.finally(() => {
						this.claiming = false
					})
			},
			goSeckill(coupon) {
				uni.navigateTo({
					url: '/pages/coupon/seckill/seckill-detail?id=' + coupon.couponId
				})
			},
			thresholdText(item) {
				const threshold = Number(item.thresholdAmount) || 0
				return threshold > 0 ? '满 ¥' + this.fmt(threshold) + ' 可用' : '无门槛'
			},
			fmt(value) {
				const n = Number(value)
				return Number.isFinite(n) ? n.toFixed(2) : '0.00'
			}
		}
	}
</script>

<style scoped>
	.page {
		min-height: 100vh;
		background: #f8f8f8;
	}

	.tabs {
		display: flex;
		background: #ffffff;
	}

	.tab {
		flex: 1;
		padding: 24rpx 0;
		text-align: center;
		font-size: 28rpx;
		color: #666666;
		border-bottom: 4rpx solid transparent;
	}

	.tab.active {
		color: #FF6700;
		font-weight: bold;
		border-bottom-color: #FF6700;
	}

	.coupon-list {
		padding: 24rpx;
	}

	.seckill-wrap {
		padding: 24rpx 0 0;
	}

	.seckill-scroll {
		width: 100%;
		white-space: nowrap;
	}

	.seckill-row {
		display: inline-flex;
		align-items: flex-start;
		padding: 0 24rpx;
	}

	.seckill-tip {
		padding: 16rpx 24rpx 24rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.coupon-card {
		display: flex;
		align-items: center;
		margin-bottom: 20rpx;
		padding: 28rpx 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	.coupon-left {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 170rpx;
		padding-right: 20rpx;
		border-right: 1rpx dashed #eeeeee;
	}

	.coupon-value {
		display: flex;
		align-items: baseline;
		color: #FF6700;
	}

	.value-num {
		font-size: 48rpx;
		font-weight: bold;
	}

	.value-unit {
		font-size: 24rpx;
	}

	.coupon-threshold {
		margin-top: 6rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.coupon-right {
		flex: 1;
		display: flex;
		flex-direction: column;
		padding: 0 20rpx;
	}

	.coupon-name-row {
		display: flex;
		align-items: center;
	}

	.coupon-name {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}

	.member-tag {
		margin-left: 10rpx;
		padding: 0 10rpx;
		font-size: 20rpx;
		color: #7A5410;
		background: #F7E3B5;
		border-radius: 6rpx;
	}

	.coupon-scope,
	.coupon-time,
	.coupon-desc {
		margin-top: 6rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.coupon-action {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 140rpx;
	}

	.claim-btn {
		padding: 10rpx 22rpx;
		font-size: 24rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 26rpx;
	}

	.claimed-btn {
		padding: 10rpx 22rpx;
		font-size: 24rpx;
		color: #999999;
		background: #f2f2f2;
		border-radius: 26rpx;
	}

	.remain {
		margin-top: 10rpx;
		font-size: 20rpx;
		color: #bbbbbb;
	}

	.empty {
		display: flex;
		justify-content: center;
		padding-top: 220rpx;
	}

	.empty-text {
		font-size: 28rpx;
		color: #999999;
	}
</style>
