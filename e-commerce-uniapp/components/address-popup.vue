<template>
	<!-- 收货地址弹窗（全局组件）：底部弹出，含「地址列表 + 新增/编辑表单」两个视图。
	     点某条地址 emit select（下单用）；增删改后 emit saved 通知页面刷新 -->
	<uni-popup ref="popup" class="address-popup" type="bottom" @change="handleChange">
		<view class="popup-body">
			<!-- 标题栏 -->
			<view class="header">
				<text class="title">{{ mode === 'list' ? '收货地址' : (editingId ? '编辑地址' : '新增地址') }}</text>
				<text class="close" @click="handleClose">×</text>
			</view>

			<!-- 列表视图 -->
			<view v-if="mode === 'list'" class="list-view">
				<view class="address-item" v-for="item in addresses" :key="item.id" @click="handleSelect(item)">
					<view class="item-main">
						<view class="consignee-info">
							<text class="consignee">{{ item.consignee }}</text>
							<text class="phone">{{ item.phone }}</text>
						</view>
						<view class="consignee-info">
							<text class="region">{{ formatRegion(item.region) }}</text>
							<text class="detail">{{ item.detailAddress }}</text>
						</view>

					</view>
					<view class="item-side">
						<text v-if="item.isDefault === 1" class="default-tag">默认</text>
						<text class="edit" @click.stop="handleEdit(item)">编辑</text>
					</view>
				</view>

				<!-- 空列表：整行可点击进入新增 -->
				<view v-if="!addresses.length" class="empty" @click="handleAdd">还没有收货地址，点击新增</view>

			</view>

			<!-- 编辑视图 -->
			<view v-else class="edit-view">
				<view class="form-item">
					<text class="label">收货人</text>
					<input class="input" placeholder="请输入收货人姓名" v-model="form.consignee" />
				</view>
				<view class="form-item">
					<text class="label">手机号</text>
					<input class="input" type="number" placeholder="请输入11位手机号" v-model="form.phone" />
				</view>
				<view class="form-item">
					<text class="label">所在地区</text>
					<!-- 微信原生省市区级联选择器，数据微信内置 -->
					<picker mode="region" :value="regionArray" @change="onRegionChange">
						<view class="input picker" :class="{ placeholder: !form.region }">{{ form.region ? formatRegion(form.region) : '请选择省市区' }}
						</view>
					</picker>
				</view>
				<view class="form-item">
					<text class="label">详细地址</text>
					<input class="input" placeholder="街道、门牌号、小区、楼栋等" v-model="form.detailAddress" />
				</view>

				<view class="default-switch">
					<text class="switch-label">设为默认地址</text>
					<!-- uview-ui 开关：滑块自带平滑过渡动画，打开在右、关闭在左 -->
					<u-switch :model-value="form.isDefault === 1" @update:model-value="onDefaultChange"
						active-color="#FF6700" inactive-color="rgba(150, 150, 150, 0.2)" size="20" />
				</view>

				<view class="save-btn" :class="{ loading: saving }" @click="handleSave">{{ saving ? '保存中…' : '保存' }}
				</view>
				<view v-if="editingId" class="delete-btn" @click="handleDelete">删除地址</view>
			</view>

			<!-- 新增入口：固定在弹窗底部，已有地址时展示 -->
			<view v-if="mode === 'list' && addresses.length" class="add-btn" @click="handleAdd">新增收货地址</view>
		</view>
	</uni-popup>
</template>

