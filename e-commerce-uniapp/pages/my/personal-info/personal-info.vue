﻿﻿﻿﻿﻿<template>
<view class="page">
		<CustomNavbar title="个人信息" :show-back="true" />

		<!-- 已退出登录：只保留重新登录入口 -->
		<view v-if="!isLogin" class="card empty-card">
			<text class="empty-text">你已退出登录</text>
			<view class="login-btn" @click="handleLogin">微信登录</view>
		</view>

		<block v-else>
			<!-- 资料卡：头像 / 昵称 / 手机号 -->
			<view class="card">
				<!-- 头像：chooseAvatar 必须由用户点击触发，所以包在 button 里 -->
				<view class="row">
					<text class="label">头像</text>
					<button open-type="chooseAvatar" @chooseavatar="onChooseAvatar" class="avatar-btn">
						<image v-if="form.avatar" :src="form.avatar" class="avatar"></image>
						<view v-else class="avatar avatar-default">微</view>
						<uni-icons type="right" size="14" color="#c0c0c0"></uni-icons>
					</button>
				</view>

				<!-- 昵称：失焦或键盘「完成」时保存 -->
				<view class="row">
					<text class="label">昵称</text>
					<input class="input" v-model="form.name" maxlength="32" placeholder="点击设置昵称"
						placeholder-class="placeholder" confirm-type="done" @blur="saveName" @confirm="saveName" />
					<uni-icons type="right" size="14" color="#c0c0c0"></uni-icons>
				</view>

				<!-- 手机号：已绑定显示完整号码；未绑定走微信手机号授权 -->
				<view class="row">
					<text class="label">手机号</text>
					<text v-if="form.phone" class="value">{{ form.phone }}</text>
					<button v-else open-type="getPhoneNumber" @getphonenumber="onGetPhone" class="bind-btn">去绑定</button>
				</view>
			</view>

			<!-- 只读信息 -->
			<view class="card">
				<view class="row last">
					<text class="label">注册时间</text>
					<text class="value">{{ formatCreateTime(createTime) }}</text>
				</view>
			</view>

			<!-- 退出登录 -->
			<view class="logout-card" @click="handleLogout">退出登录</view>

			<!-- 注销账号：不可恢复，放在退出登录下面 -->
			<view class="logout-card deactivate-card" @click="handleDeactivate">注销账号</view>
		</block>
	</view>
</template>

