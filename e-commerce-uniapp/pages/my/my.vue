<template>
<view class="page">
		<!-- 自定义顶部导航栏：tabBar 页没有上一级，只留标题 -->
		<CustomNavbar />

		<!-- 用户卡片：白底圆角卡片，浮在浅灰页面背景上，与下方功能卡片统一；头像可点击更换 -->
		<view class="user-card">
			<!-- chooseAvatar 必须由用户点击触发，所以包在 button 里；未登录时换成普通 view，点击提示登录 -->
			<button v-if="isLogin" open-type="chooseAvatar" @chooseavatar="onChooseAvatar" class="avatar-btn">
				<image v-if="userInfo.avatar" :src="userInfo.avatar" class="avatar"></image>
				<view v-else class="avatar avatar-default">微</view>
			</button>
			<view v-else class="avatar-btn" @click="handleAvatarClick">
				<image v-if="userInfo.avatar" :src="userInfo.avatar" class="avatar"></image>
				<view v-else class="avatar avatar-default">微</view>
			</view>
			<view class="user-meta">
				<text class="nickname">{{ userInfo.name || '微信用户' }}</text>
				<text class="user-tip">{{ isLogin ? '点击头像可更换' : '点击头像可登录' }}</text>
			</view>
			<!-- 设置入口：右上角齿轮，进个人信息页 -->
			<view class="setting-btn" @click="handleNav('/pages/my/personal-info/personal-info')">
				<uni-icons type="gear" size="22" color="#999999"></uni-icons>
			</view>
		</view>

		<!-- 会员卡：展示当前等级 / 成长值，点进会员中心 -->
		<view class="vip-card" v-if="features.vip" @click="handleNav('/pages/my/member/member')">
			<uni-icons type="vip-filled" size="22" color="#7A5410"></uni-icons>
			<view class="vip-meta">
				<text class="vip-title">{{ memberProfile ? memberProfile.levelName : '杰米电商会员' }}</text>
				<text class="vip-tip" v-if="memberProfile">
					成长值 {{ memberProfile.growthValue }}
					<text v-if="memberProfile.nextLevelName"> · 距{{ memberProfile.nextLevelName }}还差 {{ memberProfile.growthToNext }}</text>
				</text>
				<text class="vip-tip" v-else>查看等级与专属权益</text>
			</view>
			<view class="vip-btn">会员中心</view>
		</view>

		<!-- 我的订单：5 个状态入口 -->
		<view class="card">
			<view class="card-head">
				<text class="card-title">我的订单</text>
				<view class="card-more" @click="handleNav('/pages/order/list')">
					<text class="more-text">全部订单</text>
					<uni-icons type="right" size="14" color="#999"></uni-icons>
				</view>
			</view>
			<view class="order-grid">
				<view class="order-item" v-for="item in orderTabs" :key="item.type" @click="handleNav(item.url)">
					<!-- fontIcon 有值就用 myvue-icon 那套 iconfont，没有的先用 uni-icons 顶着；图标右上角红色数字角标 -->
					<view class="order-icon-box">
						<text v-if="item.fontIcon" class="myvue-icon order-icon" :class="item.fontIcon"></text>
						<uni-icons v-else :type="item.icon" size="52rpx"></uni-icons>
						<view v-if="item.badge > 0" class="order-badge">{{ item.badge > 99 ? '99+' : item.badge }}</view>
					</view>
					<text class="order-text">{{ item.text }}</text>
				</view>
			</view>
		</view>

		<!-- 优惠券 / 积分 / 专属客服：前两个未开通、url 为空，不渲染；专属客服进客服页 -->
		<view class="card stat-card">
			<view class="stat-item" v-for="item in visibleStats" :key="item.text" @click="handleNav(item.url)">
				<!-- 有数字的显示数字；专属客服没有数字，用图标顶上同一位置 -->
				<view class="stat-body">
					<text v-if="item.value !== undefined" class="stat-num">{{ item.value }}</text>
					<uni-icons v-else :type="item.icon" size="46rpx" ></uni-icons>
				</view>
				<text class="stat-text">{{ item.text }}</text>
			</view>
		</view>

		<!-- 功能入口 -->
		<view class="card">
			<view class="func-item" v-for="item in funcList" :key="item.text" @click="handleNav(item.url)">
				<!-- 图标统一包一层定宽盒：两套图标字形宽度不同，不包的话每行文字起点会错开。
				     尺寸：uni-icons 写 size（可直接写 rpx），iconfont 写在 .func-icon 里，两个值调成一样 -->
				<view class="func-icon-box">
					<text v-if="item.fontIcon" class="myvue-icon func-icon" :class="item.fontIcon"></text>
					<uni-icons v-else :type="item.icon" size="46rpx"></uni-icons>
				</view>
				<text class="func-text">{{ item.text }}</text>
				<uni-icons type="right" size="14" color="#c0c0c0"></uni-icons>
			</view>
		</view>

		<!-- 自定义底部导航栏 -->
		<CustomTabBar />
	</view>
