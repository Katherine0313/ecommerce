<template>
  <div
    class="mini-statistic-card overflow-hidden relative min-h-[112px] bg-container inline-flex items-center justify-between drop-shadow-sm p-md border-border rounded-lg"
  >
    <!-- pr-2：给右下角的装饰 icon 让出 8px，文字再长也不会贴上去 -->
    <div class="statistic-main flex-1 pr-2">
      <div class="statistic-title text-subtext text-base">{{ title }}</div>
      <div class="statistic-content flex items-baseline">
        <span class="value text-title text-xxl font-bold">{{ value }}</span>
        <!-- 原来这里写死 '+30%'。改成 prop：不传/空串就不渲染，
             算不出环比的指标（快照类）不会显示假涨幅 -->
        <span
          v-if="hasChange"
          class="suffix ml-1 text-xs font-bold"
          :class="down ? 'text-error-500' : 'text-green-500'"
          >{{ change }}</span
        >
      </div>
    </div>
    <div class="statistic-icon absolute bottom-0 right-0">
      <slot name="icon"></slot>
    </div>
  </div>
</template>

<script>
  import { defineComponent } from 'vue';
  export default defineComponent({
    props: {
      title: String,
      value: [String, Number],
      // 涨跌幅文案，如 '+31%'；不传 = 该指标算不出环比。prop 名与 OverviewTitle 对齐
      change: { type: [String, Number], default: '' },
      up: { type: Boolean, default: false },
      down: { type: Boolean, default: false },
    },
    name: 'MiniStatisticCard',
    computed: {
      hasChange() {
        return this.change !== '' && this.change !== null && this.change !== undefined;
      },
    },
  });
</script>

<style lang="less" scoped>
  .mini-statistic-card {
  }
</style>