<script>
	import { useUserStore } from '../../../store/user.js'
	import { ensureLogin, login, logout, isManualLogout } from '../../../utils/auth.js'
	import { getProfile, uploadAvatar, saveAvatar, updateName, bindPhone, deactivateAccount } from '../../../api/login.js'
	import { parseOrderTime } from '../../../utils/format.js'

	export default {
		data() {
			return {
				store: useUserStore(), // 用户信息统一走 Pinia，改完同步回「我的」页
				form: {
					name: '',
					avatar: '',
					phone: ''
				},
				createTime: null, // 注册时间原样存后端返回值，展示时再格式化
				originalName: '', // 已保存的昵称，用于失焦时判断是否真的改了
				savingName: false // confirm + blur 可能连着触发，防止重复提交
			}
		},
		computed: {
			// token 存在即视为登录中（退出登录会清掉 token）
			isLogin() {
				return this.store.isLogin
			}
		},
		onShow() {
			// 手动退出后不触发静默登录，保持「未登录」状态
			if (isManualLogout()) {
				this.clearProfile()
				return
			}
			this.loadProfile()
		},
		methods: {
			// 拉取资料：先确保登录，再回填表单和 Pinia
			loadProfile() {
				ensureLogin()
					.then(() => getProfile())
					.then((profile) => {
						this.form = {
							name: profile.name || '',
							avatar: profile.avatar || '',
							phone: profile.phone || ''
						}
						this.originalName = this.form.name
						this.createTime = profile.createTime
						this.store.setUserInfo({
							id: profile.id,
							name: profile.name,
							avatar: profile.avatar
						})
					})
					.catch(() => {})
			},
			// 清空页面上的资料（退出登录后调用）
			clearProfile() {
				this.form = {
					name: '',
					avatar: '',
					phone: ''
				}
				this.originalName = ''
				this.createTime = null
			},
			// 退出登录：二次确认后清本地登录态，并记住「手动退出」
			handleLogout() {
				uni.showModal({
					title: '退出登录',
					content: '退出后将无法查看订单、收藏等信息，确定退出吗？',
					confirmColor: '#FF6700',
					success: (res) => {
						if (!res.confirm) {
							return
						}
						logout()
						this.clearProfile()
						uni.showToast({
							title: '已退出登录',
							icon: 'none'
						})
					}
				})
			},
			// 注销账号：不可恢复，二次确认后调后端注销并清本地登录态
			handleDeactivate() {
				uni.showModal({
					title: '注销账号',
					content: '注销后账号将被永久删除，订单、收藏、会员权益等数据将无法找回，且不可恢复。确定注销吗？',
					confirmColor: '#ff4d4f',
					confirmText: '确定注销',
					cancelText: '再想想',
					success: (res) => {
						if (!res.confirm) {
							return
						}
						deactivateAccount().then(() => {
							logout()
							this.clearProfile()
							uni.showToast({
								title: '账号已注销',
								icon: 'none'
							})
							// 回到「我的」页，此时已退出登录，页面会自动显示登录入口
							uni.switchTab({ url: '/pages/my/my' })
						}).catch(() => {
							// 请求失败提示已在 request 封装里统一处理
						})
					}
				})
			},
			// 重新登录：用户主动点「微信登录」时清除手动退出标记
			handleLogin() {
				login().then(() => {
					this.loadProfile()
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
					.then((url) => saveAvatar(url))      // 拿到 URL 后存进 user.avatar
					.then((url) => {
						this.form.avatar = url
						this.store.setAvatar(url)
						uni.showToast({ title: '头像已更新', icon: 'success' })
					})
					.catch((err) => {
						uni.showToast({
							title: (err && err.message) || '头像上传失败',
							icon: 'none'
						})
					})
			},
			// 保存昵称：为空回退，没改动不发请求
			saveName() {
				const name = (this.form.name || '').trim()
				if (!name) {
					this.form.name = this.originalName
					uni.showToast({
						title: '昵称不能为空',
						icon: 'none'
					})
					return
				}
				if (name === this.originalName || this.savingName) {
					this.form.name = name
					return
				}
				this.savingName = true
				updateName(name).then((saved) => {
					this.form.name = saved
					this.originalName = saved
					// 保留 id/avatar，只替换昵称
					this.store.setUserInfo({
						...this.store.userInfo,
						name: saved
					})
					uni.showToast({
						title: '昵称已更新',
						icon: 'success'
					})
				}).catch(() => {
					this.form.name = this.originalName
				}).finally(() => {
					this.savingName = false
				})
			},
			// 绑定手机号：getPhoneNumber 授权拿 code，后端换号码并写库
			onGetPhone(e) {
				const code = e.detail && e.detail.code
				if (!code) {
					uni.showToast({
						title: '已取消手机号授权',
						icon: 'none'
					})
					return
				}
				bindPhone(code).then((phone) => {
					this.form.phone = phone
					uni.showToast({
						title: '手机号已绑定',
						icon: 'success'
					})
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				})
			},
			// 注册时间展示：兼容 Jackson 3 的 ISO 字符串 / 数组两种序列化
			formatCreateTime(t) {
				if (!t) {
					return '—'
				}
				if (typeof t === 'string') {
					return t.replace('T', ' ').slice(0, 16)
				}
				const ms = parseOrderTime(t)
				if (!ms) {
					return '—'
				}
				const d = new Date(ms)
				const pad = (n) => String(n).padStart(2, '0')
				return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
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
		padding-bottom: env(safe-area-inset-bottom);
		background: #F8F8F8;
	}

	/* 通用白卡片 */
	.card {
		margin: 20rpx 20rpx 0;
		padding: 0 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	/* 未登录空态 */
	.empty-card {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 60rpx 24rpx;
	}

	.empty-text {
		font-size: 28rpx;
		color: #999999;
	}

	.login-btn {
		margin-top: 32rpx;
		height: 72rpx;
		line-height: 72rpx;
		padding: 0 80rpx;
		font-size: 28rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 36rpx;
	}

	/* 退出登录：白卡片 + 红字，放在资料卡下方 */
	.logout-card {
		margin: 20rpx 20rpx 0;
		height: 96rpx;
		line-height: 96rpx;
		text-align: center;
		font-size: 28rpx;
		color: #ff4d4f;
		background: #ffffff;
		border-radius: 16rpx;
	}

	/* 注销账号：比退出登录更危险，字色加深并加边框区分 */
	.deactivate-card {
		color: #d9363e;
		border: 1rpx solid #ffccc7;
		margin-bottom: 20rpx;
	}

	/* 一行资料：左侧标签，右侧内容，行间细分隔线 */
	.row {
		display: flex;
		align-items: center;
		min-height: 100rpx;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.row.last {
		border-bottom: none;
	}

	.label {
		width: 140rpx;
		flex-shrink: 0;
		font-size: 28rpx;
		color: #333333;
	}

	/* 头像按钮：去掉 button 默认样式，只当点击区域用 */
	.avatar-btn {
		display: flex;
		align-items: center;
		flex: 1;
		justify-content: flex-end;
		margin: 0;
		padding: 0;
		background: none;
		border: none;
		line-height: 1;
	}

	.avatar-btn::after,
	.bind-btn::after {
		border: none;
	}

	.avatar {
		width: 88rpx;
		height: 88rpx;
		margin-right: 12rpx;
		border: 2rpx solid #f0f0f0;
		border-radius: 50%;
	}

	/* 无头像时的占位块 */
	.avatar-default {
		display: flex;
		align-items: center;
		justify-content: center;
		background: #e6e6e6;
		color: #999;
		font-size: 32rpx;
	}

	.input {
		flex: 1;
		text-align: right;
		font-size: 28rpx;
		color: #333333;
	}

	.placeholder {
		color: #c0c0c0;
	}

	.value {
		flex: 1;
		text-align: right;
		font-size: 28rpx;
		color: #666666;
	}

	/* 「去绑定」小胶囊按钮 */
	.bind-btn {
		height: 56rpx;
		line-height: 56rpx;
		margin: 0 0 0 auto;
		padding: 0 24rpx;
		font-size: 24rpx;
		color: #FF6700;
		background: #FFF3E8;
		border-radius: 28rpx;
	}
</style>
