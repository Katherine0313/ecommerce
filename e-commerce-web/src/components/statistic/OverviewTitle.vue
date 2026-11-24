<template>
  <div class="overview-title">
    {{ title }}
    <div class="subtitle">
      {{ subtitle }}
      <span :class="{ change: true, up, down }">{{ change }}</span>
    </div>
  </div>
</template>
<script>
  import { defineComponent } from 'vue';
  export default defineComponent({
    name: 'OverviewTitle',
    props: {
      title: String,
      subtitle: String,
      change: [String, Number],
      up: { type: Boolean, required: false },
      down: { type: Boolean, required: false },
    },
    setup(props, { attrs, slots, emit }) {},
  });
</script>
<style scoped lang="less">
  .overview-title {
    // 主标题 16px → 32px，副标题 12px → 24px（工作台报表统一放大一倍）
    @apply text-title font-bold;
    font-size: 18px;
    line-height: 28px;
    .subtitle {
      @apply text-subtext;
      font-size: 14px;
      line-height: 24px;
      .change {
        @apply text-primary-500 ml-xs;
        font-size: 18px;
        &.up {
          @apply text-success-500;
        }
        &.down {
          @apply text-error-500;
        }
      }
    }
  }
</style>