</template>

<script>
	import {
		useUserStore
	} from '../../store/user.js'
	import {
		ensureLogin,
		login
	} from '../../utils/auth.js'
	import {
		getProfile,
		uploadAvatar,
		saveAvatar
	} from '../../api/login.js'
	import {
		listOrders
	} from '../../api/order.js'
	import {
		listAfterSales
	} from '../../api/after-sale.js'
	import {
		getMyCoupons
	} from '../../api/coupon.js'
	import {
		getMemberProfile
	} from '../../api/member.js'
	import {
		FEATURES
	} from '../../utils/features.js'

	export default {
		data() {
			return {
				store: useUserStore(), // 用户登录态 + 用户信息，统一走 Pinia
				features: FEATURES, // 未开通功能开关，见 utils/features.js
				memberProfile: null, // 会员档案（等级/成长值）
				// 订单状态入口。图标两套写法：
				//   fontIcon —— static/myvue-icon 里的 iconfont 类名，有值就优先用它
				//   icon     —— fontIcon 为空时的兜底，用 uni-icons 的内置图标名
				// 把要用的图标类名填进 fontIcon，就会自动从 uni-icons 切过去
				// 待付款走独立页 pages/order/pending-pay；待发货 3 / 待收货 4 走 pages/order/list 状态参数
				// 待评价、退款/售后后端接口还没做，url 留空点击走占位提示
				orderTabs: [{
						type: 0,
						text: '待付款',
						fontIcon: '',
						icon: 'wallet',
						url: '/pages/order/pending-pay',
						badge: 0
					},
					{
						type: 1,
						text: '待发货',
						fontIcon: 'icon-31daifahuo',
						icon: 'gift',
						url: '/pages/order/list?status=3',
						badge: 0
					},
					{
						type: 2,
						text: '待收货',
						fontIcon: 'icon-daishouhuo',
						icon: 'paperplane',
						url: '/pages/order/list?status=4',
						badge: 0
					},
					{
						type: 3,
						text: '待评价',
						fontIcon: '',
						icon: 'compose',
						url: '/pages/order/list?status=5',
						badge: 0
					},
					{
						type: 4,
						text: '退款/售后',
						fontIcon: 'icon-shouhou',
						icon: 'refreshempty',
						url: '/pages/order/list?afterSale=1',
						badge: 0
					}
				],
				// 优惠券 / 积分 / 专属客服：前两个未开通，url 为空所以不渲染（见 visibleStats）
				// 专属客服没有数字，不给 value，模板会用 icon 顶在数字的位置上
				statList: [{
						text: '优惠券',
						value: 0,
						url: '/pages/coupon/coupons'
					},
					{
						text: '积分',
						value: 0,
						url: ''
					},
					{
						text: '专属客服',
						icon: 'headphones',
						url: '/pages/my/customer-service/customer-service'
					}
				],
				// 功能入口：url 留空的（如优惠券/积分）点击时给「功能开发中」占位提示
				funcList: [{
						text: '物流查询',
						fontIcon: 'icon-wuliuchaxun',
						icon: 'map-pin',
						url: '/pages/my/logistics/logistics'
					},
					{
						text: '秒杀订单',
						fontIcon: '',
						icon: 'fire',
						url: '/pages/coupon/seckill/seckill-order-list'
					},
					{
						text: '收藏与浏览',
						fontIcon: '',
						icon: 'star',
						url: '/pages/my/favorites/favorites'
					},
					{
						text: '收货地址',
						fontIcon: '',
						icon: 'location',
						url: '/pages/my/address/address'
					},
					{
						text: '意见反馈',
						fontIcon: '',
						icon: 'chat',
						url: '/pages/my/feedback/feedback'
					},
					{
						text: '关于我们',
						fontIcon: '',
						icon: 'info',
						url: '/pages/my/about-us/about-us'
					}
				]
			}
		},
		computed: {
			userInfo() {
				return this.store.userInfo
			},
			// 手动退出后 token 被清掉，头像区切换成「点击登录」形态
			isLogin() {
				return this.store.isLogin
			},
			// 只渲染已开通的入口：url 为空说明页面还没做，先隐藏；补上 url 后自动显示
			visibleStats() {
				return this.statList.filter(item => item.url)
			}
		},
		onShow() {
			// 免登录：进页面确保已静默登录（已登录会短路），再补拉一次资料（昵称/头像），失败静默忽略
			ensureLogin()
				.then(() => {
					// 登录就绪后再拉角标数量 + 资料
					this.loadBadges()
					this.loadMemberProfile()
					return getProfile()
				})
				.then((profile) => {
					this.store.setUserInfo({
						id: profile.id,
						name: profile.name,
						avatar: profile.avatar
					})
				})
				.catch(() => {})
		},
		methods: {
			// 拉各状态订单/售后数量，回填图标角标
			loadBadges() {
				listOrders().then((list) => {
					const orders = list || []
					this.setBadge('待付款', orders.filter(o => Number(o.orderStatus) === 1).length)
					this.setBadge('待发货', orders.filter(o => Number(o.orderStatus) === 3).length)
					this.setBadge('待收货', orders.filter(o => Number(o.orderStatus) === 4).length)
					this.setBadge('待评价', orders.filter(o => Number(o.orderStatus) === 5 && !o.reviewed).length)
				}).catch(() => {})
				listAfterSales().then((list) => {
					this.setBadge('退款/售后', (list || []).length)
				}).catch(() => {})
				// 优惠券数量：口径与「我的优惠券」的「可使用」页签一致（还没用掉的券）
				// 含未生效 / 使用中，只数 AVAILABLE 会让后台刚发的券在数量上看不出来
				getMyCoupons().then((list) => {
					const usable = (list || []).filter(c => ['AVAILABLE', 'NOT_STARTED', 'LOCKED'].includes(c.status))
					const item = this.statList.find(i => i.text === '优惠券')
					if (item) {
						item.value = usable.length
					}
				}).catch(() => {})
			},
			// 拉会员等级/成长值（失败静默，不影响页面）
			loadMemberProfile() {
				if (!this.features.vip) return
				getMemberProfile().then((profile) => {
					this.memberProfile = profile || null
				}).catch(() => {})
			},
			// 回填某个入口的角标数量
			setBadge(text, count) {
				const item = this.orderTabs.find(i => i.text === text)
				if (item) {
					item.badge = count
				}
			},
			// 页面跳转：url 为空说明目标页还没做，先给个提示，比点了没反应清楚
			handleNav(url) {
				if (!url) {
					uni.showToast({
						title: '功能开发中',
						icon: 'none'
					})
					return
				}
				uni.navigateTo({
					url
				})
			},
			// 未登录点头像：直接静默登录并刷新资料，登录后头像区自动切回 chooseAvatar 按钮
			handleAvatarClick() {
				login().then(() => getProfile()).then((profile) => {
					this.store.setUserInfo({
						id: profile.id,
						name: profile.name,
						avatar: profile.avatar
					})
					uni.showToast({
						title: '登录成功',
						icon: 'success'
					})
				}).catch(() => {
					// 登录失败提示已在 request 封装里统一处理
				})
			},
			// 换头像：chooseAvatar 给本地临时路径，先上传拿 URL，再存 DB，最后更新 Pinia
			onChooseAvatar(e) {
				const filePath = e.detail.avatarUrl
				if (!filePath) {
					return
				}
				uploadAvatar(filePath)
					.then((url) => saveAvatar(url))
					.then((url) => {
						this.store.setAvatar(url)
						uni.showToast({ title: '头像已更新', icon: 'success' })
					})
					.catch((err) => {
						uni.showToast({
							title: (err && err.message) || '头像上传失败',
							icon: 'none'
						})
					})
			}
		}
	}
