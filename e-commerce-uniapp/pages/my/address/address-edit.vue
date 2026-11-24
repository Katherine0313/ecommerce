<template>
<view class="page">
		<CustomNavbar :title="editingId ? '编辑地址' : '新增地址'" :show-back="true" />

		<view class="form">
			<view class="form-item">
				<text class="label">收货人</text>
				<input class="input" placeholder="请输入收货人姓名" v-model="form.consignee" />
			</view>
			<view class="form-item">
				<text class="label">手机号</text>
				<input class="input" type="number" maxlength="11" placeholder="请输入11位手机号" v-model="form.phone" />
			</view>
			<view class="form-item">
				<text class="label">所在地区</text>
				<!-- 微信原生省市区级联选择器，数据微信内置 -->
				<picker mode="region" :value="regionArray" @change="onRegionChange">
					<view class="input picker" :class="{ placeholder: !form.region }">
						{{ form.region ? formatRegion(form.region) : '请选择省市区' }}
					</view>
				</picker>
			</view>
			<view class="form-item">
				<text class="label">详细地址</text>
				<input class="input" placeholder="街道、门牌号、小区、楼栋等" v-model="form.detailAddress" />
			</view>

			<view class="default-switch">
				<text class="switch-label">设为默认地址</text>
				<u-switch :model-value="form.isDefault === 1" @update:model-value="onDefaultChange"
					active-color="#FF6700" inactive-color="rgba(150, 150, 150, 0.2)" size="20" />
			</view>

			<view class="save-btn" @click="handleSave">保存</view>
			<view v-if="editingId" class="delete-btn" @click="handleDelete">删除地址</view>
		</view>
	</view>
</template>

<script>
	import { listAddress, addAddress, updateAddress, deleteAddress } from '../../../api/address.js'

	export default {
		data() {
			return {
				editingId: null, // 编辑的地址 id，null 表示新增
				form: {
					consignee: '',
					phone: '',
					region: '',
					detailAddress: '',
					isDefault: 0
				},
				regionArray: [] // picker 回显用的省市区数组
			}
		},
		onLoad(options) {
			if (options.id) {
				this.editingId = Number(options.id)
				this.loadAddress(this.editingId)
			}
		},
		methods: {
			// 后端没有单条地址接口，用列表按 id 找来回显
			loadAddress(id) {
				listAddress().then((list) => {
					const item = (list || []).find(a => a.id === id)
					if (item) {
						this.form = {
							consignee: item.consignee,
							phone: item.phone,
							region: item.region,
							detailAddress: item.detailAddress,
							isDefault: item.isDefault
						}
						this.regionArray = item.region ? item.region.split(' ') : []
					}
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				})
			},
			// region 存「省 市 区」空格分隔，展示时去掉空格更紧凑
			formatRegion(region) {
				return (region || '').replace(/\s+/g, '')
			},
			// 省市区 picker 选中
			onRegionChange(e) {
				this.regionArray = e.detail.value
				this.form.region = e.detail.value.join(' ')
			},
			// 默认开关切换：u-switch 给 boolean，转 0/1 存
			onDefaultChange(val) {
				this.form.isDefault = val ? 1 : 0
			},
			// 前端校验（后端仍有权威校验兜底）
			validate() {
				if (!this.form.consignee.trim()) {
					uni.showToast({ title: '请填写收货人', icon: 'none' })
					return false
				}
				if (!/^\d{11}$/.test(this.form.phone.trim())) {
					uni.showToast({ title: '请填写正确的11位手机号', icon: 'none' })
					return false
				}
				if (!this.form.region) {
					uni.showToast({ title: '请选择省市区', icon: 'none' })
					return false
				}
				if (!this.form.detailAddress.trim()) {
					uni.showToast({ title: '请填写详细地址', icon: 'none' })
					return false
				}
				return true
			},
			// 保存（新增或修改）
			handleSave() {
				if (!this.validate()) {
					return
				}
				const data = {
					consignee: this.form.consignee.trim(),
					phone: this.form.phone.trim(),
					region: this.form.region,
					detailAddress: this.form.detailAddress.trim(),
					isDefault: this.form.isDefault
				}
				const req = this.editingId ? updateAddress(this.editingId, data) : addAddress(data)
				req.then(() => {
					uni.showToast({ title: '保存成功', icon: 'success' })
					setTimeout(() => uni.navigateBack(), 500)
				}).catch(() => {})
			},
			// 删除地址
			handleDelete() {
				uni.showModal({
					title: '删除地址',
					content: '删除后不可恢复，确定删除吗？',
					confirmColor: '#FF6700',
					success: (res) => {
						if (res.confirm) {
							deleteAddress(this.editingId).then(() => {
								uni.showToast({ title: '已删除', icon: 'success' })
								setTimeout(() => uni.navigateBack(), 500)
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
	}

	/* ═══ 表单 ═══ */
	.form {
		margin-top: 20rpx;
		background: #ffffff;
		border-radius: 16rpx;
		margin: 20rpx;
		padding: 0 30rpx;
	}

	.form-item {
		display: flex;
		align-items: center;
		padding: 28rpx 0;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.form-item:last-of-type {
		border-bottom: none;
	}

	.label {
		flex-shrink: 0;
		width: 140rpx;
		font-size: 28rpx;
		color: #333333;
	}

	.input {
		flex: 1;
		height: 72rpx;
		padding: 0 20rpx;
		font-size: 28rpx;
		color: #333333;
	}

	.picker {
		display: flex;
		align-items: center;
	}

	.picker.placeholder {
		color: #999999;
	}

	/* ═══ 默认开关 ═══ */
	.default-switch {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 24rpx 0;
	}

	.switch-label {
		font-size: 28rpx;
		color: #333333;
	}

	/* ═══ 保存 / 删除 ═══ */
	.save-btn {
		margin: 20rpx 0 0;
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		font-size: 32rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 44rpx;
	}

	.delete-btn {
		margin: 20rpx 0;
		height: 88rpx;
		line-height: 88rpx;
		text-align: center;
		font-size: 30rpx;
		color: #ff4d4f;
		background: #ffffff;
		border-radius: 44rpx;
		border: 1rpx solid #ff4d4f;
	}
</style>
