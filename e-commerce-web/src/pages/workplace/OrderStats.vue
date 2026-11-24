<template>
  <div class="order-stats card p-lg">
    <OverviewTitle
      title="订单统计"
      :subtitle="periodLabel"
      :change="change"
      :up="up"
      :down="down"
    />
    <line-chart :series="series" />
  </div>
</template>
<script>
  import { defineComponent } from 'vue';
  import LineChart from '@/components/chart/LineChart.vue';
  import OverviewTitle from '@/components/statistic/OverviewTitle.vue';
  import { PERIODS } from './useWorkplaceData';
  export default defineComponent({
    components: { LineChart, OverviewTitle },
    name: 'OrderStats',
    props: {
      // [{ name: '待付款订单', data: [['10-18', 3], ...] }, ...]
      series: { type: Array, default: () => [] },
      // 全局报表时间维度，仅用于副标题
      period: { type: String, default: 'week' },
      change: { type: String, default: '' },
      up: { type: Boolean, default: false },
      down: { type: Boolean, default: false },
    },
    computed: {
      periodLabel() {
        return PERIODS.find((p) => p.value === this.period)?.label || '';
      },
    },
  });
</script>
<style scoped lang="less">
  .order-stats {
    :deep(.line-chart) {
      @apply -mt-10;
    }

    // 手机端图例移到底部，负 margin 会压住标题
    @media (max-width: 767px) {
      :deep(.line-chart) {
        margin-top: -8px;
      }
    }
  }
</style>
