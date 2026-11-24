<template>
<view class="page">
		<CustomNavbar title="会员中心" :show-back="true" />

		<!-- 会员卡 -->
		<view class="member-card">
			<view class="member-head">
				<uni-icons type="vip-filled" size="28" color="#7A5410"></uni-icons>
				<text class="member-level">{{ profile ? profile.levelName : '普通会员' }}</text>
				<text v-if="profile && !profile.nextLevelName" class="max-tag">已满级</text>
			</view>
			<view class="growth-row">
				<text class="growth-value">{{ profile ? profile.growthValue : 0 }}</text>
				<text class="growth-label">成长值</text>
			</view>
			<view class="progress-wrap">
				<view class="progress-bar" :style="{ width: progress + '%' }"></view>
			</view>
			<text class="progress-tip" v-if="profile && profile.nextLevelName">
				距 {{ profile.nextLevelName }} 还差 {{ profile.growthToNext }} 成长值
			</text>
			<text class="progress-tip" v-else-if="profile">已达到最高等级</text>
			<text class="progress-tip" v-else>加载中…</text>
			<view class="benefit" v-if="profile && profile.benefits">
				<text class="benefit-label">当前权益</text>
				<text class="benefit-text">{{ profile.benefits }}</text>
			</view>
		</view>

		<!-- 等级说明 -->
		<view class="card">
			<text class="card-title">会员等级</text>
			<view v-for="level in levels" :key="level.id" class="level-item"
			      :class="{ active: profile && profile.levelId === level.id }">
				<view class="level-left">
					<text class="level-name">{{ level.name }}</text>
					<text class="level-benefit">{{ level.benefits || '—' }}</text>
				</view>
				<text class="level-threshold">{{ level.growthThreshold }} 成长值</text>
			</view>
		</view>

		<!-- 规则 -->
		<view class="card rule-card">
			<text class="card-title">成长规则</text>
			<text class="rule-line">· 已支付订单实付 1 元 = 1 成长值</text>
			<text class="rule-line">· 退款金额会从成长值中扣回</text>
			<text class="rule-line">· 成长值只升不降，达到门槛自动升级</text>
		</view>

		<view class="coupon-btn" @click="goCoupons">去领会员券</view>
	</view>
</template>

<script>
	import { getMemberLevels, getMemberProfile } from '../../../api/member.js'
	import { ensureLogin } from '../../../utils/auth.js'

	export default {
		data() {
			return {
				profile: null,
				levels: []
			}
		},
		computed: {
			progress() {
				const p = this.profile
				if (!p || !p.nextLevelThreshold) return p ? 100 : 0
				const current = Number(p.currentThreshold) || 0
				const next = Number(p.nextLevelThreshold) || 0
				if (next <= current) return 100
				const percent = ((Number(p.growthValue) - current) / (next - current)) * 100
				return Math.max(0, Math.min(100, Math.round(percent)))
			}
		},
		onShow() {
			this.load()
		},
		methods: {
			load() {
				ensureLogin()
					.then(() => Promise.all([getMemberProfile(), getMemberLevels()]))
					.then(([profile, levels]) => {
						this.profile = profile || null
						this.levels = levels || []
					})
					.catch(() => {})
			},
			goCoupons() {
				uni.navigateTo({ url: '/pages/coupon/coupon-center' })
			}
		}
	}
</script>

<style scoped>
	.page {
		min-height: 100vh;
		padding-bottom: 60rpx;
		background: #f8f8f8;
	}

	.member-card {
		margin: 24rpx;
		padding: 36rpx 32rpx;
		background: linear-gradient(135deg, #F7E3B5 0%, #E9C87A 100%);
		border-radius: 20rpx;
	}

	.member-head {
		display: flex;
		align-items: center;
	}

	.member-level {
		margin-left: 12rpx;
		font-size: 34rpx;
		font-weight: bold;
		color: #6b4a0f;
	}

	.max-tag {
		margin-left: 14rpx;
		padding: 2rpx 12rpx;
		font-size: 20rpx;
		color: #6b4a0f;
		border: 1rpx solid #6b4a0f;
		border-radius: 20rpx;
	}

	.growth-row {
		display: flex;
		align-items: baseline;
		margin-top: 26rpx;
	}

	.growth-value {
		font-size: 52rpx;
		font-weight: bold;
		color: #6b4a0f;
	}

	.growth-label {
		margin-left: 10rpx;
		font-size: 24rpx;
		color: #8a6a2a;
	}

	.progress-wrap {
		margin-top: 16rpx;
		height: 12rpx;
		background: rgba(107, 74, 15, 0.15);
		border-radius: 8rpx;
		overflow: hidden;
	}

	.progress-bar {
		height: 100%;
		background: #6b4a0f;
		border-radius: 8rpx;
	}

	.progress-tip {
		display: block;
		margin-top: 12rpx;
		font-size: 22rpx;
		color: #8a6a2a;
	}

	.benefit {
		display: flex;
		margin-top: 24rpx;
		padding-top: 20rpx;
		border-top: 1rpx solid rgba(107, 74, 15, 0.15);
	}

	.benefit-label {
		font-size: 24rpx;
		color: #6b4a0f;
	}

	.benefit-text {
		flex: 1;
		margin-left: 12rpx;
		font-size: 24rpx;
		color: #7a5a1c;
	}

	.card {
		margin: 24rpx;
		padding: 28rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	.card-title {
		display: block;
		margin-bottom: 18rpx;
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}

	.level-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 20rpx 18rpx;
		border-radius: 12rpx;
	}

	.level-item.active {
		background: #fff7ea;
	}

	.level-left {
		display: flex;
		flex-direction: column;
	}

	.level-name {
		font-size: 28rpx;
		color: #333333;
	}

	.level-benefit {
		margin-top: 6rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.level-threshold {
		font-size: 24rpx;
		color: #FF6700;
	}

	.rule-line {
		display: block;
		margin-top: 10rpx;
		font-size: 24rpx;
		color: #666666;
	}

	.coupon-btn {
		margin: 40rpx 24rpx 0;
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		font-size: 30rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 44rpx;
	}
</style>
