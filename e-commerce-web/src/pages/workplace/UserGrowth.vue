<template>
  <div class="user-growth card p-lg">
    <OverviewTitle title="用户增长" :subtitle="periodLabel" />
    <!-- 折线（用户总量）+ 柱状（每日新增），共用一根纵轴（不双轴）：
         双轴各自缩放会把「新增」画得比「总量」还高，看图的人没法换算，纯误导。
         代价是量级小的每日新增柱子偏矮，但总量曲线不会被误读 -->
    <!-- 接口报错时明确提示「加载失败」，不要退化成「暂无数据」：
         后者会被当成这段时间真没注册，误导运营判断 -->
    <div v-if="error" class="user-growth-error">
      <a-empty description="用户统计加载失败，请刷新重试" />
    </div>
    <line-chart v-else :series="series" />
  </div>
</template>
<script>
  import { defineComponent } from 'vue';
  import dayjs from 'dayjs';
  import LineChart from '@/components/chart/LineChart.vue';
  import OverviewTitle from '@/components/statistic/OverviewTitle.vue';
  import { PERIODS } from './useWorkplaceData';
  export default defineComponent({
    components: { LineChart, OverviewTitle },
    name: 'UserGrowth',
    props: {
      // { bucketType: 'hour' | 'day' | 'month', buckets: [...], newUsers: [...], cumulativeUsers: [...], totalUsers: 123 }
      data: {
        type: Object,
        default: () => ({ bucketType: '', buckets: [], newUsers: [], cumulativeUsers: [], totalUsers: 0 }),
      },
      // 全局报表时间维度，仅用于副标题
      period: { type: String, default: 'week' },
      // 接口是否加载失败：失败时显示错误提示，不显示「暂无数据」
      error: { type: Boolean, default: false },
    },
    computed: {
      periodLabel() {
        return PERIODS.find((p) => p.value === this.period)?.label || '';
      },
      // 折线 + 柱状，共用一根纵轴：
      // 用户总量是连续存量，走折线；每日新增是离散量，走柱状，柱子高度就是那一天的量。
      // 不做双轴：双轴各自缩放会把每日新增拉高到盖过总量线，纯误导
      series() {
        const { bucketType, buckets = [], newUsers = [], cumulativeUsers = [], totalUsers = 0 } = this.data;
        const nums = newUsers.map((n) => Number(n) || 0);
        const labels = buckets.map((b) => this.formatBucket(b, bucketType));
        // 累计存量曲线：优先用后端返回的 cumulativeUsers；老接口没这个字段时用 totalUsers 反推兜底
        let total;
        if (Array.isArray(cumulativeUsers) && cumulativeUsers.length) {
          total = cumulativeUsers.map((n) => Number(n) || 0);
        } else {
          let acc = (Number(totalUsers) || 0) - nums.reduce((s, n) => s + n, 0);
          total = nums.map((n) => (acc += n));
        }
        return [
          { name: '用户总量', type: 'line', data: labels.map((l, i) => [l, total[i] ?? 0]) },
          { name: '每日新增', type: 'bar', data: labels.map((l, i) => [l, nums[i] ?? 0]) },
        ];
      },
    },
    methods: {
      // 小时桶已是 HH:00；月桶显示「M月」；日桶 yyyy-MM-dd → MM-dd
      formatBucket(bucket, type) {
        if (type === 'hour') return bucket;
        if (type === 'month') return dayjs(`${bucket}-01`).format('M月');
        return String(bucket).slice(5);
      },
    },
  });
</script>
<style scoped lang="less">
  .user-growth {
    :deep(.line-chart) {
      @apply -mt-10;
    }

    // 错误态占位：高度对齐图表区（400/340/260），避免整块塌下去
    .user-growth-error {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 400px;

      @media (max-width: 1200px) {
        height: 340px;
      }

      @media (max-width: 767px) {
        height: 260px;
      }
    }

    // 手机端图例移到底部，负 margin 会压住标题
    @media (max-width: 767px) {
      :deep(.line-chart) {
        margin-top: -8px;
      }
    }
  }
</style>
