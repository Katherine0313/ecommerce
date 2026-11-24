import App from './App'
// 自定义顶部导航栏，全局注册后各页面直接用 <CustomNavbar />
import CustomNavbar from './pages/index/component/top-bar.vue'
// 自定义底部导航栏，全局注册后各 tabBar 页直接用 <CustomTabBar />
import CustomTabBar from './pages/index/component/tab-bar.vue'
// 自定义轮播图，全局注册后各页面直接用 <CustomSwiper />
import CustomSwiper from './components/custom-swiper.vue'
// 猜你喜欢，用于首页和购物车
import Guess from './components/Guess.vue'
// 登录弹窗，未登录点加购时弹出，静默登录后继续加购
import LoginPopup from './components/login-popup.vue'
// 数量步进器（- / 可输入数字 / +），购物车页与加购弹窗共用
import QuantityStepper from './components/quantity-stepper.vue'
// 收货地址弹窗（列表 + 新增/编辑），下单/我的等页面共用
import AddressPopup from './components/address-popup.vue'
// 全局悬浮客服入口（右侧中间悬浮球 + 聊天面板）
import FloatingChat from './components/floating-chat/floating-chat.vue'
// #ifndef VUE3
import Vue from 'vue'
import './uni.promisify.adaptor'
Vue.config.productionTip = false
Vue.component('CustomNavbar', CustomNavbar)
Vue.component('CustomTabBar', CustomTabBar)
Vue.component('CustomSwiper',CustomSwiper)
Vue.component('Guess',Guess)
Vue.component('LoginPopup', LoginPopup)
Vue.component('QuantityStepper', QuantityStepper)
Vue.component('AddressPopup', AddressPopup)
Vue.component('FloatingChat', FloatingChat)
App.mpType = 'app'
const app = new Vue({
	...App
})
app.$mount()
// #endif

// #ifdef VUE3
import {
	createSSRApp
} from 'vue'
import {
	createPinia
} from 'pinia'
import uviewPlus from 'uview-plus'
export function createApp() {
	const app = createSSRApp(App)
	// 全局注册自定义导航栏组件
	app.component('CustomNavbar', CustomNavbar)
	// 全局注册自定义底部导航栏组件
	app.component('CustomTabBar', CustomTabBar)
	// 全局注册自定义轮播图组件
	app.component('CustomSwiper',CustomSwiper)
	// 全局注册猜你喜欢组件
	app.component('Guess',Guess)
	// 全局注册登录弹窗组件
	app.component('LoginPopup', LoginPopup)
	// 全局注册数量步进器组件
	app.component('QuantityStepper', QuantityStepper)
	// 全局注册收货地址弹窗组件
	app.component('AddressPopup', AddressPopup)
	// 全局注册悬浮客服组件
	app.component('FloatingChat', FloatingChat)
	// 注册 uview-plus 组件库（u-switch 等）
	app.use(uviewPlus)
	// 创建 Pinia 实例并注册，统一管理全局状态
	const pinia = createPinia()
	app.use(pinia)
	return {
		app
	}
}
// #endif
