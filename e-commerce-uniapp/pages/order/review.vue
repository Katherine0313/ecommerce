<template>
<view class="page">
		<CustomNavbar title="填写评价" :show-back="true" />

		<!-- 优惠券未开通，先隐藏这条承诺；上线后把 utils/features.js 的 coupon 改成 true -->
		<view class="coupon-tip" v-if="features.coupon">参与晒单最多可得 <text class="coupon-amount">1.00 元</text> 优惠券</view>

		<view class="goods-card" v-if="product">
			<image lazy-load class="goods-img" :src="goodsImgUrl(product.image, product.productId)" mode="aspectFill"></image>
			<view class="goods-info">
				<text class="goods-name">{{ product.productName }}</text>
				<view class="goods-bottom">
					<text class="goods-price">¥{{ fmtMoney(product.salesPrice) }}</text>
					<text class="goods-qty">x{{ product.quantity }}</text>
				</view>
			</view>
		</view>

		<view class="rate-block">
			<text class="rate-label">描述相符</text>
			<view class="stars">
				<uni-icons
					v-for="i in 5"
					:key="i"
					:type="i <= rating ? 'star-filled' : 'star'"
					size="36"
					:color="i <= rating ? '#FF4E2A' : '#e0e0e0'"
					@click="rating = i"
				/>
			</view>
		</view>

		<view class="tags">
			<view
				v-for="tag in tagOptions"
				:key="tag"
				class="tag-item"
				:class="{ active: selectedTag === tag }"
				@click="selectedTag = tag"
			>{{ tag }}</view>
		</view>

		<view class="input-block">
			<textarea
				class="review-input"
				v-model="content"
				placeholder="填写评价，可以帮助更多想买的人哦～"
				:maxlength="500"
			/>
			<text class="input-tip">详细描述 (至少输入 5 个字)</text>
		</view>

		<!-- 晒单：图片（最多 6 张）+ 视频（1 个），选填，入口与预览同排流动 -->
		<view class="upload-block">
			<view class="upload-grid">
				<view class="upload-item" v-for="(img, idx) in imageList" :key="'img' + idx">
					<image lazy-load class="upload-thumb" :src="mediaUrl(img)" mode="aspectFill"></image>
					<view class="upload-del" @click="removeImage(idx)">×</view>
				</view>
				<view v-if="videoUrl" class="upload-item">
					<video class="upload-thumb" :src="mediaUrl(videoUrl)" object-fit="cover"></video>
					<view class="upload-del" @click="removeVideo">×</view>
				</view>
				<view v-if="imageList.length < 6" class="upload-add" @click="chooseImage">
					<uni-icons type="plusempty" size="40" color="#cccccc"></uni-icons>
					<text class="upload-add-text">图片</text>
				</view>
				<view v-if="!videoUrl" class="upload-add" @click="chooseVideo">
					<uni-icons type="videocam" size="40" color="#cccccc"></uni-icons>
					<text class="upload-add-text">视频</text>
				</view>
			</view>
		</view>

		<view class="footer-bar">
			<view class="submit-btn" @click="submit">提交评价</view>
		</view>
	</view>
</template>

