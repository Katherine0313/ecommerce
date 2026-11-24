<template>
<view class="page">
		<!-- 自定义导航栏：标题固定「我的订单」 -->
		<CustomNavbar title="我的订单" :show-back="true" />

		<!-- 搜索框：订单号/商品名/收货人，回车或点搜索触发 -->
		<view class="search-box">
			<input
				class="search-input"
				v-model="keyword"
				placeholder="订单号/商品名"
				confirm-type="search"
				@confirm="search"
			/>
			<view class="search-btn" @click="search">搜索</view>
		</view>

		<!-- 状态 Tab：横向滚动，切换筛订单 -->
		<scroll-view scroll-x class="tabs-scroll">
			<view class="tabs">
				<view
					class="tab-item"
					:class="{ active: activeIndex === index }"
					v-for="(tab, index) in tabs"
					:key="index"
					@click="changeTab(index)"
				>
					{{ tab.title }}
				</view>
			</view>
		</scroll-view>

		<!-- 售后 Tab：售后单列表 -->
		<template v-if="isAfterSaleTab">
			<view v-if="afterSaleList.length" class="order-list">
				<AfterSaleCard
					v-for="item in afterSaleList"
					:key="item.id"
					:after-sale="item"
					@click="goAfterSaleDetail"
				/>
			</view>
			<view v-else-if="afterSaleLoaded" class="empty">
				<text class="empty-text">暂无售后记录</text>
			</view>
		</template>

		<!-- 订单列表：复用 OrderCard，卡片内部按状态渲染头部与底部按钮 -->
		<view v-else-if="orders.length" class="order-list">
			<OrderCard
				v-for="order in orders"
				:key="order.id"
				:order="order"
				:now="now"
				@click="goDetail"
				@pay="handlePay"
				@cancel="handleCancel"
				@confirm="confirmReceive"
				@delete="deleteOrder"
				@buy-again="buyAgain"
				@review="handleReview"
			/>
		</view>

		<!-- 空态：加载完成且没有订单 -->
		<view v-else-if="loaded" class="empty">
			<text class="empty-text">暂无相关订单</text>
		</view>
	</view>
</template>

