<template>
<view class="page">
		<CustomNavbar title="申请换货" :show-back="true" />

		<!-- 换货商品 -->
		<view class="goods-card">
			<view class="goods-item" v-for="item in items" :key="item.productId">
				<image lazy-load class="goods-img" :src="goodsImgUrl(item.image, item.productId)" mode="aspectFill"></image>
				<view class="goods-info">
					<text class="goods-name">{{ item.productName }}</text>
					<view class="goods-bottom">
						<text class="goods-price">¥{{ fmtMoney(item.salesPrice) }}</text>
						<text class="goods-qty">×{{ item.quantity }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 换货信息 -->
		<view class="info-card">
			<!-- 退货方式：点击弹出弹窗选择 -->
			<view class="form-item" @click="openReturnWay">
				<text class="form-label"><text class="star">*</text>退货方式</text>
				<view class="form-value">
					<text :class="{ placeholder: !returnWayText }">{{ returnWayText || '请选择' }}</text>
					<view class="arrow"></view>
				</view>
			</view>
			<picker mode="selector" :range="reasonNames" @change="onReasonChange">
				<view class="form-item">
					<text class="form-label"><text class="star">*</text>换货原因</text>
					<view class="form-value">
						<text :class="{ placeholder: !reason }">{{ reasonName || '请选择' }}</text>
						<view class="arrow"></view>
					</view>
				</view>
			</picker>
			<!-- 发货地址（商家寄新货地址） -->
			<view class="form-item-textarea">
				<text class="form-label"><text class="star">*</text>发货地址</text>
				<textarea class="form-textarea" v-model="shipAddress" placeholder="收货人、电话、地址" :maxlength="200" />
			</view>
		</view>

		<!-- 补充描述和凭证 -->
		<view class="desc-card">
			<textarea
				class="desc-input"
				v-model="remark"
				placeholder="补充描述，有助于商家更好的处理售后问题"
				:maxlength="200"
			/>
			<text class="desc-count">{{ remark.length }}/200</text>

			<view class="upload-row">
				<view class="upload-item" v-for="(img, idx) in imageList" :key="idx">
					<image lazy-load class="upload-thumb" :src="mediaUrl(img)" mode="aspectFill"></image>
					<view class="upload-del" @click="removeImage(idx)">×</view>
				</view>
				<view v-if="imageList.length < 3" class="upload-add" @click="uploadImage">
					<uni-icons type="camera" size="40" color="#cccccc"></uni-icons>
					<text class="upload-add-text">上传凭证（最多3张）</text>
				</view>
			</view>
		</view>

		<!-- 底部提交 -->
		<view class="footer-bar">
			<view class="submit-btn" @click="submitExchange">提交</view>
		</view>

		<!-- 退货方式弹窗 -->
		<ReturnWayPopup ref="returnWayPopup" @confirm="onReturnWayConfirm" />
	</view>
</template>

<script>
	import {
		getOrder,
		uploadMedia
	} from '../../api/order.js'
	import {
		createAfterSale,
		getAfterSaleStatus
	} from '../../api/after-sale.js'
	import {
		goodsImgUrl,
		fmtMoney
	} from '../../utils/format.js'
	import {
		BASE_URL
	} from '../../utils/server-api.js'
	import ReturnWayPopup from './return-way-popup.vue'

	export default {
		components: {
			ReturnWayPopup
		},
		data() {
			return {
				orderId: null,
				orderItemId: null,
				items: [],
				reason: '',
				shippedReasonMap: {}, // 已发货退款原因 code → 中文（换货只用已发货原因）
				returnWay: '', // 退货方式：pickup 上门取件 / self 自行寄回
				returnTimeSlot: '', // 取件时段：morning 上午 / afternoon 下午
				shipAddress: '', // 换货发货地址（商家寄新货地址）
				remark: '',
				imageList: [],
				submitting: false // 提交中，防连点重复创建售后单
			}
		},
		computed: {
			// 换货（exchange）只允许已发货/已完成订单，统一用已发货原因
			reasonOptions() {
				return Object.keys(this.shippedReasonMap).map(code => ({ code, name: this.shippedReasonMap[code] }))
			},
			reasonNames() {
				return this.reasonOptions.map(r => r.name)
			},
			reasonName() {
				const r = this.reasonOptions.find(r => r.code === this.reason)
				return r ? r.name : ''
			},
			// 退货方式展示文案
			returnWayText() {
				if (this.returnWay === 'pickup') {
					const slot = this.returnTimeSlot === 'morning' ? '09:00 – 13:00' : (this.returnTimeSlot === 'afternoon' ? '13:00 – 19:00' : '')
					return slot ? ('上门取件 · ' + slot) : '上门取件'
				}
				if (this.returnWay === 'self') return '自行寄回'
				return ''
			}
		},
		onLoad(options) {
			this.orderId = options.orderId
			this.orderItemId = options.orderItemId ? Number(options.orderItemId) : null
			this.loadConstants()
			if (this.orderId) {
				this.loadOrder()
			}
		},
		methods: {
			goodsImgUrl,
			fmtMoney,
			loadConstants() {
				getAfterSaleStatus().then((data) => {
					this.shippedReasonMap = (data && data.shippedReasonMap) || {}
				}).catch(() => {})
			},
			loadOrder() {
				getOrder(this.orderId).then((order) => {
					const all = order.items || []
					// 单品换该商品，整单换全部
					const item = this.orderItemId
						? all.find(i => i.orderItemId === this.orderItemId)
						: null
					this.items = item ? [item] : all
				}).catch(() => {})
			},
			onReasonChange(e) {
				const idx = Number(e.detail.value)
				this.reason = this.reasonOptions[idx].code
			},
			// 打开退货方式弹窗
			openReturnWay() {
				this.$refs.returnWayPopup.open()
			},
			// 弹窗确认后回填退货方式
			onReturnWayConfirm(payload) {
				this.returnWay = payload.returnWay
				this.returnTimeSlot = payload.returnTimeSlot
			},
			uploadImage() {
				uni.chooseImage({
					count: 3 - this.imageList.length,
					sizeType: ['compressed'],
					success: (res) => {
						const paths = res.tempFilePaths || []
						paths.forEach(p => this.uploadOne(p))
					}
				})
			},
			uploadOne(filePath) {
				uni.showLoading({ title: '上传中...' })
				uploadMedia(filePath, 'after-sale').then((url) => {
					uni.hideLoading()
					this.imageList.push(url)
				}).catch(() => {
					uni.hideLoading()
					uni.showToast({ title: '上传失败，请重试', icon: 'none' })
				})
			},
			removeImage(idx) {
				this.imageList.splice(idx, 1)
			},
			mediaUrl(url) {
				if (!url) return ''
				return url.indexOf('http') === 0 ? url : BASE_URL + url
			},
			submitExchange() {
				if (this.submitting) return
				if (!this.returnWay) {
					uni.showToast({ title: '请选择退货方式', icon: 'none' })
					return
				}
				if (!this.reason) {
					uni.showToast({ title: '请选择换货原因', icon: 'none' })
					return
				}
				if (!this.shipAddress || !this.shipAddress.trim()) {
					uni.showToast({ title: '请输入发货地址', icon: 'none' })
					return
				}
				this.submitting = true
				createAfterSale({
					orderId: this.orderId,
					orderItemId: this.orderItemId || null,
					afterSaleType: 'exchange',
					reason: this.reason,
					returnWay: this.returnWay,
					returnTimeSlot: this.returnTimeSlot || null,
					shipAddress: this.shipAddress.trim(),
					remark: this.remark,
					images: this.imageList
				}).then(() => {
					// 自行寄回提示回填物流，防止忘记填
					const msg = this.returnWay === 'self' ? '提交成功，请寄出后回填物流单号' : '提交成功'
					uni.showToast({ title: msg, icon: 'none' })
					setTimeout(() => {
						uni.switchTab({ url: '/pages/index/index' })
					}, 800)
				}).catch(() => {
					// 失败允许重试；成功保持 submitting=true，直到跳走
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
		background: #f5f5f5;
		padding-bottom: 160rpx;
	}

	/* 商品卡片 */
	.goods-card {
		background: #ffffff;
		margin: 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.goods-item {
		display: flex;
		align-items: center;
	}

	.goods-item + .goods-item {
		border-top: 1rpx solid #f5f5f5;
		margin-top: 20rpx;
		padding-top: 20rpx;
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
		margin-left: 20rpx;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
		height: 120rpx;
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
		color: #333333;
	}

	.goods-qty {
		font-size: 26rpx;
		color: #666666;
	}

	/* 换货信息 */
	.info-card {
		background: #ffffff;
		margin: 0 20rpx;
		border-radius: 16rpx;
		padding: 0 24rpx;
	}

	.form-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 100rpx;
	}

	.form-item + .form-item {
		border-top: 1rpx solid #f5f5f5;
	}

	.form-label {
		font-size: 28rpx;
		color: #333333;
	}

	.star {
		color: #ff4d4f;
		margin-right: 4rpx;
	}

	.form-value {
		display: flex;
		align-items: center;
	}

	.form-value .placeholder {
		color: #999999;
	}

	.arrow {
		margin-left: 12rpx;
		width: 16rpx;
		height: 16rpx;
		border-top: 2rpx solid #cccccc;
		border-right: 2rpx solid #cccccc;
		transform: rotate(45deg);
	}

	/* 退货地址输入（多行：收货人、电话、地址） */
	.form-item-textarea {
		display: flex;
		align-items: flex-start;
		padding: 28rpx 0;
		border-top: 1rpx solid #f5f5f5;
	}

	.form-textarea {
		flex: 1;
		min-height: 120rpx;
		margin-left: 40rpx;
		font-size: 28rpx;
		text-align: right;
	}

	/* 补充描述和凭证 */
	.desc-card {
		background: #ffffff;
		margin: 20rpx;
		border-radius: 16rpx;
		padding: 24rpx;
	}

	.desc-input {
		width: 100%;
		height: 160rpx;
		font-size: 28rpx;
		line-height: 1.6;
	}

	.desc-count {
		display: block;
		text-align: right;
		font-size: 22rpx;
		color: #999999;
		margin-top: 8rpx;
	}

	.upload-row {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx;
		margin-top: 20rpx;
	}

	.upload-item,
	.upload-add {
		position: relative;
		width: 160rpx;
		height: 160rpx;
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
		gap: 8rpx;
		border: 2rpx dashed #dddddd;
		background: #fafafa;
	}

	.upload-add-text {
		font-size: 20rpx;
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

	/* 底部提交 */
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
		background: #FF6700;
		color: #ffffff;
		border-radius: 44rpx;
		font-size: 30rpx;
	}
</style>