<script>
	import {
		listAddress,
		addAddress,
		updateAddress,
		deleteAddress
	} from '../api/address.js'

	export default {
		name: 'AddressPopup',
		props: {
			// 是否展示弹窗，由页面控制（和其它弹窗组件一致）
			show: {
				type: Boolean,
				default: false
			}
		},
		data() {
			return {
				opened: false, // 内部开关，配合页面 show 避免重复 open/close
				mode: 'list', // list 列表 | edit 编辑
				editingId: null, // 编辑的地址 id，null 表示新增
				addresses: [], // 地址列表
				form: {
					consignee: '',
					phone: '',
					region: '',
					detailAddress: '',
					isDefault: 0
				},
				regionArray: [], // picker 回显用的省市区数组
				saving: false // 保存请求中
			}
		},
		watch: {
			show(val) {
				const popup = this.$refs.popup
				if (!popup) {
					return
				}
				if (val && !this.opened) {
					this.opened = true
					this.mode = 'list'
					this.editingId = null
					this.resetForm()
					this.fetchList()
					popup.open()
				} else if (!val && this.opened) {
					this.opened = false
					popup.close()
				}
			}
		},
		methods: {
			// 拉地址列表
			fetchList() {
				listAddress().then((list) => {
					this.addresses = list || []
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				})
			},
			// 点遮罩关闭：uni-popup 自己关掉后只发 change，这里同步状态并通知页面
			handleChange(e) {
				if (!e.show && this.opened) {
					this.opened = false
					this.$emit('close')
				}
			},
			// 点右上角叉叉
			handleClose() {
				this.$emit('close')
			},
			// 点某条地址：选中（下单用），通知页面并关弹窗
			handleSelect(item) {
				this.$emit('select', item)
				this.$emit('close')
			},
			// 新增：清空表单进编辑视图
			handleAdd() {
				this.editingId = null
				this.resetForm()
				this.mode = 'edit'
			},
			// 编辑：回显已有地址
			handleEdit(item) {
				this.editingId = item.id
				this.form = {
					consignee: item.consignee,
					phone: item.phone,
					region: item.region,
					detailAddress: item.detailAddress,
					isDefault: item.isDefault
				}
				this.regionArray = item.region ? item.region.split(' ') : []
				this.mode = 'edit'
			},
			// region 存「省 市 区」空格分隔（后端约定 + 编辑回显依赖），展示时去掉空格更紧凑
			formatRegion(region) {
				return (region || '').replace(/\s+/g, '')
			},
			// 省市区 picker 选中
			onRegionChange(e) {
				this.regionArray = e.detail.value
				this.form.region = e.detail.value.join(' ')
			},
			// 默认开关切换：u-switch 给的是 boolean，转成 0/1 存
			onDefaultChange(val) {
				this.form.isDefault = val ? 1 : 0
			},
			// 保存（新增或修改）
			handleSave() {
				if (this.saving) {
					return
				}
				if (!this.validate()) {
					return
				}
				this.saving = true
				const data = {
					consignee: this.form.consignee.trim(),
					phone: this.form.phone.trim(),
					region: this.form.region,
					detailAddress: this.form.detailAddress.trim(),
					isDefault: this.form.isDefault
				}
				const req = this.editingId ? updateAddress(this.editingId, data) : addAddress(data)
				req.then(() => {
					this.saving = false
					uni.showToast({
						title: '保存成功',
						icon: 'success'
					})
					this.mode = 'list'
					this.fetchList()
					this.$emit('saved')
				}).catch(() => {
					this.saving = false
				})
			},
			// 删除地址
			handleDelete() {
				uni.showModal({
					title: '删除地址',
					content: '删除后不可恢复，确定删除该地址吗？',
					confirmColor: '#FF6700',
					success: (res) => {
						if (!res.confirm) {
							return
						}
						deleteAddress(this.editingId).then(() => {
							this.mode = 'list'
							this.fetchList()
							this.$emit('saved')
						}).catch(() => {})
					}
				})
			},
			// 前端校验：四个必填 + 手机号 11 位（后端仍有权威校验兜底）
			validate() {
				if (!this.form.consignee.trim()) {
					uni.showToast({
						title: '请填写收货人',
						icon: 'none'
					})
					return false
				}
				if (!/^\d{11}$/.test(this.form.phone.trim())) {
					uni.showToast({
						title: '请填写正确的11位手机号',
						icon: 'none'
					})
					return false
				}
				if (!this.form.region) {
					uni.showToast({
						title: '请选择省市区',
						icon: 'none'
					})
					return false
				}
				if (!this.form.detailAddress.trim()) {
					uni.showToast({
						title: '请填写详细地址',
						icon: 'none'
					})
					return false
				}
				return true
			},
			// 重置表单
			resetForm() {
				this.form = {
					consignee: '',
					phone: '',
					region: '',
					detailAddress: '',
					isDefault: 0
				}
				this.regionArray = []
			}
		}
	}