<script>
	import {
		listOrders,
		deleteOrder as deleteOrderApi,
		finishOrder,
		payOrder as payOrderApi,
		cancelOrder as cancelOrderApi
	} from '../../api/order.js'
	import {
		addToCart
	} from '../../api/cart.js'
	import {
		useCartStore
	} from '../../store/cart.js'
	import {
		ensureLogin
	} from '../../utils/auth.js'
	import OrderCard from '../../components/order-card/order-card.vue'
	import AfterSaleCard from '../../components/after-sale-card/after-sale-card.vue'
	import {
		listAfterSales
	} from '../../api/after-sale.js'

	export default {
		components: {
			OrderCard,
			AfterSaleCard
		},
		data() {
			return {
				// 6 个 Tab：statuses 为 null 表示不过滤（全部）；售后是独立数据，占位
				tabs: [
					{ title: '全部', statuses: null },
					{ title: '待付款', statuses: [1] },
					{ title: '待发货', statuses: [3] },
					{ title: '待收货', statuses: [4] },
					{ title: '评论', statuses: [5] },
					{ title: '售后', afterSale: true }
				],
				activeIndex: 0, // 当前选中 Tab
				keyword: '', // 搜索关键词（订单号/商品名/收货人）
				allOrders: [], // 一次拉取的全部订单
				orders: [], // 当前 Tab 过滤后的订单
				loaded: false, // 是否完成首次加载
				afterSaleList: [], // 售后单列表（售后 Tab 用）
				afterSaleLoaded: false, // 售后列表是否完成加载
				now: Date.now(), // 当前时间戳，每秒刷新驱动倒计时
				operating: false // 订单操作中（付款/取消/确认收货/删除/再来一单），防连点重复提交
			}
		},
		computed: {
			// 当前是否「售后」Tab（占位，售后单列表未做）
			isAfterSaleTab() {
				return !!this.tabs[this.activeIndex].afterSale
			}
		},
		onLoad(options) {
			// 从「我的」页跳转定位 Tab：afterSale 定位售后 Tab，status 定位对应状态 Tab
			if (options.afterSale) {
				const index = this.tabs.findIndex(tab => tab.afterSale)
				if (index !== -1) {
					this.activeIndex = index
				}
				return
			}
			const status = Number(options.status)
			if (!status) {
				return
			}
			const index = this.tabs.findIndex(tab => tab.statuses && tab.statuses.includes(status))
			if (index !== -1) {
				this.activeIndex = index
			}
		},
		onShow() {
			if (this.isAfterSaleTab) {
				this.loadAfterSales()
			} else {
				this.loadOrders()
			}
			this.startTimer()
		},
		onHide() {
			this.stopTimer()
		},
		onUnload() {
			this.stopTimer()
		},
		methods: {
			// 切换 Tab：重新按状态过滤；售后 Tab 单独加载售后列表
			changeTab(index) {
				this.activeIndex = index
				if (this.isAfterSaleTab) {
					this.loadAfterSales()
				} else {
					this.filterOrders()
				}
			},
			// 搜索：带关键词重拉订单（后端按订单号/收货人/商品名模糊匹配）
			search() {
				this.loadOrders()
			},
			// 点订单卡片：按状态跳对应详情页（待发货 → pending-ship；待收货 → pending-receive）
			goDetail(order) {
				const status = Number(order.orderStatus)
				if (status === 3) {
					uni.navigateTo({ url: '/pages/order/pending-ship?id=' + order.id })
				} else if (status === 4) {
					uni.navigateTo({ url: '/pages/order/pending-receive?id=' + order.id })
				}
			},
			// 拉全部订单，预计算待付款订单的超时时间戳，再按当前 Tab 过滤
			loadOrders() {
				ensureLogin()
					.then(() => listOrders(this.keyword))
					.then((list) => {
						this.allOrders = list || []
						this.filterOrders()
					})
					.catch(() => {
						// 错误提示已在 request 封装里统一处理
					})
					.finally(() => {
						this.loaded = true
					})
			},
			// 拉售后单列表（售后 Tab）
			loadAfterSales() {
				ensureLogin()
					.then(() => listAfterSales())
					.then((list) => {
						this.afterSaleList = list || []
					})
					.catch(() => {
						// 错误提示已在 request 封装里统一处理
					})
					.finally(() => {
						this.afterSaleLoaded = true
					})
			},
			// 点售后卡片：按售后类型跳对应详情页
			goAfterSaleDetail(afterSale) {
				let page = '/pages/after-sales/only-refund-detail'
				if (afterSale.afterSaleType === 'return_refund') {
					page = '/pages/after-sales/return-refund-detail'
				} else if (afterSale.afterSaleType === 'exchange') {
					page = '/pages/after-sales/exchange-detail'
				}
				uni.navigateTo({ url: page + '?id=' + afterSale.id })
			},
			// 按当前 Tab 的状态码过滤
			filterOrders() {
				const statuses = this.tabs[this.activeIndex].statuses
				this.orders = statuses == null ?
					this.allOrders :
					this.allOrders.filter(o => statuses.includes(Number(o.orderStatus)))
			},
			// 倒计时：每秒把 now 更新成当前时间
			startTimer() {
				this.stopTimer()
				this.now = Date.now()
				this._timer = setInterval(() => {
					this.now = Date.now()
				}, 1000)
			},
			stopTimer() {
				if (this._timer) {
					clearInterval(this._timer)
					this._timer = null
				}
			},
			// 付款：待付款 → 待发货，成功后刷新
			handlePay(order) {
				if (this.operating) return
				this.operating = true
				payOrderApi(order.id).then(() => {
					uni.showToast({ title: '支付成功', icon: 'success' })
					this.loadOrders()
				}).catch(() => {}).finally(() => {
					this.operating = false
				})
			},
			// 取消订单：成功后刷新
			handleCancel(order) {
				if (this.operating) return
				this.operating = true
				cancelOrderApi(order.id).then(() => {
					uni.showToast({ title: '订单已取消', icon: 'success' })
					this.loadOrders()
				}).catch(() => {}).finally(() => {
					this.operating = false
				})
			},
			// 确认收货：已发货 → 已完成，成功后刷新
			confirmReceive(order) {
				if (this.operating) return
				this.operating = true
				finishOrder(order.id).then(() => {
					uni.showToast({ title: '已确认收货', icon: 'success' })
					this.loadOrders()
				}).catch(() => {}).finally(() => {
					this.operating = false
				})
			},
			// 删除订单：软删，成功后刷新
			deleteOrder(order) {
				if (this.operating) return
				this.operating = true
				deleteOrderApi(order.id).then(() => {
					uni.showToast({ title: '已删除', icon: 'success' })
					this.loadOrders()
				}).catch(() => {}).finally(() => {
					this.operating = false
				})
			},
			// 再来一单：把订单商品重新加入购物车
			buyAgain(order) {
				const items = order.items || []
				if (!items.length || this.operating) {
					return
				}
				this.operating = true
				uni.showLoading({ title: '加入购物车中...' })
				Promise.all(items.map(item => addToCart(item.productId, item.quantity, [], 0)))
					.then(() => {
						uni.hideLoading()
						useCartStore().addQuantity(items.reduce((sum, i) => sum + (Number(i.quantity) || 0), 0))
						uni.showToast({ title: '已加入购物车', icon: 'success' })
						setTimeout(() => {
							uni.switchTab({ url: '/pages/cart/cart' })
						}, 800)
					})
					.catch(() => {
						uni.hideLoading()
					})
					.finally(() => {
						this.operating = false
					})
			},
			// 评价商品：跳评价页
			handleReview(order) {
				uni.navigateTo({ url: '/pages/order/review?orderId=' + order.id })
			}
		}
	}
</script>

<style scoped>
	.page {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
		background: #f8f8f8;
		padding-bottom: env(safe-area-inset-bottom);
	}

	/* ═══ 搜索框 ═══ */
	.search-box {
		display: flex;
		align-items: center;
		background: #ffffff;
		padding: 16rpx 20rpx;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.search-input {
		flex: 1;
		height: 60rpx;
		background: #f5f5f5;
		border-radius: 8rpx;
		padding: 0 20rpx;
		font-size: 26rpx;
	}

	.search-btn {
		margin-left: 16rpx;
		padding: 0 32rpx;
		height: 60rpx;
		line-height: 60rpx;
		background: #FF6700;
		color: #ffffff;
		border-radius: 8rpx;
		font-size: 26rpx;
	}

	/* ═══ Tab 栏 ═══ */
	.tabs-scroll {
		white-space: nowrap;
		background: #ffffff;
		margin-bottom: 20rpx;
	}

	.tabs {
		display: flex;
	}

	.tab-item {
		padding: 24rpx 24rpx;
		font-size: 30rpx;
		color: #666666;
		position: relative;
	}

	.tab-item.active {
		color: #FF6700;
		font-weight: bold;
	}

	.tab-item.active::after {
		content: "";
		position: absolute;
		bottom: 0;
		left: 50%;
		transform: translateX(-50%);
		width: 40rpx;
		height: 4rpx;
		background: #FF6700;
		border-radius: 2rpx;
	}

	.order-list {
		padding: 0 20rpx;
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
</style>
