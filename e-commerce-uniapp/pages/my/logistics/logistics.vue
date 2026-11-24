<template>
<view class="page">
		<!-- 自定义导航栏：二级页，带返回按钮 -->
		<CustomNavbar title="物流查询" :show-back="true" />

		<!-- 已发货（待收货）订单卡片：复用订单卡片，点击直接看该订单的物流轨迹 -->
		<view v-if="orders.length" class="order-list">
			<OrderCard
				v-for="order in orders"
				:key="order.id"
				:order="order"
				@click="goLogistics"
				@confirm="confirmReceive"
			/>
		</view>

		<!-- 空态：没有已发货订单 -->
		<view v-else-if="loaded" class="empty">
			<text class="empty-text">您目前没有可派送的包裹</text>
		</view>
	</view>
</template>

<script>
	import {
		listOrders,
		finishOrder
	} from '../../../api/order.js'
	import {
		ensureLogin
	} from '../../../utils/auth.js'
	import OrderCard from '../../../components/order-card/order-card.vue'

	export default {
		components: {
			OrderCard
		},
		data() {
			return {
				orders: [], // 已发货（orderStatus=4）的订单
				loaded: false // 是否完成首次加载
			}
		},
		onShow() {
			// 每次进入/返回都重新拉，确认收货后卡片会同步消失
			this.load()
		},
		methods: {
			// 拉当前用户全部订单，只保留已发货（4）：已完成(5)、待发货(2/3) 不算可派送
			load() {
				ensureLogin()
					.then(() => listOrders())
					.then((list) => {
						this.orders = (list || []).filter(o => Number(o.orderStatus) === 4)
					})
					.catch(() => {
						// 错误提示已在 request 封装里统一处理
					})
					.finally(() => {
						this.loaded = true
					})
			},
			// 点卡片：直接进物流轨迹页
			goLogistics(order) {
				uni.navigateTo({
					url: '/pages/order/logistics?id=' + order.id
				})
			},
			// 确认收货：已发货 → 已完成，成功后刷新列表
			confirmReceive(order) {
				finishOrder(order.id).then(() => {
					uni.showToast({
						title: '已确认收货',
						icon: 'success'
					})
					this.load()
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
		background: #f8f8f8;
		padding-bottom: env(safe-area-inset-bottom);
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
