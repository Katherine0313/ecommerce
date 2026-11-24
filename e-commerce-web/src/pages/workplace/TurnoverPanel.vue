<template>
  <div class="turnover-panel card p-lg">
    <div class="flex items-start justify-between">
      <OverviewTitle
        title="营业额"
        :subtitle="turnover.timeDesc || periodLabel"
      />
    </div>
    <div class="turnover-value flex items-baseline mt-md">
      <span class="text-base">￥</span>
      <span class="text-xxl font-bold">{{
        formatThousand(turnover.turnover, 2)
      }}</span>
    </div>
    <!-- 昨日用「前日 vs 昨日」双柱对比，比单点折线更有信息量 -->
    <bar-chart
      v-if="period === 'yesterday'"
      :color="['#ffffff']"
      :list="compareList"
      :y-axis-interval="2000"
    />
    <line-chart v-else :series="series" :y-axis-interval="2000" />
  </div>
</template>
<script setup>
  import { computed } from 'vue';
  import BarChart from '@/components/chart/BarChart.vue';
  import LineChart from '@/components/chart/LineChart.vue';
  import OverviewTitle from '@/components/statistic/OverviewTitle.vue';
  import { formatThousand } from '@/utils/formatter';
  import { PERIODS } from './useWorkplaceData';

  const props = defineProps({
    // 后端 TurnoverVO：{ timeDesc, turnover }
    turnover: {
      type: Object,
      default: () => ({ timeDesc: '', turnover: 0 }),
    },
    // 后端 TurnoverReportVO：{ dateList, turnoverList }
    report: {
      type: Object,
      default: () => ({ dateList: [], turnoverList: [] }),
    },
    period: { type: String, default: 'week' },
  });

  const periodLabel = computed(
    () => PERIODS.find((p) => p.value === props.period)?.label || ''
  );

  // 昨日维度的双柱：report.turnoverList = [前日, 昨日]
  const compareList = computed(() => {
    const values = props.report?.turnoverList || [];
    if (values.length < 2) return [];
    return [
      ['前日', Number(values[0]) || 0],
      ['昨日', Number(values[1]) || 0],
    ];
  });

  // dateList 是 yyyy-MM-dd，X 轴只显示 MM-dd
  const series = computed(() => [
    {
      name: '营业额',
      data: (props.report?.dateList || []).map((d, i) => [
        String(d).slice(5),
        Number(props.report.turnoverList?.[i]) || 0,
      ]),
    },
  ]);

</script>
<style scoped lang="less">
  .turnover-panel {
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