</script>

<style>
	.page {
		display: flex;
		flex-direction: column;
		background: #F8F8F8;
		/* 给固定定位的底部导航栏留出空间，安全区高度一并算上 */
		padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
	}

	/* 用户卡片：白底圆角卡片，与下方功能卡片同一套间距/圆角，去掉大面积橙色 */
	.user-card {
		position: relative;
		display: flex;
		align-items: center;
		margin: 20rpx 20rpx 0;
		padding: 32rpx 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	/* 右上角设置齿轮：绝对定位到卡片右上角，点击进个人信息页 */
	.setting-btn {
		position: absolute;
		top: 52rpx;
		right: 22rpx;
		padding: 12rpx;
	}

	/* 头像按钮：去掉 button 默认的边框、背景和内边距，只当点击区域用 */
	.avatar-btn {
		width: 120rpx;
		height: 120rpx;
		flex-shrink: 0;
		padding: 0;
		margin: 0;
		background: none;
		border: none;
		line-height: 1;
		border-radius: 50%;
	}

	/* 头像：白底上加一圈浅灰描边，与头像图片边界更清楚 */
	.avatar {
		width: 120rpx;
		height: 120rpx;
		border-radius: 50%;
		border: 4rpx solid #f0f0f0;
	}

	/* 无头像时的占位块 */
	.avatar-default {
		display: flex;
		align-items: center;
		justify-content: center;
		background: #e6e6e6;
		color: #999;
		font-size: 48rpx;
	}

	.user-meta {
		display: flex;
		flex-direction: column;
		margin-left: 28rpx;
		/* 给右上角齿轮留出位置，昵称过长时不会压到图标 */
		padding-right: 56rpx;
	}

	.nickname {
		font-size: 36rpx;
		font-weight: bold;
		color: #333333;
	}

	.user-tip {
		margin-top: 10rpx;
		font-size: 22rpx;
		color: #999999;
	}

	/* VIP 会员卡（占位）：金色横条，与上方橙色用户卡区分开 */
	.vip-card {
		display: flex;
		align-items: center;
		margin: 20rpx 20rpx 0;
		padding: 0 28rpx;
		height: 120rpx;
		background: linear-gradient(90deg, #F7E3B5 0%, #E9C87A 100%);
		border-radius: 16rpx;
	}

	.vip-meta {
		flex: 1;
		display: flex;
		flex-direction: column;
		margin-left: 16rpx;
	}

	.vip-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #7A5410;
	}

	.vip-tip {
		margin-top: 6rpx;
		font-size: 22rpx;
		color: #A8802F;
	}

	/* 「立即开通」按钮：半透明白底胶囊，压在金卡上做次要按钮 */
	.vip-btn {
		height: 56rpx;
		line-height: 56rpx;
		padding: 0 24rpx;
		font-size: 24rpx;
		color: #7A5410;
		background: rgba(255, 255, 255, 0.7);
		border-radius: 28rpx;
	}

	/* 通用白卡片 */
	.card {
		margin: 20rpx 20rpx 0;
		padding: 0 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	/* 卡片头：标题在左，右侧一个「更多」入口 */
	.card-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 88rpx;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.card-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}

	.card-more {
		display: flex;
		align-items: center;
	}

	.more-text {
		margin-right: 4rpx;
		font-size: 24rpx;
		color: #999;
	}

	/* 订单入口：5 个等分排一行 */
	.order-grid {
		display: flex;
		padding: 24rpx 0 8rpx;
	}

	.order-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding-bottom: 20rpx;
	}

	.order-text {
		margin-top: 12rpx;
		font-size: 24rpx;
		color: #333333;
	}

	/* iconfont 图标：基类默认 16px 太小，按尺寸写死。
	   同一行的 uni-icons 也写成 size="52rpx"，两套名义尺寸保持一致 */
	.order-icon {
		font-size: 52rpx;
	}

	/* 图标容器：相对定位，作为右上角角标的定位基准 */
	.order-icon-box {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	/* 右上角红色数字角标 */
	.order-badge {
		position: absolute;
		top: -10rpx;
		right: -18rpx;
		min-width: 32rpx;
		height: 32rpx;
		padding: 0 8rpx;
		box-sizing: border-box;
		background: #ff4d4f;
		color: #ffffff;
		font-size: 20rpx;
		line-height: 32rpx;
		text-align: center;
		border-radius: 16rpx;
	}

	/* 优惠券 / 积分 / 专属客服（占位）：三栏等分，非首栏加左边框当分隔线。
	   放在 .card 之后，靠后者覆盖掉 .card 的左右内边距 */
	.stat-card {
		display: flex;
		padding: 0;
	}

	.stat-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 28rpx 0;
	}

	.stat-item + .stat-item {
		border-left: 1rpx solid #f0f0f0;
	}

	/* 数字 / 图标固定在同样的高度，保证三栏下方的标签对齐在同一水平线上。
	   高度要罩得住最高的那个 —— 数字是 36rpx，专属客服的图标是 40rpx */
	.stat-body {
		display: flex;
		align-items: center;
		justify-content: center;
		height: 48rpx;
	}

	.stat-num {
		font-size: 36rpx;
		font-weight: bold;
	}

	.stat-text {
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #999;
	}

	/* 功能入口：一行一项，图标 + 文字 + 右箭头 */
	.func-item {
		display: flex;
		align-items: center;
		height: 96rpx;
		border-bottom: 1rpx solid #f0f0f0;
	}

	/* 最后一项去掉下边框，免得和卡片边缘叠出一条粗线 */
	.func-item:last-child {
		border-bottom: none;
	}

	.func-text {
		flex: 1;
		margin-left: 20rpx;
		font-size: 28rpx;
		color: #333333;
	}

	/* 图标盒子：两套图标字形宽度不同，不固定盒宽的话每行文字起点会错开。
	   尺寸改 .func-icon 和模板里 uni-icons 的 size，两个值保持一致 */
	.func-icon-box {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 48rpx;
		height: 48rpx;
		flex-shrink: 0;
	}

	/* iconfont 那套（物流查询） */
	.func-icon {
		font-size: 40rpx;
	}
</style>
