<template>
<view class="page">
		<CustomNavbar title="意见反馈" :show-back="true" />

		<!-- 反馈类型 -->
		<view class="card">
			<text class="section-title">反馈类型</text>
			<view class="type-row">
				<view class="type-chip" v-for="(item, index) in types" :key="item.code"
					:class="{ active: typeIndex === index }" @click="typeIndex = index">{{ item.label }}</view>
			</view>
		</view>

		<!-- 问题描述 -->
		<view class="card">
			<text class="section-title">问题描述</text>
			<textarea class="textarea" v-model="content" maxlength="200" auto-height
				placeholder="请描述你遇到的问题或建议（至少 5 个字）" placeholder-class="placeholder"></textarea>
			<text class="counter">{{ content.length }}/200</text>
		</view>

		<!-- 截图（选填，最多 6 张） -->
		<view class="card">
			<text class="section-title">上传截图（选填）</text>
			<view class="upload-grid">
				<view class="upload-item" v-for="(img, idx) in imageList" :key="img + idx">
					<image lazy-load class="upload-thumb" :src="mediaUrl(img)" mode="aspectFill"
						@click="previewImage(idx)"></image>
					<view class="upload-del" @click.stop="removeImage(idx)">×</view>
				</view>
				<view v-if="imageList.length < 6" class="upload-add" @click="chooseImage">
					<uni-icons type="plusempty" size="20" color="#cccccc"></uni-icons>
					<text class="upload-add-text">图片</text>
				</view>
			</view>
		</view>

		<!-- 联系方式（选填） -->
		<view class="card">
			<view class="contact-row">
				<text class="contact-label">联系方式</text>
				<input class="contact-input" v-model="contact" maxlength="50"
					placeholder="手机号/微信号/邮箱（选填）" placeholder-class="placeholder" />
			</view>
			<text class="contact-tip">方便我们与你联系，不会对外公开</text>
		</view>

		<text class="tips">你的每一条反馈我们都会认真阅读，感谢你的建议。</text>

		<!-- 提交 -->
		<view class="footer-bar">
			<view class="submit-btn" :class="{ disabled: submitting }" @click="submit">提交反馈</view>
		</view>
	</view>
</template>

