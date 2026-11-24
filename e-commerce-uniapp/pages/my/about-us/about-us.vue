<template>
<view class="page">
		<CustomNavbar title="关于我们" :show-back="true" />

		<!-- 品牌头卡：Logo + 名称 + 标语 + 版本 -->
		<view class="brand-card">
			<view class="logo">杰</view>
			<text class="brand-name">杰米多多</text>
			<text class="brand-slogan">好物多一点，生活近一点</text>
			<text class="version">Version {{ version }}</text>
		</view>

		<!-- 品牌简介 -->
		<view class="card">
			<text class="section-title">关于杰米多多</text>
			<text class="intro">杰米多多是一家专注品质生活的自营电商小店，精选数码、3C电子与日常好物，从浏览、下单到售后，提供一站式的购物体验。</text>
			<text class="intro">我们相信，好的购物体验不只是买到东西，而是每一次打开小程序，都能被认真对待。</text>
		</view>

		<!-- 服务承诺 -->
		<view class="card promise-card">
			<view class="promise-item" v-for="item in promises" :key="item.title">
				<uni-icons :type="item.icon" size="44rpx" color="#FF6700"></uni-icons>
				<text class="promise-title">{{ item.title }}</text>
				<text class="promise-desc">{{ item.desc }}</text>
			</view>
		</view>

		<!-- 信息入口 -->
		<view class="card">
			<view class="link-item" v-for="item in visibleLinks" :key="item.text" @click="handleLink(item)">
				<uni-icons :type="item.icon" size="40rpx" color="#666666"></uni-icons>
				<text class="link-text">{{ item.text }}</text>
				<text v-if="item.extra" class="link-extra">{{ item.extra }}</text>
				<uni-icons v-else type="right" size="14" color="#c0c0c0"></uni-icons>
			</view>
		</view>

		<!-- 底部版权 -->
		<view class="footer">
			<text class="footer-text">© 2026 杰米多多</text>
			<text class="footer-sub">用心经营每一笔订单</text>
		</view>
	</view>
</template>

<script>
	import {
		FEATURES
	} from '../../../utils/features.js'

	// 与 manifest.json 的 versionName 保持一致；改版本时两处一起改
	const APP_VERSION = '1.0.0'

	export default {
		data() {
			return {
				version: APP_VERSION,
				// 服务承诺：都是当前项目实际支持的，不写未实现的能力
				promises: [{
						icon: 'checkmarkempty',
						title: '正品保障',
						desc: '自营精选好物'
					},
					{
						icon: 'refreshempty',
						title: '售后无忧',
						desc: '退款/退货/换货'
					},
					{
						icon: 'locked',
						title: '信息安全',
						desc: '隐私数据保护'
					}
				],
				// 信息入口：协议/隐私政策内容由后台文章维护，联系我们直接进已有的客服页
				links: [{
						icon: 'info',
						text: '版本信息',
						extra: 'V' + APP_VERSION,
						action: 'version'
					},
					{
						icon: 'list',
						text: '用户协议',
						action: 'agreement',
						articleTitle: '用户协议',
						enabled: FEATURES.agreement
					},
					{
						icon: 'locked',
						text: '隐私政策',
						action: 'privacy',
						articleTitle: '隐私政策',
						enabled: FEATURES.privacy
					},
					{
						icon: 'phone',
						text: '联系我们',
						action: 'contact',
						url: '/pages/my/customer-service/customer-service'
					}
				]
			}
		},
		computed: {
			// 只显示已开通的入口：enabled 明确为 false 的过滤掉
			visibleLinks() {
				return this.links.filter(item => item.enabled !== false)
			}
		},
		methods: {
			handleLink(item) {
				if (item.action === 'version') {
					uni.showToast({
						title: '当前已是最新版本',
						icon: 'none'
					})
					return
				}
				// 有 url 的直接跳转（联系我们 → 客服页）
				if (item.url) {
					uni.navigateTo({
						url: item.url
					})
					return
				}
				// 协议类内容在后台文章里，按标题进文章详情页
				if (item.articleTitle) {
					uni.navigateTo({
						url: '/pages/article/detail?title=' + encodeURIComponent(item.articleTitle)
					})
					return
				}
				const tips = {
					agreement: '用户协议开发中',
					privacy: '隐私政策开发中'
				}
				uni.showToast({
					title: tips[item.action] || '功能开发中',
					icon: 'none'
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
		box-sizing: border-box;
		padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
		background: #F8F8F8;
	}

	/* 品牌头卡 */
	.brand-card {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin: 20rpx 20rpx 0;
		padding: 48rpx 24rpx 40rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	/* 无图片 Logo：品牌橙圆角方块 + 首字，避免额外占包体积 */
	.logo {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 128rpx;
		height: 128rpx;
		background: linear-gradient(135deg, #FF8A3D 0%, #FF6700 100%);
		color: #ffffff;
		font-size: 60rpx;
		font-weight: bold;
		border-radius: 32rpx;
	}

	.brand-name {
		margin-top: 24rpx;
		font-size: 36rpx;
		font-weight: bold;
		color: #333333;
	}

	.brand-slogan {
		margin-top: 10rpx;
		font-size: 24rpx;
		color: #999999;
	}

	.version {
		margin-top: 16rpx;
		font-size: 22rpx;
		color: #c0c0c0;
	}

	/* 通用白卡片 */
	.card {
		margin: 20rpx 20rpx 0;
		padding: 28rpx 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	.section-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}

	.intro {
		display: block;
		margin-top: 16rpx;
		font-size: 26rpx;
		line-height: 1.7;
		color: #666666;
	}

	/* 服务承诺：三栏等分 */
	.promise-card {
		display: flex;
		padding: 32rpx 0;
	}

	.promise-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
	}

	.promise-item + .promise-item {
		border-left: 1rpx solid #f0f0f0;
	}

	.promise-title {
		margin-top: 12rpx;
		font-size: 26rpx;
		color: #333333;
	}

	.promise-desc {
		margin-top: 6rpx;
		font-size: 22rpx;
		color: #999999;
	}

	/* 信息入口 */
	.link-item {
		display: flex;
		align-items: center;
		height: 96rpx;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.link-item:last-child {
		border-bottom: none;
	}

	.link-text {
		flex: 1;
		margin-left: 20rpx;
		font-size: 28rpx;
		color: #333333;
	}

	.link-extra {
		font-size: 24rpx;
		color: #999999;
	}

	/* 底部版权 */
	.footer {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-top: 60rpx;
	}

	.footer-text {
		font-size: 22rpx;
		color: #999999;
	}

	.footer-sub {
		margin-top: 8rpx;
		font-size: 20rpx;
		color: #c0c0c0;
	}
</style>
