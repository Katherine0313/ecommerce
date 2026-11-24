<template>
	<view class="page" :style="{ paddingBottom: keyboardHeight + 'px' }">
		<CustomNavbar title="专属客服" :show-back="true" />

		<!-- 顶部说明 + 人工客服入口 -->
		<view class="service-bar">
			<view class="service-info">
				<text class="service-title">{{ store.humanMode ? '人工客服' : '客服助手' }}</text>
				<text class="service-tip">{{ store.humanMode ? '人工客服接待中，请直接留言' : '常见问题自动答复，人工客服请点右侧按钮' }}</text>
			</view>
			<button v-if="!store.humanMode" class="contact-btn" @click="handoffToHuman('人工客服')" hover-class="none">联系人工客服</button>
		</view>

		<!-- 页面显著位置说明：自动答复内容仅供参考 -->
		<view class="ai-notice">本服务为常见问题自动答复，仅供参考，未解决请联系人工客服</view>

		<!-- 消息列表 -->
		<scroll-view class="msg-scroll" scroll-y :scroll-top="scrollTop">
			<view class="msg-list">
				<view
					v-for="msg in messages"
					:id="'msg-' + msg.id"
					:key="msg.id"
					class="msg-row"
					:class="msg.role"
				>
					<!-- 客服头像：机器人消息左侧 -->
					<image
						v-if="msg.role === 'bot'"
						class="msg-avatar"
						src="/static/zongjie.jpg"
						mode="aspectFill"
					></image>
					<view class="bubble">
						<text v-if="msg.typing" class="bubble-text typing-text">正在查询…</text>
						<text v-else class="bubble-text">{{ msg.text }}</text>
						<!-- 自动答复明确标注，避免用户误以为是真人 -->
						<text v-if="msg.ai && !msg.typing" class="ai-tag">自动答复</text>
						<text v-else-if="msg.human && !msg.typing" class="human-tag">人工客服</text>
						<view v-if="msg.contact || msg.feedback" class="bubble-actions">
							<button
								v-if="msg.contact"
								class="bubble-btn"
								@click="handoffToHuman(msg.handoffText)"
								hover-class="none"
							>联系人工客服</button>
							<button
								v-if="msg.feedback"
								class="bubble-btn ghost"
								@click="goFeedback"
							>去意见反馈</button>
						</view>
						<!-- 查到的商品：横向卡片，点进去看详情 -->
						<view v-if="msg.products && msg.products.length" class="prod-list">
							<view
								v-for="p in msg.products"
								:key="p.id"
								class="prod-card"
								@click="goProduct(p.id)"
							>
								<image class="prod-img" :src="prodImg(p)" mode="aspectFill" lazy-load></image>
								<text class="prod-name">{{ p.name }}</text>
								<view class="prod-bottom">
									<text class="prod-price">¥{{ prodPrice(p.price) }}</text>
									<text v-if="p.originalPrice" class="prod-origin">¥{{ prodPrice(p.originalPrice) }}</text>
								</view>
							</view>
						</view>
					</view>
					<!-- 用户头像：气泡右侧；没拉到微信头像时用「微」占位 -->
					<image
						v-if="msg.role === 'user' && userAvatar"
						class="msg-avatar msg-avatar-user"
						:src="userAvatar"
						mode="aspectFill"
					></image>
					<view
						v-else-if="msg.role === 'user'"
						class="msg-avatar msg-avatar-user avatar-default"
					>微</view>
				</view>
			</view>
		</scroll-view>

		<!-- 快捷问题 -->
		<scroll-view scroll-x class="quick-scroll">
			<view class="quick-list">
				<view
					v-for="q in quickQuestions"
					:key="q"
					class="quick-chip"
					:class="{ disabled: sending }"
					@click="ask(q)"
				>{{ q }}</view>
			</view>
		</scroll-view>

		<!-- 输入框：adjust-position=false 阻止微信自动推页面，
		     键盘高度靠 bindkeyboardheightchange 拿，避免输入框被顶到屏幕顶部 -->
		<view class="input-bar">
			<input
				class="input"
				v-model="inputText"
				placeholder="请输入你想咨询的问题"
				confirm-type="send"
				:adjust-position="false"
				@keyboardheightchange="onKeyboardHeightChange"
				@confirm="send"
			/>
			<view class="send-btn" :class="{ disabled: !inputText.trim() || sending }" @click="send">发送</view>
		</view>
	</view>
</template>

