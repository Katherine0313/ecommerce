<template>
	<view class="custom-navbar">
		<!-- 固定定位的导航栏本体，页面滚动时保持不动 -->
		<view class="navbar-fixed" :style="{ height: totalHeight + 'px', background: bgColor }">
			<!-- 状态栏占位：高度 = 手机状态栏高度，让内容避开刘海/信号栏 -->
			<view :style="{ height: statusBarHeight + 'px' }"></view>

			<!-- 导航内容区：右侧给微信胶囊按钮让位，避免内容被盖住 -->
			<view class="navbar-content"
				:style="{ height: navContentHeight + 'px', paddingRight: rightSafeArea + 'px' }">
				<!-- 返回按钮：tabBar 页面不要开，详情/订单等二级页传 showBack 打开 -->
				<view v-if="showBack" class="back-btn" @click="handleBack">
					<view class="back-arrow"></view>
				</view>

				<!-- 搜索框：点击只抛事件，跳哪个搜索页由页面自己决定 -->
				<view v-if="showSearch" class="search-box" @click="handleSearch">
					<uni-icons type="search" size="16" color="#999"></uni-icons>
					<text class="search-text">{{ searchPlaceholder }}</text>
				</view>

				<!-- 纯标题：没开搜索框时显示 -->
				<view v-if="!showSearch" class="title"
					:style="{ paddingLeft: rightSafeArea + 'px' }">{{ title }}</view>
			</view>
		</view>

		<!-- 占位块：把页面内容顶到固定导航栏下方，避免被盖住 -->
		<view :style="{ height: totalHeight + 'px' }"></view>
	</view>
</template>

<script>
	export default {
		name: 'CustomNavbar',
		props: {
			// 是否显示搜索框
			showSearch: { type: Boolean, default: false },
			// 是否显示返回按钮（tabBar 页面没有上一级，不要开）
			showBack: { type: Boolean, default: false },
			// 纯标题栏的文字
			title: { type: String, default: '' },
			// 搜索框占位文字
			searchPlaceholder: { type: String, default: '搜索商品' },
			// 导航栏背景色（默认品牌橙）
			bgColor: { type: String, default: '#FF6700' }
		},
		data() {
			return {
				statusBarHeight: 0, // 状态栏高度（px）
				navContentHeight: 44, // 导航内容区高度（px），非小程序端用这个默认值
				rightSafeArea: 0 // 右侧胶囊按钮避让宽度（px）
			}
		},
		computed: {
			// 导航栏总高度 = 状态栏 + 内容区
			totalHeight() {
				return this.statusBarHeight + this.navContentHeight
			}
		},
		created() {
			this.initNavbarSize()
			// 把总高度抛给页面：页面里的 scroll-view 要按它算确定高度（下拉刷新要求 scroll-view 有确定高度）
			this.$emit('heightChange', this.totalHeight)
		},
		methods: {
			// 按当前机型算出状态栏高度和胶囊按钮位置
			initNavbarSize() {
				const sysInfo = uni.getSystemInfoSync()
				this.statusBarHeight = sysInfo.statusBarHeight || 0

				// #ifdef MP-WEIXIN
				// 微信右上角有胶囊按钮：内容区与之等高，右边距让给它
				const menuRect = uni.getMenuButtonBoundingClientRect()
				if (menuRect && menuRect.height) {
					// 胶囊上下留白相等，用它反推导航内容区高度；华为部分机型反推偏小，44px 下限防搜索框被裁切
					this.navContentHeight = Math.max(50,(menuRect.top - this.statusBarHeight) * 2 + menuRect.height)
					this.rightSafeArea = sysInfo.windowWidth - menuRect.left + 8
				}
				// #endif
			},
			// 返回上一页
			handleBack() {
				uni.navigateBack()
			},
			// 搜索框点击：交给页面自己处理
			handleSearch() {
				this.$emit('search')
			}
		}
	}
</script>

<style scoped>
	/* width:100% 兜底：页面根节点若是 align-items:center 的 flex 容器，会把子元素宽度压成内容宽 */
	.custom-navbar {
		width: 100%;
	}

	/* 固定定位，保证页面滚动时导航栏不动 */
	.navbar-fixed {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		z-index: 99;
		box-sizing: border-box;
	}

	.navbar-content {
		display: flex;
		align-items: center;
		padding-left: 24rpx;
		box-sizing: border-box;
	}

	/* 返回按钮 */
	.back-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		width: 60rpx;
		height: 60rpx;
		margin-right: 8rpx;
	}

	/* 用边框旋转出返回箭头，省掉一张图片 */
	.back-arrow {
		width: 20rpx;
		height: 20rpx;
		border-left: 4rpx solid #ffffff;
		border-bottom: 4rpx solid #ffffff;
		transform: rotate(45deg);
	}

	/* 搜索框：撑满内容区剩余空间 */
	.search-box {
		display: flex;
		align-items: center;
		flex: 1;
		height: 60rpx;
		padding: 0 20rpx;
		background: #ffffff;
		border-radius: 30rpx;
		box-sizing: border-box;
	}

	.search-text {
		margin-left: 8rpx;
		font-size: 26rpx;
		color: #999;
	}

	/* 纯标题：左边补上和右侧胶囊等宽的空位，视觉才是真居中 */
	.title {
		flex: 1;
		font-size: 34rpx;
		font-weight: bold;
		color: #ffffff;
	}
</style>