<script>
	import {
		getOrder,
		submitReview,
		uploadMedia
	} from '../../api/order.js'
	import {
		goodsImgUrl,
		fmtMoney
	} from '../../utils/format.js'
	import {
		BASE_URL
	} from '../../utils/server-api.js'
	import {
		FEATURES
	} from '../../utils/features.js'

	export default {
		data() {
			return {
				features: FEATURES, // 未开通功能开关
				orderId: null,
				product: null,
				rating: 5,
				tagOptions: ['性价比高', '品质惊艳', '物美价廉', '发货很快'],
				selectedTag: '',
				content: '',
				imageList: [], // 已上传图片相对路径列表（最多 6 张）
				videoUrl: '' // 已上传视频相对路径（最多 1 个）
			}
		},
		onLoad(options) {
			this.orderId = options.orderId
			if (this.orderId) {
				this.loadProduct()
			}
		},
		methods: {
			goodsImgUrl,
			fmtMoney,
			loadProduct() {
				getOrder(this.orderId).then((order) => {
					this.product = (order.items && order.items[0]) || null
				}).catch(() => {})
			},
			// 选择图片（最多 6 张，按剩余张数）
			chooseImage() {
				uni.chooseImage({
					count: 6 - this.imageList.length,
					sizeType: ['compressed'],
					success: (res) => {
						const paths = res.tempFilePaths || []
						paths.forEach(p => this.uploadOne(p, 'image'))
					}
				})
			},
			// 选择视频（1 个）
			chooseVideo() {
				uni.chooseVideo({
					sourceType: ['album', 'camera'],
					maxDuration: 30,
					compressed: true,
					success: (res) => {
						this.uploadOne(res.tempFilePath, 'video')
					}
				})
			},
			// 上传单个图片/视频，成功后写入对应列表
			uploadOne(filePath, type) {
				uni.showLoading({ title: '上传中...' })
				uploadMedia(filePath).then((url) => {
					uni.hideLoading()
					if (type === 'video') {
						this.videoUrl = url
					} else {
						this.imageList.push(url)
					}
				}).catch(() => {
					uni.hideLoading()
					// uploadMedia 不走 request 封装，错误提示在这里统一弹
					uni.showToast({ title: '上传失败，请重试', icon: 'none' })
				})
			},
			removeImage(idx) {
				this.imageList.splice(idx, 1)
			},
			removeVideo() {
				this.videoUrl = ''
			},
			// 相对路径补 BASE_URL（image/video 的 src 都要绝对地址，小程序把 / 当包内路径）
			mediaUrl(url) {
				if (!url) return ''
				return url.indexOf('http') === 0 ? url : BASE_URL + url
			},
			submit() {
				const content = this.content.trim()
				if (content.length < 5) {
					uni.showToast({
						title: '请至少输入 5 个字',
						icon: 'none'
					})
					return
				}
				// 晒图/视频合并成媒体数组（图片 type=image，视频 type=video），JSON 存 images 字段
				const media = this.imageList.map(url => ({ url: url, type: 'image' }))
				if (this.videoUrl) {
					media.push({ url: this.videoUrl, type: 'video' })
				}
				submitReview(this.orderId, {
					productId: this.product ? this.product.productId : null,
					rating: this.rating,
					tag: this.selectedTag,
					content: content,
					images: media.length ? JSON.stringify(media) : null
				}).then(() => {
					uni.showToast({
						title: '评价已提交',
						icon: 'success'
					})
					setTimeout(() => {
						uni.redirectTo({
							url: '/pages/order/list'
						})
					}, 600)
				}).catch(() => {})
			}
		}
	}
</script>

<style scoped>
	.page {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
		background: #f5f5f5;
		padding-bottom: 160rpx;
	}

	.coupon-tip {
		text-align: center;
		font-size: 26rpx;
		color: #666666;
		padding: 20rpx;
	}

	.coupon-amount {
		color: #FF4E2A;
		font-weight: bold;
	}

	.goods-card {
		display: flex;
		align-items: center;
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.goods-img {
		width: 120rpx;
		height: 120rpx;
		border-radius: 12rpx;
		flex-shrink: 0;
	}

	.goods-info {
		flex: 1;
		min-width: 0;
		height: 120rpx;
		margin-left: 20rpx;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
	}

	.goods-name {
		font-size: 28rpx;
		color: #333333;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.goods-bottom {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}

	.goods-price {
		font-size: 30rpx;
		color: #f44;
	}

	.goods-qty {
		font-size: 26rpx;
		color: #999999;
	}

	.rate-block {
		display: flex;
		align-items: center;
		justify-content: space-between;
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.rate-label {
		font-size: 28rpx;
		color: #333333;
	}

	.stars {
		display: flex;
	}

	.tags {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx;
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.tag-item {
		padding: 8rpx 28rpx;
		border: 1rpx solid #dddddd;
		border-radius: 32rpx;
		font-size: 24rpx;
		color: #666666;
	}

	.tag-item.active {
		border-color: #FF4E2A;
		color: #FF4E2A;
		background: #fff5f2;
	}

	.input-block {
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.review-input {
		width: 100%;
		height: 160rpx;
		font-size: 28rpx;
		line-height: 1.6;
	}

	.input-tip {
		display: block;
		margin-top: 12rpx;
		font-size: 22rpx;
		color: #999999;
	}

	/* 晒单上传：图片网格 + 视频预览 */
	.upload-block {
		background: #ffffff;
		margin: 0 20rpx 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.upload-grid {
		display: flex;
		justify-content: space-between;
		padding: 0 50rpx;
	}

	.upload-item,
	.upload-add {
		position: relative;
		width: 200rpx;
		height: 120rpx;
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
		border: 2rpx dashed #dddddd;
		background: #fafafa;
	}

	.upload-add-text {
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
		background: rgba(0, 0, 0, 0.5);
		color: #ffffff;
		border-radius: 0 0 0 12rpx;
		font-size: 30rpx;
	}

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
		background: #FF4E2A;
		color: #ffffff;
		border-radius: 44rpx;
		font-size: 30rpx;
	}
</style>
