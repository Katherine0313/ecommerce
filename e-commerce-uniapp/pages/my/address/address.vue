<template>
<view class="page">
		<CustomNavbar title="收货地址" :show-back="true" />

		<!-- 地址列表 -->
		<view v-if="addresses.length" class="addr-list">
			<view class="addr-card" v-for="item in addresses" :key="item.id" @click="goEdit(item)">
				<view class="addr-main">
					<view class="name-row">
						<text class="name">{{ item.consignee }}</text>
						<text class="phone">{{ item.phone }}</text>
						<text v-if="item.isDefault === 1" class="default-tag">默认</text>
					</view>
					<view class="addr-text">{{ formatRegion(item.region) }} {{ item.detailAddress }}</view>
				</view>
				<view class="action-row">
					<view class="checkbox-wrap" @click.stop="toggleDefault(item)">
						<text class="check-icon">{{ item.isDefault === 1 ? '☑' : '☐' }}</text>
						<text class="check-text" :class="{ active: item.isDefault === 1 }">默认地址</text>
					</view>
					<view class="right-op">
						<view class="op-item" @click.stop="goEdit(item)">
							<text class="op-icon">🖊</text>
							<text>编辑</text>
						</view>
						<view class="op-item" @click.stop="handleDelete(item)">
							<text class="op-icon">🗑</text>
							<text>删除</text>
						</view>
					</view>
				</view>
			</view>
		</view>

		<!-- 空态 -->
		<view v-else-if="loaded" class="empty">
			<text class="empty-text">还没有收货地址，点击下方新增</text>
		</view>

		<!-- 底部新增按钮 -->
		<view class="bottom-bar">
			<view class="btn-add" @click="goAdd">新增地址</view>
		</view>
	</view>
</template>

<script>
	import { listAddress, deleteAddress, updateAddress } from '../../../api/address.js'

	export default {
		data() {
			return {
				addresses: [],
				loaded: false
			}
		},
		onShow() {
			this.fetchList()
		},
		methods: {
			// 拉地址列表
			fetchList() {
				listAddress().then((list) => {
					this.addresses = list || []
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).finally(() => {
					this.loaded = true
				})
			},
			// region 存「省 市 区」空格分隔，展示时去掉空格更紧凑
			formatRegion(region) {
				return (region || '').replace(/\s+/g, '')
			},
			// 新增地址
			goAdd() {
				uni.navigateTo({ url: '/pages/my/address/address-edit' })
			},
			// 编辑地址（点卡片本身或编辑按钮都进）
			goEdit(item) {
				uni.navigateTo({ url: '/pages/my/address/address-edit?id=' + item.id })
			},
			// 设为默认地址
			toggleDefault(item) {
				if (item.isDefault === 1) {
					uni.showToast({ title: '已是默认地址', icon: 'none' })
					return
				}
				updateAddress(item.id, {
					consignee: item.consignee,
					phone: item.phone,
					region: item.region,
					detailAddress: item.detailAddress,
					isDefault: 1
				}).then(() => {
					uni.showToast({ title: '已设为默认', icon: 'success' })
					this.fetchList()
				}).catch(() => {})
			},
			// 删除地址
			handleDelete(item) {
				uni.showModal({
					title: '删除地址',
					content: '删除后不可恢复，确定删除吗？',
					confirmColor: '#FF6700',
					success: (res) => {
						if (res.confirm) {
							deleteAddress(item.id).then(() => {
								uni.showToast({ title: '已删除', icon: 'success' })
								this.fetchList()
							}).catch(() => {})
						}
					}
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
		padding-bottom: 140rpx;
	}

	/* ═══ 地址列表 ═══ */
	.addr-list {
		padding: 20rpx;
	}

	.addr-card {
		display: flex;
		flex-direction: column;
		background: #ffffff;
		border-radius: 16rpx;
		padding: 28rpx 24rpx;
		margin-bottom: 20rpx;
	}

	.addr-main {
		flex: 1;
		min-width: 0;
		border-bottom: 2rpx solid rgba(150,150,150,0.2);
		padding-bottom: 10rpx;
	}

	.name-row {
		display: flex;
		align-items: center;
		gap: 16rpx;
	}

	.name {
		font-size: 32rpx;
		font-weight: bold;
		color: #333333;
	}

	.phone {
		font-size: 28rpx;
		color: #666666;
	}

	.default-tag {
		padding: 4rpx 12rpx;
		font-size: 22rpx;
		color: #FF6700;
		background: #FFF3E8;
		border-radius: 6rpx;
	}

	.addr-text {
		margin-top: 12rpx;
		font-size: 28rpx;
		color: #666666;
		line-height: 1.5;
	}

	.action-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-top: 20rpx;
	}

	.checkbox-wrap {
		display: flex;
		align-items: center;
		gap: 8rpx;
	}

	.check-icon {
		font-size: 30rpx;
		color: #999999;
	}

	.check-text {
		font-size: 26rpx;
		color: #999999;
	}

	.check-text.active {
		color: #FF6700;
	}

	.right-op {
		display: flex;
		gap: 40rpx;
	}

	.op-item {
		display: flex;
		align-items: center;
		gap: 8rpx;
		font-size: 26rpx;
		color: #666666;
	}

	.op-icon {
		font-size: 26rpx;
	}

	/* ═══ 空态 ═══ */
	.empty {
		display: flex;
		align-items: center;
		justify-content: center;
		padding-top: 200rpx;
	}

	.empty-text {
		font-size: 28rpx;
		color: #999999;
	}

	/* ═══ 底部按钮 ═══ */
	.bottom-bar {
		position: fixed;
		bottom: 0;
		left: 0;
		width: 100%;
		box-sizing: border-box;
		padding: 20rpx 30rpx;
		padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
		background: #ffffff;
	}

	.btn-add {
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		font-size: 32rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 44rpx;
	}
</style>
