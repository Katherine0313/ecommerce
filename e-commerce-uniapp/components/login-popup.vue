<template>
	<!-- 登录弹窗：打开即静默登录（uni.login 换 code，无授权框），头像可选。遮罩/居中/点遮罩关闭交给 uni-popup（uni-ui，easycom 免注册） -->
	<uni-popup ref="popup" class="login-popup" type="center" :is-mask-click="true" @change="handleChange">
		<view class="popup-card">
			<text class="title">登录提示</text>
			<text class="desc">登录后可加入购物车</text>
			<!-- 头像可选：chooseAvatar 必须由用户点击触发，所以包在 button 里 -->
			<button open-type="chooseAvatar" @chooseavatar="onChooseAvatar" class="avatar-btn">
				<image v-if="avatar" :src="avatar" class="avatar"></image>
				<view v-else class="avatar avatar-default">微</view>
			</button>
			<!-- 头像随静默登录自动从后端带回来；微信不允许自动弹头像选择框，要换得用户点一下 -->
			<text class="avatar-tip">{{ avatar ? '点击可更换头像' : '点击可选择微信头像' }}</text>
			<view class="close-btn" @click="handleClose">关闭</view>
		</view>
	</uni-popup>
</template>

<script>
	import {
		useUserStore
	} from '../store/user.js'
	import {
		ensureLogin
	} from '../utils/auth.js'
	import {
		getProfile,
		uploadAvatar
	} from '../api/login.js'

	export default {
		name: 'LoginPopup',
		props: {
			// 是否展示弹窗，由页面控制
			show: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				// 内部开关状态，配合页面传入的 show，避免重复 open/close 触发多余的 change
				opened: false,
				store: useUserStore() // 登录态 + 用户信息，统一走 Pinia
			}
		},
		computed: {
			// 头像：上传成功后 setAvatar 回写 store，这里直接读
			avatar() {
				return this.store.userInfo.avatar || ''
			}
		},
		watch: {
			// uni-popup 是 ref 调 open()/close() 的命令式用法，这里把页面的 show 开关翻译成对应调用
			show(val) {
				const popup = this.$refs.popup
				if (!popup) {
					return
				}
				if (val && !this.opened) {
					this.opened = true
					popup.open()
					// 弹窗一打开就静默登录，用户全程无感
					this.startLogin()
				} else if (!val && this.opened) {
					this.opened = false
					popup.close()
				}
			}
		},
		methods: {
			// 静默登录 + 自动取头像：ensureLogin 已登录时会直接放行（重开小程序 userInfo 是空的），
			// 所以这里统一再拉一次资料，头像由后端带回自动展示，不用用户点任何东西。
			// 失败提示已由 request 封装统一 toast，这里不重复提示
			startLogin() {
				// force：这是用户主动触发的登录弹窗，手动退出过也要能重新登录
				ensureLogin({ force: true }).then(() => {
					return getProfile()
				}).then((res) => {
					this.store.setUserInfo({
						id: res.id,
						name: res.name,
						avatar: res.avatar
					})
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).then(() => {
					// 登录成功就通知页面收掉弹窗、接着把那次加购做完，不用用户再点一次。
					// 用 store.isLogin 判断而不是塞进上面的 then：头像拉失败不影响登录态，token 拿到就算成功
					if (this.store.isLogin) {
						this.$emit('success')
					}
				})
			},
			// 关闭：只关弹窗，登录已经在打开时静默做完，不做其它动作
			handleClose() {
				this.$emit('close')
			},
			// 点遮罩：uni-popup 自己关掉后只发 change，这里同步内部状态并通知页面
			handleChange(e) {
				if (!e.show && this.opened) {
					this.opened = false
					this.$emit('close')
				}
			},
			// 换头像：chooseAvatar 给的是本地临时路径，必须上传后端换成可访问 URL
			onChooseAvatar(e) {
				const filePath = e.detail.avatarUrl
				if (!filePath) {
					return
				}
				// uploadAvatar 不走 request 封装，错误提示要自己来
				uploadAvatar(filePath).then((url) => {
					this.store.setAvatar(url)
					uni.showToast({
						title: '头像已更新',
						icon: 'success'
					})
				}).catch((err) => {
					uni.showToast({
						title: (err && (err.message || err.errMsg)) || '头像上传失败',
						icon: 'none'
					})
				})
			}
		}
	}
</script>

<style scoped>
	.popup-card {
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 560rpx;
		padding: 40rpx 30rpx 30rpx;
		background: #ffffff;
		border-radius: 20rpx;
	}

	.title {
		font-size: 32rpx;
		font-weight: bold;
		color: #333333;
	}

	.desc {
		margin-top: 12rpx;
		font-size: 24rpx;
		color: #999999;
	}

	/* 头像按钮：去掉小程序原生 button 的默认样式。margin 必须一次写全，只写 margin-top 会因 button 自带的 margin:auto 让头像偏移 */
	.avatar-btn {
		margin: 30rpx 0 0;
		padding: 0;
		background: none;
		border: none;
		line-height: 1;
	}

	.avatar {
		width: 140rpx;
		height: 140rpx;
		border-radius: 50%;
	}

	/* 无头像时的占位块 */
	.avatar-default {
		display: flex;
		align-items: center;
		justify-content: center;
		background: #e6e6e6;
		color: #999;
		font-size: 56rpx;
	}

	.avatar-tip {
		margin-top: 12rpx;
		font-size: 22rpx;
		color: #999999;
	}

	/* 关闭按钮：只负责关窗，样式保持朴素，不做成主按钮的样子 */
	.close-btn {
		width: 100%;
		margin-top: 40rpx;
		padding: 16rpx 0;
		font-size: 28rpx;
		text-align: center;
		color: #666666;
		background: #f5f5f5;
		border-radius: 40rpx;
	}
</style>

<style>
	/* uni-popup 自带 z-index:99，会被自定义 TabBar（999）和商品弹窗（1000）压在下面，这里抬到 1100 */
	.login-popup.uni-popup {
		z-index: 1100;
	}
</style>
