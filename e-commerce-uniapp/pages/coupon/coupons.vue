<template>
<view class="page">
		<CustomNavbar title="我的优惠券" :show-back="true" />

		<!-- 可领取优惠券：直接展示领券中心内容，不用跳页 -->
		<view v-if="centerList.length" class="center-section">
			<text class="section-title">可领取优惠券</text>
			<view v-for="item in centerList" :key="item.templateId" class="coupon-card">
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
					<text class="claim-btn" @click="handleClaim(item)">立即领取</text>
					<text class="remain">剩 {{ item.remainQuantity }} 张</text>
				</view>
			</view>
		</view>

		<!-- 状态页签 -->
		<view class="tabs">
			<view v-for="tab in tabs" :key="tab.value" class="tab" :class="{ active: current === tab.value }"
			      @click="switchTab(tab.value)">
				<text>{{ tab.label }}</text>
			</view>
		</view>

		<!-- 券列表 -->
		<view v-if="list.length" class="coupon-list">
			<view v-for="item in list" :key="item.userCouponId" class="coupon-card" :class="{ disabled: item.status !== 'AVAILABLE' }">
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
						<!-- 秒杀来源券：标「秒杀购得」，与免费领的券区分开 -->
						<text v-if="item.source === 'SECKILL'" class="seckill-tag">秒杀购得</text>
					</view>
					<text class="coupon-scope">{{ item.typeDesc }} · {{ scopeText(item.scopeType) }}</text>
					<text class="coupon-time">{{ timeText(item) }}</text>
					<text class="coupon-desc" v-if="item.description">{{ item.description }}</text>
				</view>
				<view class="coupon-status">
					<text v-if="item.status === 'AVAILABLE'" class="use-btn" @click="goUse">去使用</text>
					<!-- 已退款券：第一期仍展示但置灰，不可使用 -->
					<text v-else-if="item.status === 'REFUNDED'" class="status-text refunded">已退款</text>
					<text v-else class="status-text">{{ item.statusDesc }}</text>
				</view>
			</view>
		</view>

		<view v-else-if="!loading" class="empty">
			<text class="empty-text">{{ emptyText }}</text>
		</view>
	</view>
</template>

<script>
	import { claimCoupon, getCouponCenter, getMyCoupons } from '../../api/coupon.js'
	import { ensureLogin } from '../../utils/auth.js'

	// 「可使用」页签收纳所有还没用掉的券：AVAILABLE（现在就能用）/ NOT_STARTED（未生效）/ LOCKED（使用中）
	// 后端只会返回这几种状态；按状态各开一个页签会让后台发到手的券在某个状态下彻底看不见，
	// 所以统一归到「可使用」页签，卡片上再标出「未生效」「使用中」
	const USABLE_STATUS = ['AVAILABLE', 'NOT_STARTED', 'LOCKED']

	export default {
		data() {
			return {
				tabs: [
					{ value: 'AVAILABLE', label: '可使用' },
					{ value: 'USED', label: '已使用' },
					{ value: 'EXPIRED', label: '已过期' }
				],
				current: 'AVAILABLE',
				allList: [],
				list: [],
				centerList: [],
				loading: false,
				claiming: false
			}
		},
		computed: {
			emptyText() {
				return this.current === 'AVAILABLE' ? '暂无可用优惠券' : '暂无优惠券'
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			load() {
				this.loading = true
				ensureLogin()
					.then(() => Promise.all([getMyCoupons(), getCouponCenter()]))
					.then(([list, center]) => {
						// 一次拉全量，页签在本地过滤：切页签不用重新请求
						this.allList = list || []
						this.list = this.filterByTab(this.current)
						// 只展示还没领的券；已领取的会出现在「可使用」页签
						this.centerList = (center || []).filter(c => !c.claimed)
					})
					.catch(() => {})
					.finally(() => {
						this.loading = false
					})
			},
			filterByTab(tab) {
				if (tab === 'USED') return this.allList.filter(c => c.status === 'USED')
				if (tab === 'EXPIRED') return this.allList.filter(c => c.status === 'EXPIRED')
				// 已退款的券第一期仍展示（置灰 + 「已退款」标签），归到「可使用」页签
				return this.allList.filter(c => USABLE_STATUS.includes(c.status) || c.status === 'REFUNDED')
			},
			switchTab(value) {
				if (this.current === value) return
				this.current = value
				this.list = this.filterByTab(value)
			},
			handleClaim(item) {
				if (this.claiming) return
				this.claiming = true
				claimCoupon(item.templateId)
					.then(() => {
						uni.showToast({ title: '领取成功', icon: 'success' })
						// 领取后同时刷新「我的券」和可领列表
						this.load()
					})
					.catch(() => {})
					.finally(() => {
						this.claiming = false
					})
			},
			goUse() {
				uni.switchTab({ url: '/pages/index/index' })
			},
			thresholdText(item) {
				const threshold = Number(item.thresholdAmount) || 0
				return threshold > 0 ? '满 ¥' + this.fmt(threshold) + ' 可用' : '无门槛'
			},
			scopeText(scope) {
				return { ALL: '全场通用', CATEGORY: '指定分类', PRODUCT: '指定商品' }[scope] || '全场通用'
			},
			timeText(item) {
				if (item.status === 'USED') {
					return '使用时间：' + (item.usedTime || '-')
				}
				return (item.startTime || '') + ' ~ ' + (item.endTime || '')
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

	.center-section {
		padding: 20rpx 24rpx 0;
	}

	.section-title {
		display: block;
		margin-bottom: 16rpx;
		font-size: 28rpx;
		font-weight: bold;
		color: #333333;
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
		padding: 20rpx 24rpx 60rpx;
	}

	.coupon-card {
		display: flex;
		align-items: center;
		margin-bottom: 20rpx;
		padding: 28rpx 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	.coupon-card.disabled {
		opacity: 0.55;
	}

	.coupon-left {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 180rpx;
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

	.seckill-tag {
		margin-left: 10rpx;
		padding: 0 10rpx;
		font-size: 20rpx;
		color: #ffffff;
		background: linear-gradient(90deg, #FF8A00, #FF4D00);
		border-radius: 6rpx;
	}

	.coupon-scope,
	.coupon-time,
	.coupon-desc {
		margin-top: 6rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.coupon-status {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 120rpx;
	}

	.use-btn {
		padding: 8rpx 20rpx;
		font-size: 24rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 24rpx;
	}

	.status-text {
		font-size: 24rpx;
		color: #999999;
	}

	.status-text.refunded {
		color: #bbbbbb;
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

	.remain {
		margin-top: 10rpx;
		font-size: 20rpx;
		color: #bbbbbb;
	}

	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-top: 200rpx;
	}

	.empty-text {
		font-size: 28rpx;
		color: #999999;
	}
</style>
