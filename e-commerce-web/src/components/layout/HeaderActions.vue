<script setup>
  import { computed } from 'vue';
  import { useRouter } from 'vue-router';
  import { StepinHeaderAction } from 'stepin';
  import DayNightSwitch from '@/components/switch/DayNightSwitch.vue';
  import { BellOutlined, GithubOutlined } from '@ant-design/icons-vue';
  import { unreadOrderCount, clearUnreadOrders } from '@/utils/orderNotify';
  import Fullscreen from '../fullscreen/Fullscreen.vue';

  defineEmits(['showSetting']);

  const router = useRouter();
  // 顶栏铃铛未读角标：来自来单提醒 WebSocket 计数
  const unread = computed(() => unreadOrderCount.value);

  // 点击铃铛：清空未读，跳订单列表查看新订单
  const goOrders = () => {
    clearUnreadOrders();
    router.push('/orders/order-list');
  };
</script>
<template>
  <StepinHeaderAction class="header-action-daynight">
    <DayNightSwitch />
  </StepinHeaderAction>
  <!-- GitHub 入口：项目仓库地址 -->
  <StepinHeaderAction class="header-action-github">
    <a
      class="action-item"
      href="https://github.com/Katherine0313/ecommerce.git"
      target="_blank"
      rel="noopener"
    >
      <GithubOutlined />
    </a>
  </StepinHeaderAction>
  <StepinHeaderAction class="header-action-setting">
    <div class="action-item setting" @click="$emit('showSetting')">
      <SettingOutlined />
    </div>
  </StepinHeaderAction>
  <StepinHeaderAction class="header-action-notice">
    <div class="action-item notice" @click="goOrders">
      <a-badge :count="unread" :overflow-count="99">
        <BellOutlined />
      </a-badge>
    </div>
  </StepinHeaderAction>
  <StepinHeaderAction class="header-action-fullscreen">
    <Fullscreen
      class="-mx-xs -my-sm h-[56px] px-xs py-sm flex items-center"
      target=".stepin-layout"
    />
  </StepinHeaderAction>
</template>
<style scoped lang="less">
  .action-item {
    font-size: 20px;
    height: 100%;
    margin: 0 -8px;
    padding: 0 4px;
    line-height: 40px;
    display: flex;
    align-items: center;

    &.setting {
      font-size: 18px;
    }

    &.notice {
      cursor: pointer;

      /* a-badge 自带 font-size:14px，必须穿透覆盖内部图标才会生效 */
      :deep(.anticon) {
        font-size: 24px;
        line-height: 1;
      }
    }
  }
</style>
