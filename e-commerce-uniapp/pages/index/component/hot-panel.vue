<template>
	<!-- 热门推荐面板：4 个色块，每块左右各一个商品位，点图片把商品 id 抛给页面 -->
	<view class="panel-hot">
		<view class="list" v-for="(row, ri) in rows" :key="ri">
			<view class="item" v-for="(block, bi) in row" :key="bi">
				<view class="l">
					<text class="title">{{ block.left.title }}</text>
					<image lazy-load :src="imgUrl(block.left.id, block.left.image)" mode="aspectFill" @click="handleSelect(block.left.id)"></image>
				</view>
				<view class="r">
					<text class="title">{{ block.right.title }}</text>
					<image lazy-load :src="imgUrl(block.right.id, block.right.image)" mode="aspectFill" @click="handleSelect(block.right.id)"></image>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import {
		BASE_URL
	} from '../../../utils/server-api.js'

	// 栏目名固定写死；商品位默认展示的 id（下拉刷新前）
	const TITLES = [
		[
			{ left: '爆款推荐', right: '人气热卖' },
			{ left: '特惠推荐', right: '精选数码' }
		],
		[
			{ left: '好物精选', right: '新品首发' },
			{ left: '前沿潮品', right: '数码优选' }
		]
	]
	const DEFAULT_IDS = [1, 7, 16, 32, 26, 12, 22, 21]

	export default {
		name: 'HotPanel',
		props: {
			// 外部传入的商品列表（下拉刷新换的一批）；undefined/空 时用默认 8 个 id
			list: { type: Array, default: undefined }
		},
		computed: {
			// 每行两个色块，每个色块左右各放一个商品位：title 是栏目名，id 是商品 id
			rows() {
				return TITLES.map((row, ri) => row.map((block, bi) => ({
					left: { title: block.left, ...this.itemAt((ri * 2 + bi) * 2) },
					right: { title: block.right, ...this.itemAt((ri * 2 + bi) * 2 + 1) }
				})))
			}
		},
		methods: {
			// 第 n 个商品位的 id：优先用外部传入的商品，不足或未传时回退默认 id
			idAt(n) {
				return this.itemAt(n).id
			},
			// 第 n 个商品位的 id + 图片：图片取后端返回的 image（含 ?v= 版本号）
			itemAt(n) {
				const item = this.list && this.list[n]
				return {
					id: item && item.id ? item.id : DEFAULT_IDS[n],
					image: item ? item.image : ''
				}
			},
			// 当前展示的 8 个商品 id，页面下拉刷新时拿去排除，避免换出来和现在重复
			currentIds() {
				return [0, 1, 2, 3, 4, 5, 6, 7].map((i) => this.idAt(i))
			},
			// 商品图：优先用后端返回的 image（带 ?v= 版本号，用来绕开一年期缓存），
			// 没有 image 才回退按 id 拼同命名商品图
			imgUrl(id, image) {
				if (image) {
					return image.indexOf('http') === 0 ? image : BASE_URL + image
				}
				return BASE_URL + '/image/upload/' + id + '.webp'
			},
			// 点击商品图：只把 id 抛出去，拉详情和弹窗都由页面处理
			handleSelect(id) {
				this.$emit('select', id)
			}
		}
	}
</script>

<style scoped>
	.panel-hot {
		display: flex;
		flex-direction: column;
		gap: 20rpx;
		padding: 0 20rpx;
	}

	.list {
		display: flex;
		gap: 20rpx;
	}

	.item {
		box-sizing: border-box;
		display: flex;
		gap: 15rpx;
		width: calc(50% - 10rpx);
		padding: 10rpx 15rpx;
	}
	.l{
		width: calc(50% - 7.5rpx);
		font-weight:bold;
	}
	.r{
		flex:1;
		color:#bbb;
	}
	image{
		margin-top: 14rpx;
		height: 140rpx;
		width:100%;
	}
</style>
