<template>
	<view class="page">
		<CustomNavbar :title="navTitle" :show-back="true" />

		<scroll-view class="body" scroll-y>
			<view v-if="loading" class="tip">加载中…</view>
			<view v-else-if="!article" class="tip">内容不存在或已下架</view>
			<view v-else class="article">
				<text class="article-title">{{ article.title }}</text>
				<view class="article-meta">
					<text v-if="article.author">{{ article.author }}</text>
					<text v-if="article.createTime">{{ article.createTime }}</text>
				</view>
				<image v-if="article.cover" class="article-cover" :src="coverUrl" mode="widthFix"></image>
				<!-- 正文是后台富文本，小程序 rich-text 不认相对图片地址，这里先补全域名 -->
				<rich-text class="article-content" :nodes="contentHtml"></rich-text>
			</view>
		</scroll-view>
	</view>
</template>

<script>
	import { getArticleDetail, getArticleByTitle, reportArticleBrowse } from '../../api/article.js'
	import { BASE_URL } from '../../utils/server-api.js'

	export default {
		data() {
			return {
				loading: true,
				article: null,
				// 导航栏标题：优先用文章标题，没有就用传入的标题参数
				navTitle: '文章详情'
			}
		},
		computed: {
			// 封面图补全域名
			coverUrl() {
				const cover = this.article && this.article.cover
				if (!cover) return ''
				return cover.indexOf('http') === 0 ? cover : BASE_URL + cover
			},
			// 正文里的图片是相对路径（/image/body/xxx.webp），小程序 rich-text 只认绝对地址
			contentHtml() {
				const content = (this.article && this.article.content) || ''
				return content.replace(/(src=")\/image\//g, '$1' + BASE_URL + '/image/')
			}
		},
		onLoad(options) {
			// 两种进入方式：按 id（Banner 跳转）或按标题（用户协议 / 隐私政策）
			if (options && options.id) {
				this.loadById(options.id)
			} else if (options && options.title) {
				// 跳转方（关于页）已经 encodeURIComponent 过一次，而微信不会自动解码 onLoad 的 query，
				// 这里先解一次；api 层再编码一次，net 上才是单次编码。
				// 不解的话会变成 %25E9%259A%2590 这种双重编码，后端按标题精确匹配必然查不到。
				const title = this.decodeTitle(options.title)
				this.navTitle = title
				this.loadByTitle(title)
			} else {
				this.loading = false
			}
		},
		methods: {
			// 解码页面参数里的标题：已是明文就原样返回，非法编码则兜底用原值
			decodeTitle(raw) {
				try {
					return decodeURIComponent(raw)
				} catch (e) {
					return raw
				}
			},
			loadById(id) {
				getArticleDetail(id).then((data) => {
					this.applyArticle(data)
				}).catch(() => {
					this.loading = false
				})
			},
			loadByTitle(title) {
				getArticleByTitle(title).then((data) => {
					this.applyArticle(data)
				}).catch(() => {
					this.loading = false
				})
			},
			applyArticle(data) {
				this.article = data
				if (data && data.title) this.navTitle = data.title
				// 浏览量 +1，失败不影响阅读
				if (data && data.id) reportArticleBrowse(data.id).catch(() => {})
				this.loading = false
			}
		}
	}
</script>

<style scoped>
	.page {
		display: flex;
		flex-direction: column;
		height: 100vh;
		background: #F8F8F8;
	}

	.body {
		flex: 1;
		box-sizing: border-box;
		padding: 20rpx;
	}

	.tip {
		padding: 60rpx 0;
		text-align: center;
		font-size: 28rpx;
		color: #999;
	}

	.article {
		background: #ffffff;
		border-radius: 16rpx;
		padding: 30rpx 24rpx;
	}

	.article-title {
		display: block;
		font-size: 36rpx;
		font-weight: 600;
		color: #333;
		line-height: 1.4;
	}

	.article-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 20rpx;
		margin: 16rpx 0 20rpx;
		font-size: 24rpx;
		color: #999;
	}

	.article-cover {
		width: 100%;
		margin-bottom: 20rpx;
		border-radius: 8rpx;
	}

	.article-content {
		font-size: 28rpx;
		line-height: 1.8;
		color: #333;
		word-break: break-word;
	}
</style>