<script>
	import { ensureLogin } from '../../../utils/auth.js'
	import { getProfile } from '../../../api/login.js'
	import { uploadMedia } from '../../../api/order.js'
	import { submitFeedback } from '../../../api/feedback.js'
	import { BASE_URL } from '../../../utils/server-api.js'

	export default {
		data() {
			return {
				// 反馈类型：code 走后端，label 只用于展示
				types: [{
						code: 'suggestion',
						label: '功能建议'
					},
					{
						code: 'product',
						label: '商品问题'
					},
					{
						code: 'order',
						label: '订单与物流'
					},
					{
						code: 'other',
						label: '其他'
					}
				],
				typeIndex: 0,
				content: '',
				imageList: [], // 已上传截图相对路径（最多 6 张）
				uploading: 0, // 正在上传的图片数量，>0 时不允许提交
				contact: '',
				submitting: false
			}
		},
		onShow() {
			// 联系方式默认带出账号手机号（选填可改），未绑定则留空
			ensureLogin()
				.then(() => getProfile())
				.then((profile) => {
					if (!this.contact && profile.phone) {
						this.contact = profile.phone
					}
				})
				.catch(() => {})
		},
		methods: {
			// 选择图片（最多 6 张，按剩余张数）
			chooseImage() {
				uni.chooseImage({
					count: 6 - this.imageList.length,
					sizeType: ['compressed'],
					sourceType: ['album', 'camera'],
					success: (res) => {
						(res.tempFilePaths || []).forEach(p => this.uploadOne(p))
					}
				})
			},
			// 上传单张截图：走 C 端上传接口，type=feedback 落 upload/image/feedback/
			uploadOne(filePath) {
				this.uploading++
				uni.showLoading({ title: '上传中...' })
				uploadMedia(filePath, 'feedback').then((url) => {
					this.imageList.push(url)
				}).catch(() => {
					// uploadMedia 不走 request 封装，错误提示在这里弹
					uni.showToast({
						title: '上传失败，请重试',
						icon: 'none'
					})
				}).finally(() => {
					this.uploading--
					if (this.uploading === 0) {
						uni.hideLoading()
					}
				})
			},
			removeImage(idx) {
				this.imageList.splice(idx, 1)
			},
			// 预览大图：相对路径要补 BASE_URL
			previewImage(idx) {
				const urls = this.imageList.map(u => this.mediaUrl(u))
				uni.previewImage({
					urls,
					current: urls[idx]
				})
			},
			// 后端返回相对路径 /image/feedback/xxx，补网关域名
			mediaUrl(url) {
				if (!url) {
					return ''
				}
				return url.indexOf('http') === 0 ? url : BASE_URL + url
			},
			submit() {
				if (this.submitting) {
					return
				}
				if (this.uploading > 0) {
					uni.showToast({
						title: '截图上传中，请稍候',
						icon: 'none'
					})
					return
				}
				const content = this.content.trim()
				if (content.length < 5) {
					uni.showToast({
						title: '请至少输入 5 个字',
						icon: 'none'
					})
					return
				}
				this.submitting = true
				submitFeedback({
					type: this.types[this.typeIndex].code,
					content,
					images: this.imageList.slice(),
					contact: this.contact.trim()
				}).then(() => {
					uni.showToast({
						title: '提交成功，感谢反馈',
						icon: 'success'
					})
					setTimeout(() => uni.navigateBack(), 800)
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).finally(() => {
					this.submitting = false
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
		padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
		background: #F8F8F8;
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

	/* 反馈类型：胶囊单选 */
	.type-row {
		display: flex;
		flex-wrap: wrap;
		margin-top: 20rpx;
	}

	.type-chip {
		margin: 0 16rpx 16rpx 0;
		padding: 10rpx 28rpx;
		font-size: 26rpx;
		color: #666666;
		background: #f5f5f5;
		border: 1rpx solid #f5f5f5;
		border-radius: 32rpx;
	}

	.type-chip.active {
		color: #FF6700;
		background: #FFF3E8;
		border-color: #FFD0B0;
	}

	/* 问题描述输入框 */
	.textarea {
		width: 100%;
		min-height: 200rpx;
		margin-top: 20rpx;
		padding: 20rpx;
		box-sizing: border-box;
		font-size: 26rpx;
		line-height: 1.6;
		color: #333333;
		background: #f8f8f8;
		border-radius: 12rpx;
	}

	.placeholder {
		color: #c0c0c0;
	}

	.counter {
		display: block;
		margin-top: 10rpx;
		text-align: right;
		font-size: 22rpx;
		color: #c0c0c0;
	}

	/* 截图上传：每行 3 张 */
	.upload-grid {
		display: flex;
		flex-wrap: wrap;
		margin-top: 20rpx;
	}

	.upload-item,
	.upload-add {
		position: relative;
		width: 150rpx;
		height: 100rpx;
		margin: 0 16rpx 16rpx 0;
		border-radius: 12rpx;
		overflow: hidden;
	}

	.upload-thumb {
		width: 100%;
		height: 100%;
	}

	.upload-add {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		box-sizing: border-box;
		border: 2rpx dashed #dddddd;
		background: #fafafa;
	}

	.upload-add-text {
		margin-top: 4rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.upload-del {
		position: absolute;
		top: 0;
		right: 0;
		width: 40rpx;
		height: 40rpx;
		line-height: 36rpx;
		text-align: center;
		font-size: 30rpx;
		color: #ffffff;
		background: rgba(0, 0, 0, 0.5);
		border-radius: 0 0 0 12rpx;
	}

	/* 联系方式 */
	.contact-row {
		display: flex;
		align-items: center;
		margin-top: 16rpx;
	}

	.contact-label {
		width: 140rpx;
		flex-shrink: 0;
		font-size: 26rpx;
		color: #666666;
	}

	.contact-input {
		flex: 1;
		font-size: 26rpx;
		color: #333333;
	}

	.contact-tip {
		display: block;
		margin-top: 14rpx;
		font-size: 22rpx;
		color: #999999;
	}

	.tips {
		display: block;
		margin: 28rpx 40rpx 0;
		text-align: center;
		font-size: 22rpx;
		line-height: 1.6;
		color: #999999;
	}

	/* 底部提交按钮 */
	.footer-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		padding: 16rpx 20rpx;
		padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
		background: #ffffff;
		box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.04);
	}

	.submit-btn {
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		font-size: 30rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 44rpx;
	}

	.submit-btn.disabled {
		opacity: 0.6;
	}
</style>
