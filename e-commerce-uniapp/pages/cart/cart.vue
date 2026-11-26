<template>
<view class="page">
		<!-- 自定义顶部导航栏：tabBar 页没有上一级，只留标题 -->
		<CustomNavbar />

		<!-- 内容区：为固定定位的结算栏 + 底部导航栏留出空间 -->
		<view class="content">
			<!-- 空购物车 -->
			<view v-if="!loading && !items.length" class="empty">
				<text class="empty-icon iconfont icon-gouwuche"></text>
				<text class="empty-text">购物车是空的，去逛逛吧</text>
			</view>

			<!-- 商品列表 -->
			<view v-else class="cart-list">
				<!-- 已选操作条：有选中项时显示，可批量删除 -->
				<view v-if="selectedIds.length" class="batch-bar">
					<text class="batch-count">已选 {{ selectedCount }} 件</text>
					<text class="batch-del" @click="removeSelected">删除选中</text>
				</view>

				<view class="cart-item" v-for="item in items" :key="item.skuId || item.productId">
					<!-- 单选圆圈 -->
					<view class="check" :class="{ on: isSelected(item.skuId || item.productId) }" @click="toggleSelect(item.skuId || item.productId)">
						<text v-if="isSelected(item.skuId || item.productId)" class="check-mark">✓</text>
					</view>

					<!-- 商品图 -->
					<image lazy-load class="item-img" :src="imgUrl(item)" mode="aspectFill"></image>

					<!-- 商品信息 -->
					<view class="item-info">
						<text class="item-name">{{ item.name }}</text>
						<text v-if="item.specJson" class="item-spec">{{ fmtSpec(item.specJson) }}</text>
						<view class="item-price-row">
							<text class="item-price">单价 ¥{{ fmt(item.price) }}</text>
							<text v-if="item.originalPrice" class="item-origin">¥{{ fmt(item.originalPrice) }}</text>
							<text v-if="item.promotionTag" class="item-tag">{{ item.promotionTag }}</text>
						</view>
						<!-- 增值保障：每件费用，有值才显示 -->
						<text v-if="num(item.insuranceFee) > 0" class="item-insurance">增值保障 ¥{{ fmt(item.insuranceFee) }}</text>
						<view class="item-bottom">
							<!-- 数量步进器：可输入 + 可加减，最小 1 -->
							<QuantityStepper :value="item.quantity" :max="item.stock" @change="changeQuantity(item, $event)" />
							<!-- 单品小计（后端已算好，含增值保障） -->
							<text class="item-sub">小计 ¥{{ fmt(item.subTotal) }}</text>
						</view>
					</view>

					<!-- 删除单项 -->
					<view class="item-del" @click="removeOne(item)">删除</view>
				</view>
			</view>

			<!-- 猜你喜欢：购物车底部推荐，点商品抛 id 交页面处理 -->
			<Guess @select="handleGuessSelect" />
		</view>

		<!-- 底部结算栏：固定在底部导航栏上方 -->
		<view v-if="items.length" class="settle-bar">
			<view class="settle-check" @click="toggleSelectAll">
				<view class="check" :class="{ on: allSelected }">
					<text v-if="allSelected" class="check-mark">✓</text>
				</view>
				<text class="settle-all">全选</text>
			</view>
			<view class="settle-total">
				<text class="total-label">合计：</text>
				<text class="total-price">¥{{ totalPrice }}</text>
			</view>
			<view class="settle-btn" :class="{ disabled: !selectedCount }" @click="handleSettle">
				去结算({{ selectedCount }})
			</view>
		</view>


		<!-- 自定义底部导航栏 -->
		<CustomTabBar />
	</view>
</template>