<script>
	import {
		useAiChatStore
	} from '../../../store/ai-chat.js'
	import {
		useUserStore
	} from '../../../store/user.js'
	import {
		goodsImgUrl,
		fmtMoney
	} from '../../../utils/format.js'

	export default {
		data() {
			return {
				// 与悬浮球共用同一份对话状态（Pinia），切换页面不丢
				store: useAiChatStore(),
				userStore: useUserStore(),
				inputText: '',
				// scroll-view 用 scroll-top 持续贴底
				scrollTop: 0,
				// 键盘高度（px）：input 的 adjust-position=false 关掉了微信自动推页面，
				// 改由 keyboardheightchange 事件拿，底部留出键盘高度
				keyboardHeight: 0,
				quickQuestions: ['什么时候发货', '怎么退换货', '运费怎么算', '查订单物流']
			}
		},
		computed: {
			messages() {
				return this.store.messages
			},
			sending() {
				return this.store.sending
			},
			userAvatar() {
				return this.userStore.userInfo.avatar || ''
			}
		},
		watch: {
			// 流式输出时消息内容持续变化，deep 监听保持贴底
			messages: {
				deep: true,
				handler() {
					this.scrollToBottom()
				}
			}
		},
		onLoad() {
			// 专属客服页是全屏聊天，关掉悬浮面板；会话本身继续共用
			this.store.close()
			this.store.ensureSession()
			// 从缓存恢复当天会话；人工会话会重新拉消息并续上轮询
			this.store.resumeSession()
		},
		methods: {
			// 小程序端：input 的 keyboardheightchange 事件
			onKeyboardHeightChange(e) {
				this.keyboardHeight = (e && e.detail && e.detail.height) || 0
			},
			send() {
				const text = this.inputText.trim()
				if (!text || this.store.sending) {
					return
				}
				this.store.send(text)
				this.inputText = ''
			},
			ask(text) {
				if (this.store.sending) {
					return
				}
				this.inputText = text
				this.send()
			},
			scrollToBottom() {
				this.$nextTick(() => {
					this.scrollTop += 10000
				})
			},
			handoffToHuman(text) {
				if (this.store.sending) return
				// 卡片按钮传 msg.handoffText：后端已落库时为空串，避免重复写入用户消息
				this.store.handoffToHuman(text)
			},
			goFeedback() {
				uni.navigateTo({
					url: '/pages/my/feedback/feedback'
				})
			},
			// 商品卡片：图走 goodsImgUrl 补全域名，价格统一两位小数
			prodImg(p) {
				return goodsImgUrl(p && p.mainImage, p && p.id)
			},
			prodPrice(v) {
				return fmtMoney(v)
			},
			goProduct(id) {
				if (!id) return
				uni.navigateTo({
					url: '/pages/order/detail?id=' + id
				})
			}
		}
	}
</script>

