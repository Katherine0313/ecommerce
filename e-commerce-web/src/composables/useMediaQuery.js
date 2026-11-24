import { onBeforeUnmount, ref } from 'vue';

/**
 * 媒体查询 composable：返回一个随视口变化的响应式布尔值
 * @param {String} query 例如 '(max-width: 767px)'
 * @returns {import('vue').Ref<Boolean>}
 */
export function useMediaQuery(query) {
  const mql = window.matchMedia(query);
  const matches = ref(mql.matches);

  // 统一处理新旧 Safari 的监听 API
  const update = (e) => {
    matches.value = e.matches;
  };
  if (mql.addEventListener) {
    mql.addEventListener('change', update);
  } else {
    mql.addListener(update);
  }

  // 组件卸载时务必移除监听，避免 keep-alive 反复挂载后泄漏
  onBeforeUnmount(() => {
    if (mql.removeEventListener) {
      mql.removeEventListener('change', update);
    } else {
      mql.removeListener(update);
    }
  });

  return matches;
}

/**
 * 手机断点：≤767px（与 theme/style/responsive.less 保持一致）
 */
export function useIsMobile() {
  return useMediaQuery('(max-width: 767px)');
}