<script>
	import { getCart, updateQuantity, removeItem, clearCart } from '../../api/cart.js'
	import { ensureLogin } from '../../utils/auth.js'
	import { BASE_URL } from '../../utils/server-api.js'
	import { useCartStore } from '../../store/cart.js'

	export default {
		data() {
			return {
				loading: false, // 首次拉列表的加载态，避免空态一闪而过
				items: [], // 购物车条目，对齐后端 CartVO（productId/name/image/price/quantity/subTotal）
				selectedIds: [], // 选中的 productId 列表
			}
		},
		computed: {
			// 选中的条目
			selectedItems() {
				return this.items.filter(i => this.selectedIds.includes(this.rowKey(i)))
			},
			// 选中件数（按数量求和，不是条目数）
			selectedCount() {
				return this.selectedItems.reduce((sum, i) => sum + i.quantity, 0)
			},
			// 选中商品合计（小计相加）
			totalPrice() {
				return this.fmt(this.selectedItems.reduce((sum, i) => sum + this.num(i.subTotal), 0))
			},
			// 是否全选
			allSelected() {
				return this.items.length > 0 && this.selectedIds.length === this.items.length
			}
		},
		// tabBar 页每次切入都刷新：别的页面加购/删除后切回来能拿到最新
		onShow() {
			this.loadCart()
		},
		methods: {
			// 购物车条目的唯一键：优先 skuId，没有则回退 productId
			rowKey(item) { return item.skuId || item.productId },
			// 拉购物车列表；先 ensureLogin 兜底（/api/cart 不在免登录白名单内），再清掉已不存在的选中项
			loadCart() {
				this.loading = true
				return ensureLogin().then(() => getCart()).then((list) => {
					this.items = list || []
					const ids = this.items.map(i => this.rowKey(i))
					this.selectedIds = this.selectedIds.filter(id => ids.includes(id))
					// 同步全局购物车件数，刷新底部角标
					this.syncCount()
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).finally(() => {
					this.loading = false
				})
			},
			// 是否选中某个商品
			isSelected(id) {
				return this.selectedIds.includes(id)
			},
			// 单选/取消
			toggleSelect(id) {
				if (this.isSelected(id)) {
					this.selectedIds = this.selectedIds.filter(x => x !== id)
				} else {
					this.selectedIds.push(id)
				}
			},
			// 全选/取消全选
			toggleSelectAll() {
				this.selectedIds = this.allSelected ? [] : this.items.map(i => this.rowKey(i))
			},
			// 数量变化（+/- 或输入框提交）：本地乐观更新，后端为权威；subTotal 按快照单价重算
			changeQuantity(item, next) {
				if (next < 1) {
					return
				}
				updateQuantity(item.productId, next, item.skuId).then(() => {
					item.quantity = next
					item.subTotal = (this.num(item.price) + this.num(item.insuranceFee)) * next
					// 数量变了，同步全局购物车件数
					this.syncCount()
				}).catch(() => {
					// 失败不更新本地，提示已在 request 封装里统一处理
				})
			},
			// 删除单项：确认后调后端，再整表刷新
			removeOne(item) {
				uni.showModal({
					title: '移除商品',
					content: '确定从购物车移除「' + item.name + '」吗？',
					confirmColor: '#FF6700',
					success: (res) => {
						if (!res.confirm) {
							return
						}
						removeItem(item.productId, item.skuId).then(() => {
							this.loadCart()
						}).catch(() => {})
					}
				})
			},
			// 批量删除选中
			removeSelected() {
				uni.showModal({
					title: '删除选中',
					content: '确定删除选中的 ' + this.selectedCount + ' 件商品吗？',
					confirmColor: '#FF6700',
					success: (res) => {
						if (!res.confirm) {
							return
						}
						const skuIds = this.selectedItems.map(i => i.skuId).filter(Boolean)
						const productIds = this.selectedItems.map(i => i.productId).filter(Boolean)
						clearCart(productIds, skuIds).then(() => {
							this.selectedIds = []
							this.loadCart()
						}).catch(() => {})
					}
				})
			},
			// 去结算：把选中的商品快照存进 store，跳下单确认页（地址选择在确认页里做）
			handleSettle() {
				if (!this.selectedCount) {
					return
				}
				useCartStore().setCheckoutItems(this.selectedItems)
				uni.navigateTo({ url: '/pages/order/confirm' })
			},
			// 猜你喜欢商品点击：跳商品详情页（独立 page，不再弹加购弹窗）
			handleGuessSelect(id) {
				uni.navigateTo({
					url: '/pages/order/detail?id=' + id
				})
			},
			// 商品主图：后端 image 是 /image/upload/N.webp 相对路径，需补 BASE_URL；没有则回退后端同命名商品图
			imgUrl(item) {
				const img = item.image
				if (img) {
					return img.indexOf('http') === 0 ? img : BASE_URL + img
				}
				return item.productId ? BASE_URL + '/image/upload/' + item.productId + '.webp' : ''
			},
			// 金额统一两位小数展示
			fmt(n) {
				return this.num(n).toFixed(2)
			},
			// 安全转数字：null/undefined 归 0
			num(n) {
				return Number(n) || 0
			},
			// 规格快照 JSON → 展示文案 "曜石黑 / 256G"
			// 兼容后端 specJson 可能是字符串或对象
			fmtSpec(specJson) {
				if (!specJson) return ''
				const obj = typeof specJson === 'string'
					? (() => { try { return JSON.parse(specJson) } catch (e) { return null } })()
					: specJson
				if (!obj || typeof obj !== 'object') return ''
				return Object.keys(obj).map(k => obj[k]).filter(Boolean).join(' / ')
			},
			// 把当前购物车总件数同步到全局 store，供底部 tabBar 角标显示
			syncCount() {
				useCartStore().setCount(this.items.reduce((sum, i) => sum + this.num(i.quantity), 0))
			}
		}
	}
</script>

<style scoped>
	.page {
		display: flex;
		flex-direction: column;
	}

	/* 内容区：底部留出结算栏 + 底部导航栏的空间，内容才不被压住 */
	.content {
		padding-bottom: calc(220rpx + env(safe-area-inset-bottom));
	}

	/* ═══ 空购物车 ═══ */
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 160rpx 0;
	}

	.empty-icon {
		font-size: 120rpx;
		color: #dddddd;
	}

	.empty-text {
		margin-top: 24rpx;
		font-size: 28rpx;
		color: #999999;
	}

	/* ═══ 已选操作条 ═══ */
	.batch-bar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin: 10rpx 10rpx 0 10rpx;
		padding: 10rpx 24rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	.batch-count {
		font-size: 26rpx;
		color: #666666;
	}

	.batch-del {
		font-size: 26rpx;
		color: #ff4d4f;
	}

	/* ═══ 商品列表 ═══ */
	.cart-list {
		padding: 0 20rpx;
	}

	.cart-item {
		display: flex;
		align-items: center;
		margin-bottom: 10rpx;
		padding: 20rpx;
		background: #ffffff;
		border-radius: 16rpx;
	}

	/* 单选圆圈：选中变品牌橙 + 白对勾 */
	.check {
		flex-shrink: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		width: 40rpx;
		height: 40rpx;
		border: 2rpx solid #d0d0d0;
		border-radius: 50%;
		box-sizing: border-box;
	}

	.check.on {
		border-color: #FF6700;
		background: #FF6700;
	}

	.check-mark {
		font-size: 26rpx;
		color: #ffffff;
		line-height: 1;
	}

	.item-img {
		flex-shrink: 0;
		width: 100rpx;
		height: 100rpx;
		margin: 0 16rpx;
		border-radius: 12rpx;
	}

	.item-info {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.item-name {
		font-size: 28rpx;
		color: #333333;
		/* 名字超长省略 */
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.item-spec {
		font-size: 22rpx;
		color: #999999;
		margin-top: 4rpx;
		/* 规格文字超长省略 */
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.item-price-row {
		display: flex;
		align-items: baseline;
		margin-top: 8rpx;
	}

	.item-price {
		font-size: 24rpx;
		color: #999999;
	}

	/* 活动前原价：划线展示 */
	.item-origin {
		margin-left: 10rpx;
		font-size: 22rpx;
		color: #bbbbbb;
		text-decoration: line-through;
	}

	/* 促销标签 */
	.item-tag {
		margin-left: 10rpx;
		padding: 0 10rpx;
		font-size: 20rpx;
		color: #FF6700;
		border: 1rpx solid #FF6700;
		border-radius: 6rpx;
	}

	.item-insurance {
		margin-top: 4rpx;
		font-size: 24rpx;
		color: #999999;
	}

	.item-bottom {
		display: flex;
		align-items: center;
		gap: 24rpx;
		margin-top: 12rpx;
	}

	/* 单品小计：品牌橙 */
	.item-sub {
		font-size: 28rpx;
		font-weight: bold;
		color: #FF6700;
		/* 不换行、不被 flex 压缩：金额再长也保持一行 */
		white-space: nowrap;
		flex-shrink: 0;
		margin-left:30rpx;
	}

	/* 删除单项 */
	.item-del {
		flex-shrink: 0;
		margin-left: 16rpx;
		font-size: 24rpx;
		color: #999999;
	}

	/* ═══ 底部结算栏 ═══ */
	.settle-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: calc(100rpx + env(safe-area-inset-bottom));
		z-index: 998;
		display: flex;
		align-items: center;
		padding: 0 24rpx;
		height: 100rpx;
		background: #ffffff;
		border-top: 1rpx solid #f0f0f0;
		box-sizing: border-box;
	}

	.settle-check {
		display: flex;
		align-items: center;
	}

	.settle-all {
		margin-left: 12rpx;
		font-size: 26rpx;
		color: #333333;
	}

	.settle-total {
		flex: 1;
		display: flex;
		align-items: baseline;
		justify-content: flex-end;
		margin-right: 20rpx;
	}

	.total-label {
		font-size: 26rpx;
		color: #333333;
	}

	.total-price {
		font-size: 34rpx;
		font-weight: bold;
		color: #FF4E2A;
	}

	.settle-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 200rpx;
		height: 72rpx;
		border-radius: 40rpx;
		font-size: 28rpx;
		font-weight: 600;
		color: #ffffff;
		background: linear-gradient(135deg, #FF4E2A, #FF7A00);
	}

	.settle-btn.disabled {
		opacity: 0.4;
	}
</style>