<style scoped>
	.page {
		display: flex;
		flex-direction: column;
		height: 100vh;
		/* padding-bottom 用来给键盘让位，border-box 保证不撑出屏幕 */
		box-sizing: border-box;
		overflow: hidden;
		background: #f8f8f8;
	}

	/* ═══ 顶部客服栏 ═══ */
	.service-bar {
		display: flex;
		align-items: center;
		justify-content: space-between;
		flex-shrink: 0;
		padding: 20rpx 24rpx;
		background: #ffffff;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.service-info {
		display: flex;
		flex-direction: column;
		flex: 1;
		margin-right: 16rpx;
	}

	.service-title {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}

	.service-tip {
		margin-top: 4rpx;
		font-size: 22rpx;
		color: #999999;
	}

	/* 小程序 button 默认带边框和背景，这里清掉做成胶囊按钮 */
	.contact-btn {
		flex-shrink: 0;
		margin: 0;
		padding: 0 24rpx;
		height: 60rpx;
		line-height: 60rpx;
		background: #ff6700;
		color: #ffffff;
		font-size: 24rpx;
		border-radius: 30rpx;
	}

	.contact-btn::after {
		border: none;
	}

	/* ═══ 消息列表 ═══ */
	.msg-scroll {
		flex: 1;
		height: 0;
	}

	.msg-list {
		padding: 24rpx;
	}

	.msg-row {
		display: flex;
		align-items: flex-start;
		margin-bottom: 24rpx;
	}

	.msg-row.user {
		justify-content: flex-end;
	}

	/* 客服头像 */
	.msg-avatar {
		flex-shrink: 0;
		width: 64rpx;
		height: 64rpx;
		margin-right: 16rpx;
		border-radius: 50%;
		background: #f0f0f0;
	}

	/* 用户头像在气泡右侧，间距反过来 */
	.msg-avatar-user {
		margin-right: 0;
		margin-left: 16rpx;
	}

	.avatar-default {
		display: flex;
		align-items: center;
		justify-content: center;
		color: #999999;
		font-size: 28rpx;
	}

	.bubble {
		max-width: 76%;
		padding: 20rpx 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
		box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
	}

	.msg-row.user .bubble {
		background: #ff6700;
	}

	.bubble-text {
		font-size: 28rpx;
		line-height: 1.6;
		color: #333333;
		word-break: break-all;
	}

	.msg-row.user .bubble-text {
		color: #ffffff;
	}

	/* 等待回复时的占位文案 */
	.typing-text {
		color: #999999;
	}

	/* 自动答复标识 */
	.ai-tag {
		display: inline-block;
		margin-top: 8rpx;
		padding: 2rpx 10rpx;
		border-radius: 6rpx;
		background: #FFF3E8;
		font-size: 20rpx;
		color: #B25B00;
	}

	.human-tag {
		display: inline-block;
		margin-top: 8rpx;
		padding: 2rpx 10rpx;
		border-radius: 6rpx;
		background: #E6F7FF;
		font-size: 20rpx;
		color: #1677FF;
	}

	/* 页面级自动答复提示条 */
	.ai-notice {
		flex-shrink: 0;
		padding: 10rpx 24rpx;
		background: #FFF3E8;
		color: #B25B00;
		font-size: 22rpx;
		text-align: center;
	}

	.bubble-actions {
		display: flex;
		flex-wrap: wrap;
		margin-top: 16rpx;
	}

	.bubble-btn {
		margin: 0 16rpx 0 0;
		padding: 0 20rpx;
		height: 56rpx;
		line-height: 56rpx;
		background: #ff6700;
		color: #ffffff;
		font-size: 24rpx;
		border-radius: 28rpx;
	}

	.bubble-btn::after {
		border: none;
	}

	.bubble-btn.ghost {
		background: #ffffff;
		color: #ff6700;
		border: 1rpx solid #ff6700;
	}

	/* 商品卡片：横向可滑动，避免长列表把气泡撑高 */
	.prod-list {
		margin-top: 16rpx;
		white-space: nowrap;
		overflow-x: auto;
	}

	.prod-card {
		display: inline-block;
		width: 200rpx;
		margin-right: 16rpx;
		vertical-align: top;
		white-space: normal;
	}

	.prod-img {
		width: 200rpx;
		height: 200rpx;
		border-radius: 12rpx;
		background: #f5f5f5;
	}

	.prod-name {
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		overflow: hidden;
		margin-top: 8rpx;
		font-size: 24rpx;
		line-height: 1.4;
		color: #333333;
	}

	.prod-bottom {
		display: flex;
		align-items: baseline;
		margin-top: 4rpx;
	}

	.prod-price {
		font-size: 26rpx;
		font-weight: 600;
		color: #ff6700;
	}

	.prod-origin {
		margin-left: 8rpx;
		font-size: 22rpx;
		color: #bbbbbb;
		text-decoration: line-through;
	}

	/* ═══ 快捷问题 ═══ */
	.quick-scroll {
		flex-shrink: 0;
		white-space: nowrap;
		background: #ffffff;
		border-top: 1rpx solid #f0f0f0;
	}

	.quick-list {
		display: flex;
		padding: 16rpx 24rpx;
	}

	.quick-chip {
		flex-shrink: 0;
		margin-right: 16rpx;
		padding: 10rpx 20rpx;
		background: #f5f5f5;
		color: #666666;
		font-size: 24rpx;
		border-radius: 24rpx;
	}

	.quick-chip.disabled {
		opacity: 0.5;
	}

	/* ═══ 输入框 ═══ */
	.input-bar {
		display: flex;
		align-items: center;
		flex-shrink: 0;
		padding: 16rpx 24rpx;
		padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
		background: #ffffff;
	}

	.input {
		flex: 1;
		height: 72rpx;
		padding: 0 24rpx;
		background: #f5f5f5;
		border-radius: 36rpx;
		font-size: 28rpx;
	}

	.send-btn {
		margin-left: 16rpx;
		padding: 0 28rpx;
		height: 72rpx;
		line-height: 72rpx;
		background: #ff6700;
		color: #ffffff;
		font-size: 28rpx;
		border-radius: 36rpx;
	}

	.send-btn.disabled {
		opacity: 0.4;
	}
</style>
