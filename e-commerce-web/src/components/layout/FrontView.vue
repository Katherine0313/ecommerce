<script setup>
  import { LogoutOutlined } from '@ant-design/icons-vue';
  import { ref, onMounted } from 'vue';
  import useThemeStore from 'stepin/es/theme-editor/store';
  import { alert } from 'stepin';
  const { setBgSeriesColors, setPrimaryColor } = useThemeStore();
  onMounted(() => {
    setBgSeriesColors({ 'bg-base': '#003f8c' }, true, '.front-main');
    setPrimaryColor({ DEFAULT: '#1896ff' }, '.front-main');
    alert.info(
      `<div class="text-text">
        <span class="underline"></span>.
      </div>`,
      { renderRaw: true, duration: -1 }
    );
  });
  const showMessage = ref(true);
  const message = ref();
  function onClose() {
    message.value.setAttribute(
      'style',
      `margin-top: ${0 - message.value.clientHeight}px`
    );
    setTimeout(() => (showMessage.value = false), 150);
  }
</script>
<template>
  <div class="front-view flex flex-col">
    <div class="front-main bg-[#003f8c] text-text flex-1">
      <div class="front-header flex items-baseline py-md px-xl">
        <!-- 顶部导航已移除，用占位保持右侧按钮位置不变 -->
        <div class="flex-1"></div>
        <div>
          <router-link
            to="/front/login"
            class="h-[46px] border-transparent hover:text-text hover:border-transparent text-lg text-text"
          >
            <LogoutOutlined class="mr-xs" />
            Sign In
          </router-link>
          <a-button
            class="ml-md px-lg border-text hover:border-text hover:bg-text border-2 h-[46px] hover:text-bg-container"
            size="large"
            >Get Started</a-button
          >
        </div>
      </div>
      <div class="front-content px-xl">
        <router-view />
      </div>
    </div>
  </div>
</template>
<style lang="less" scoped>
  .front-view {
    .front-content {
      min-height: calc(100vh - 78px);
    }

    // 手机：减小 32px 的页面留白，右侧按钮允许换行
    @media (max-width: 767px) {
      .front-header {
        flex-wrap: wrap;
        gap: 8px;
        padding: 12px;
      }
      .front-content {
        padding-left: 12px;
        padding-right: 12px;
        min-height: calc(100dvh - 78px);
      }
    }
  }
</style>
