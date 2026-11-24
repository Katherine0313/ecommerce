<template>
  <div class="sales-ranking card p-lg">
    <overview-title title="销量排名" :subtitle="periodLabel" />
    <div v-if="list.length" class="rank-list">
      <div v-for="(item, i) in list" :key="item.productId" class="rank-item">
        <!-- 前三名单独配色，其余用普通序号 -->
        <span class="rank-no" :class="`rank-no-${i + 1}`">{{ i + 1 }}</span>
        <img v-lazy="item.image" class="rank-img" alt="" />
        <span class="rank-name" :title="item.productName">{{
          item.productName
        }}</span>
        <span class="rank-qty"
          >{{ formatThousand(item.salesQuantity, 0) }} 件</span
        >
      </div>
    </div>
    <a-empty v-else description="暂无销量数据" class="py-lg" />
  </div>
</template>
<script setup>
  import { computed } from 'vue';
  import OverviewTitle from '@/components/statistic/OverviewTitle.vue';
  import { formatThousand } from '@/utils/formatter';
  import { PERIODS } from './useWorkplaceData';

  const props = defineProps({
    // 后端 SalesRankingItemVO：[{ productId, productName, image, salesQuantity }]
    list: { type: Array, default: () => [] },
    // 与营业额共用同一时间维度，仅用于副标题
    period: { type: String, default: 'week' },
  });

  const periodLabel = computed(
    () => PERIODS.find((p) => p.value === props.period)?.label || ''
  );
</script>
<style scoped lang="less">
  .sales-ranking {
    @apply flex flex-col;
    .rank-list {
      @apply flex-1 flex flex-col mt-md;
    }
    .rank-item {
      @apply flex items-center py-xs;
      .rank-no {
        @apply w-6 text-center text-sm font-bold text-subtext;
        &-1 {
          color: #f5a623;
        }
        &-2 {
          color: #8c8c8c;
        }
        &-3 {
          color: #cd7f32;
        }
      }
      .rank-img {
        @apply w-9 h-9 rounded object-cover mx-sm;
        // 快照可能没有图，用浅灰底兜底，避免空白
        background: #f0f0f0;
      }
      .rank-name {
        @apply flex-1 min-w-0 truncate text-sm;
      }
      .rank-qty {
        @apply ml-sm text-sm font-bold text-primary-500 whitespace-nowrap;
      }
    }
  }
</style>