</script>

<style scoped>
	/* 底部弹窗主体：圆角顶部 + 最大高度 + 内部滚动 */
	.popup-body {
		box-sizing: border-box;
		width: 100%;
		max-height: 80vh;
		background: #ffffff;
		border-radius: 24rpx 24rpx 0 0;
		padding: 30rpx;
		/* 底部安全区兜底：避免按钮被屏幕底部 / home indicator 遮挡 */
		padding-bottom: calc(220rpx + env(safe-area-inset-bottom));
		/* 纵向 flex：header 顶部、内容区滚动、底部按钮固定 */
		display: flex;
		flex-direction: column;
	}

	.header {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		position: relative;
		margin-bottom: 20rpx;
	}

	.title {
		font-size: 32rpx;
		font-weight: bold;
		color: #333333;
	}

	.close {
		position: absolute;
		right: 0;
		font-size: 48rpx;
		color: #999999;
		line-height: 1;
	}

	/* 列表 / 编辑视图：占满剩余高度，内容超出时在内部滚动（配合 popup-body 的 flex 布局） */
	.list-view,
	.edit-view {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
	}

	/* ===== 列表视图 ===== */
	.address-item {
		display: flex;
		align-items: center;
		padding: 24rpx 0;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.item-main {
		flex: 1;
		min-width: 0;
	}

	.consignee-info {
		display: flex;
		align-items: baseline;
		margin-top:4rpx;
	}

	.consignee {
		font-size: 30rpx;
		font-weight: bold;
		color: #333333;
	}

	.phone {
		font-size: 26rpx;
		color: #666666;
		margin-left:10rpx;
	}

	.region {
		display: block;
		font-size: 26rpx;
		color: #333333;
	}

	.detail {
		display: block;
		font-size: 26rpx;
		color: #999999;
	}

	.item-side {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
	}

	.default-tag {
		padding: 4rpx 12rpx;
		font-size: 20rpx;
		color: #FF6700;
		background: #FFF3E8;
		border-radius: 6rpx;
	}

	.edit {
		margin-top: 12rpx;
		font-size: 24rpx;
		color: #999999;
	}

	.empty {
		padding: 60rpx 0;
		text-align: center;
		font-size: 28rpx;
		color: #999999;
	}

	.add-btn {
		/* 底部固定按钮：不参与内容滚动，始终贴在弹窗底部 */
		flex-shrink: 0;
		margin-top: 50rpx;
		padding: 20rpx 0;
		font-size: 28rpx;
		text-align: center;
		color: #FF6700;
		background: #ffffff;
		border: 1rpx dashed #FF6700;
		border-radius: 12rpx;
	}

	/* ===== 编辑视图 ===== */
	.form-item {
		display: flex;
		align-items: center;
		margin-bottom: 24rpx;
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
		background: #f7f7f7;
		border-radius: 12rpx;
	}

	.picker {
		display: flex;
		align-items: center;
		justify-content: flex-start;
	}

	.picker.placeholder {
		color: #999999;
	}

	.default-switch {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 10rpx;
		padding: 20rpx 0;
	}

	.switch-label {
		font-size: 28rpx;
		color: #333333;
	}

	.save-btn {
		margin-top: 20rpx;
		padding: 20rpx 0;
		font-size: 28rpx;
		text-align: center;
		color: #ffffff;
		background: #FF6700;
		border-radius: 40rpx;
	}

	.save-btn.loading {
		opacity: 0.6;
	}

	.delete-btn {
		margin-top: 20rpx;
		padding: 16rpx 0;
		font-size: 26rpx;
		text-align: center;
		color: #ff4d4f;
	}
</style>

<style>
	/* uni-popup 自带层级较低，抬到和商品弹窗一致（1000） */
	.address-popup.uni-popup {
		z-index: 1000;
	}
</style>
