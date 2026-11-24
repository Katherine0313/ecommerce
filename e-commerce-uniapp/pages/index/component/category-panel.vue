<template>
	<!-- 前台分类面板：一级分类宫格。首页由聚合接口传入 list，其他页面不传则组件自己请求 -->
	<view class="category-panel" v-if="!showSkeleton">
		<view
			v-for="item in categories"
			:key="item.id"
			class="category-item"
			@click="handleSelect(item)"
		>
			<image lazy-load class="category-icon" :src="imgUrl(item)" mode="aspectFit"></image>
			<text class="category-name">{{ item.name }}</text>
		</view>
	</view>
	<!-- 骨架屏：接口未返回时先占好 8 个格子的位置，避免数据回来时页面跳动 -->
	<view class="category-panel" v-else>
		<view class="category-item" v-for="n in 8" :key="n">
			<view class="category-icon skeleton-block"></view>
			<view class="category-name skeleton-line"></view>
		</view>
	</view>
</template>

<script>
	import { getCategories } from '../../../api/categories.js'
	import { BASE_URL } from '../../../utils/server-api.js'

	export default {
		name: 'CategoryPanel',
		props: {
			// 首页聚合接口传入的一级分类：undefined=没传（自请求），null=加载中，数组=已加载
			list: { type: Array, default: undefined },
			// 最多展示几个（首页 4 列 × 2 行 = 8）；0 表示不限制
			limit: { type: Number, default: 0 }
		},
		data() {
			return {
				// 自请求时的一级父分类列表（购物车等页面单独使用组件时）
				internalCategories: [],
				selfLoaded: false
			}
		},
		computed: {
			// 外部传入优先，否则用自请求数据；limit>0 时只展示前 N 个
			categories() {
				const source = this.list !== undefined ? (this.list || []) : this.internalCategories
				return this.limit > 0 ? source.slice(0, this.limit) : source
			},
			// 没数据且仍在加载时显示骨架
			showSkeleton() {
				if (this.categories.length > 0) return false
				return this.list === undefined ? !this.selfLoaded : this.list === null
			}
		},
		mounted() {
			// 首页会传 list，不再重复请求
			if (this.list === undefined) {
				this.fetchCategories()
			}
		},
		methods: {
			// 拉一级分类：后端 categories 表 parentId 为 null 的是顶级分类，名称和图片都用后端的
			// 返回 Promise，供页面下拉刷新时等请求结束
			fetchCategories() {
				return getCategories().then((list) => {
					// 后端返回树形列表，每个元素就是一级父分类
					this.internalCategories = list || []
				}).catch(() => {
					// 错误提示已在 request 封装里统一处理
				}).finally(() => {
					this.selfLoaded = true
				})
			},
			// 分类图：后端 image 是 /image/category/xxx.png 相对路径，小程序把开头的 / 当包内路径，需补 BASE_URL
			imgUrl(item) {
				const img = item.image
				return img ? (img.indexOf('http') === 0 ? img : BASE_URL + img) : ''
			},
			// 点击某个分类：把整项抛给页面，跳分类页 / 筛选交给页面决定
			handleSelect(item) {
				this.$emit('select', item)
			}
		}
	}
</script>

<style scoped>
	.category-panel {
		display: flex;
		flex-wrap: wrap;
		width: 100%;
		background: #ffffff;
		padding: 24rpx 0 0;
	}

	.category-item {
		width: 23.33%;
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-bottom: 28rpx;
	}

	.category-icon {
		width: 88rpx;
		height: 88rpx;
		/* 圆角裁剪，30rpx ≈ 15px */
		border-radius: 30rpx;
	}

	.category-name {
		margin-top: 12rpx;
		font-size: 26rpx;
		line-height: 1;
		color: #333333;
	}

	/* 骨架占位：和真实分类图/文字同尺寸，数据回来不跳位置 */
	.skeleton-block {
		background: #f0f0f0;
	}

	.skeleton-line {
		width: 56rpx;
		height: 24rpx;
		background: #f0f0f0;
		border-radius: 8rpx;
	}
</style>
