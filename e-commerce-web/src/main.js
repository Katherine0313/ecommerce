import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
// 导入路由权限守卫（登录/权限控制）
import './router/permission'
import stepin from 'stepin/es';
// 导入Pinia实例（已在 store/pinia.js 中注册持久化插件）
import pinia from '@/store/pinia';
// import '@/mock';
// 生产打包时可去除 ant-design-vue/dist/antd.variable.less 的引用。
// 开发引入此包是为了加载优化，防止首次打开页面过慢
import 'ant-design-vue/dist/antd.variable.less';
import 'stepin/es/style';
// import 'default-passive-events';
import '@/theme/index.less';
import { AuthPlugin, IconfontPlugin } from '@/plugins';
import lazy from '@/directives/lazy';
import permission from '@/directives/permission';

// 发版后旧页面再懒加载 chunk 会 404（旧 hash 文件已被新版本覆盖），
// 这里自动刷新一次拿最新 index.html；sessionStorage 标记防止刷新死循环。
window.addEventListener('vite:preloadError', (event) => {
  event.preventDefault();
  if (!sessionStorage.getItem('chunk-reloaded')) {
    sessionStorage.setItem('chunk-reloaded', '1');
    window.location.reload();
  }
});

// 消除 Chrome 的「Permissions policy violation: unload is not allowed」告警：
// stepin 的 TabsView 仍在 window 上监听已废弃的 unload 事件。
// 在应用挂载前把 unload 监听重定向到官方推荐的等价事件 pagehide（刷新/关闭/跳转都会触发），
// 既保留页签缓存逻辑，又不再触发告警。add/remove 成对改写，保证卸载时能正常解绑。
const nativeAddEventListener = window.addEventListener;
const nativeRemoveEventListener = window.removeEventListener;
const toNativeEvent = (type) => (type === 'unload' ? 'pagehide' : type);
window.addEventListener = function (type, listener, options) {
  return nativeAddEventListener.call(this, toNativeEvent(type), listener, options);
};
window.removeEventListener = function (type, listener, options) {
  return nativeRemoveEventListener.call(this, toNativeEvent(type), listener, options);
};

const app = createApp(App);
// 注册图片懒加载指令 v-lazy（用于列表缩略图，避免全量请求）
app.directive('lazy', lazy);
// 注册按钮权限指令 v-permission（按 JWT 中的 permissions 控制显隐）
app.directive('permission', permission);

// 注册Pinia到Vue应用
app.use(pinia)
// 注册路由到Vue应用
app.use(router);
app.use(stepin, { router });
app.use(AuthPlugin, { action: 'disable' });
// iconfont 插件。url为你的 iconfont 图标资源地址（你的iconfont 仓库可获取此地址）
app.use(IconfontPlugin, {
  url: '//at.alicdn.com/t/c/font_3805284_ulvha6ct7d.js',
});
app.config.errorHandler = function (err) {
  console.error('未捕获的异常，', err);
};
app.mount('#stepin-app');

// 首屏加载层：等首条路由解析完成（页面真正可渲染）后再淡出，避免白屏闪烁
const bootLoader = document.getElementById('boot-loader');
let bootHidden = false;
function hideBootLoader() {
  if (bootHidden || !bootLoader) return;
  bootHidden = true;
  bootLoader.classList.add('is-done');
  setTimeout(() => bootLoader.remove(), 260);
}
router
    .isReady()
    .then(() => {
      // 等两帧，确保首屏内容已经绘制到屏幕上
      requestAnimationFrame(() => requestAnimationFrame(hideBootLoader));
    })
    .catch(hideBootLoader);
// 兜底：极端情况下也不让加载层一直挡着（正常 1 秒内就会淡出）
setTimeout(hideBootLoader, 15000);
