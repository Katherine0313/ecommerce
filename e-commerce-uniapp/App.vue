<script>
	import {
		ensureLogin
	} from './utils/auth.js'
	import {
		useCartStore
	} from './store/cart.js'
	import {
		useAiChatStore
	} from './store/ai-chat.js'

	export default {
		globalData: {
			userInfo: null
		},
		onLaunch: function() {
			console.log('App Launch')
			// 首屏优先：登录和购物车角标都延后执行，不和首页的分类/推荐接口抢带宽。
			// 角标先用本地缓存立即显示（store 初始化时读取），登录成功后静默刷新；
			// 用户提前点加购/下单时，ensureLogin 内部有并发锁，不会重复登录
			setTimeout(() => {
				ensureLogin().then(() => useCartStore().refresh()).catch(() => {})
			}, 1000)
		},
		onShow: function() {
			console.log('App Show')
			// 切到别的 App 再回来：小程序定时器会暂停，人工会话要续上轮询
			try {
				useAiChatStore().resumeSession()
			} catch (e) {}
		},
		onHide: function() {
			console.log('App Hide')
		}
	}
</script>

<style lang="scss">
	/* @import 必须在样式表最前面，否则会被忽略 */
	@import url("./static/icon/iconfont.css");
	/* 第二套 iconfont（「我的」页专用） */
	@import url("./static/myvue-icon/iconfont.css");
	@import 'uview-plus/index.scss';

	/* 两套 iconfont 的 @font-face 都叫 "iconfont"，同名会互相覆盖：
	   后引入的那套把前一套的字形顶掉，底部导航的老图标就会变方框。
	   所以在两行 @import 之后按名字重新注册一次，把两套彻底分开。
	   ⚠️ 只要字体文件名还是 iconfont.woff2/woff/ttf，以后重新从 iconfont.cn
	   下载 static/myvue-icon 都不用动这里（下载的 css 里那句同名 @font-face
	   会被这里顶掉，不影响）。
	   换完图标若没生效，是字体文件名没变、开发者工具用了旧缓存，
	   清一次缓存重新编译即可。 */
	@font-face {
		font-family: "iconfont";
		src: url("./static/icon/iconfont.woff2") format("woff2"),
			url("./static/icon/iconfont.woff") format("woff"),
			url("./static/icon/iconfont.ttf") format("truetype");
	}

	@font-face {
		font-family: "myvue-icon";
		src: url("./static/myvue-icon/iconfont.woff2") format("woff2"),
			url("./static/myvue-icon/iconfont.woff") format("woff"),
			url("./static/myvue-icon/iconfont.ttf") format("truetype");
	}

	/* 上面那个 css 里只有 .iconfont 基类，myvue-icon 这个名字要自己配一个 */
	.myvue-icon {
		font-family: "myvue-icon" !important;
		font-size: 16px;
		font-style: normal;
		-webkit-font-smoothing: antialiased;
		-moz-osx-font-smoothing: grayscale;
	}

	/* 第三套 iconfont（删除/清空图标）：同样重命名 font-family，避免和前两套同名 "iconfont" 互相覆盖 */
	@font-face {
		font-family: "clear-icon";
		src: url("./static/clear-icon/iconfont.woff2") format("woff2"),
			url("./static/clear-icon/iconfont.woff") format("woff"),
			url("./static/clear-icon/iconfont.ttf") format("truetype");
	}

	.clear-icon {
		font-family: "clear-icon" !important;
		font-size: 16px;
		font-style: normal;
		-webkit-font-smoothing: antialiased;
		-moz-osx-font-smoothing: grayscale;
	}

	/* 删除图标（class 名来自 clear-icon/iconfont.css） */
	.icon-shanchuqingchuqingkong:before {
		content: "\e61f";
	}

	/*每个页面公共css */
</style>
