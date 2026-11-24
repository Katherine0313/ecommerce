<template>
<view class="page">
		<CustomNavbar title="搜索" :show-back="true" />

		<!-- 搜索栏：输入框自动聚焦，回车或点搜索触发 -->
		<view class="search-bar">
			<input
				class="search-input"
				v-model="keyword"
				placeholder="搜索商品"
				confirm-type="search"
				:focus="true"
				@confirm="doSearch"
			/>
			<view class="search-btn" @click="doSearch">搜索</view>
		</view>

		<!-- 搜索结果：两列商品网格 -->
		<view v-if="products.length" class="goods-grid">
			<view class="goods-card" v-for="item in products" :key="item.id" @click="goDetail(item.id)">
				<image lazy-load class="goods-img" :src="goodsImgUrl(item.image, item.id)" mode="aspectFill"></image>
				<!-- 促销标签：压在商品图左上角 -->
				<text v-if="item.promotionTag" class="promo-tag">{{ item.promotionTag }}</text>
				<view class="goods-info">
					<text class="goods-name">{{ item.name }}</text>
					<view class="goods-bottom">
						<text class="goods-price">¥{{ item.price }}</text>
						<text v-if="item.originalPrice" class="origin-price">¥{{ item.originalPrice }}</text>
					</view>
				</view>
			</view>
		</view>

		<!-- 分页底部提示：加载中 / 没有更多 -->
		<view v-if="loading && products.length" class="list-tip">加载中…</view>
		<view v-else-if="products.length && !hasMore" class="list-tip">没有更多了</view>

		<!-- 空态：搜过且无结果 -->
		<view v-else-if="searched && !loading" class="empty">
			<text class="empty-text">未找到相关商品</text>
		</view>
	</view>
</template>

<script>
	import { getProductPage } from '../../api/product.js'
	import { goodsImgUrl } from '../../utils/format.js'

	// 搜索结果每页条数：两列网格
	const PAGE_SIZE = 12

	export default {
		data() {
			return {
				keyword: '',
				// 实际搜索的关键词：输入框继续输入不影响已加载列表的续拉
				activeKeyword: '',
				products: [],
				searched: false,
				// 分页状态：当前页 / 总条数 / 是否还有下一页 / 是否请求中
				page: 1,
				total: 0,
				hasMore: false,
				loading: false,
				// 请求序号：连续搜索时只应用最后一次结果
				searchSeq: 0
			}
		},
		onReachBottom() {
			// 页面滚到底部时加载下一页
			this.loadMore()
		},
		methods: {
			goodsImgUrl,
			// 触发搜索：输入框回车或点搜索按钮；重置分页后加载第一页
			doSearch() {
				const kw = this.keyword.trim()
				if (!kw) {
					return
				}
				this.activeKeyword = kw
				this.products = []
				this.page = 1
				this.total = 0
				this.hasMore = false
				this.searched = true
				this.searchSeq++
				this.fetchPage(kw, 1, this.searchSeq)
			},
			// 滚动到底：加载下一页
			loadMore() {
				if (this.loading || !this.hasMore || !this.activeKeyword) return
				this.fetchPage(this.activeKeyword, this.page + 1, this.searchSeq)
			},
			// 拉一页搜索结果；seq 不一致说明期间又发起了新搜索，丢弃结果
			fetchPage(keyword, page, seq) {
				this.loading = true
				getProductPage({ page: page, pageSize: PAGE_SIZE, keyword: keyword }).then((res) => {
					if (seq !== this.searchSeq) return
					const list = (res && res.list) || []
					this.total = (res && res.total) || 0
					this.page = page
					this.products = page === 1 ? list : this.products.concat(list)
					this.hasMore = this.products.length < this.total
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).then(() => {
					if (seq === this.searchSeq) this.loading = false
				})
			},
			// 点商品跳详情
			goDetail(id) {
				uni.navigateTo({ url: '/pages/order/detail?id=' + id })
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
	}

	/* ═══ 搜索栏 ═══ */
	.search-bar {
		display: flex;
		align-items: center;
		padding: 16rpx 20rpx;
		background: #ffffff;
		border-bottom: 1rpx solid #f0f0f0;
	}

	.search-input {
		flex: 1;
		height: 64rpx;
		background: #f5f5f5;
		border-radius: 32rpx;
		padding: 0 24rpx;
		font-size: 28rpx;
	}

	.search-btn {
		margin-left: 16rpx;
		padding: 0 36rpx;
		height: 64rpx;
		line-height: 64rpx;
		background: #FF6700;
		color: #ffffff;
		border-radius: 32rpx;
		font-size: 28rpx;
	}

	/* ═══ 商品网格 ═══ */
	.goods-grid {
		display: flex;
		flex-wrap: wrap;
		justify-content: space-between;
		padding: 20rpx 16rpx;
	}

	.goods-card {
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		position: relative;
		width: calc(50% - 8rpx);
		margin-bottom: 20rpx;
		background: #ffffff;
		border-radius: 12rpx;
		overflow: hidden;
	}

	/* 促销标签：商品图左上角角标 */
	.promo-tag {
		position: absolute;
		top: 0;
		left: 0;
		z-index: 2;
		padding: 2rpx 10rpx;
		font-size: 24rpx;
		color: #ffffff;
		background: #FF6700;
		border-radius: 0 0 8rpx 0;
	}

	/* 活动前原价：划线展示 */
	.origin-price {
		margin-left: 10rpx;
		font-size: 22rpx;
		color: #999999;
		text-decoration: line-through;
	}

	.goods-img {
		width: 100%;
		height: 300rpx;
		flex-shrink: 0;
	}

	.goods-info {
		display: flex;
		flex-direction: column;
		padding: 16rpx 20rpx 20rpx;
	}

	.goods-name {
		font-size: 28rpx;
		color: #333333;
		line-height: 1.4;
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
		overflow: hidden;
	}

	.goods-bottom {
		display: flex;
		align-items: center;
		margin-top: 12rpx;
	}

	.goods-price {
		font-size: 32rpx;
		font-weight: bold;
		color: #FF6700;
	}

	/* ═══ 分页底部提示 ═══ */
	.list-tip {
		padding: 20rpx 0 40rpx;
		text-align: center;
		font-size: 24rpx;
		color: #999999;
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
