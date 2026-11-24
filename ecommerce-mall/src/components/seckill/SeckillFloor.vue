<script setup>
import { ref, computed, onMounted } from 'vue'
import { getSeckillList } from '@/api/coupon.js'
import { useCountdown } from '@/components/seckill/useCountdown.js'
import SeckillCard from '@/components/seckill/SeckillCard.vue'

// 只展示「进行中」的活动：activityStatus === 'ACTIVE'
const coupons = ref([])
const loading = ref(true)

const activeCoupons = computed(() =>
  coupons.value.filter((c) => c.activityStatus === 'ACTIVE')
)

// 倒计时目标：最早结束的活动
const target = computed(() => {
  if (!activeCoupons.value.length) return null
  return activeCoupons.value.reduce(
    (min, c) => (!min || c.seckillEndTime < min ? c.seckillEndTime : min),
    null
  )
})

const { remainText } = useCountdown(target)

onMounted(() => {
  getSeckillList({ page: 1, pageSize: 10 })
    .then((res) => {
      coupons.value = res?.list || []
    })
    .catch(() => {
      coupons.value = []
    })
    .finally(() => {
      loading.value = false
    })
})
</script>

<template>
  <!-- 无进行中的活动时整个楼层隐藏，不占位置 -->
  <section v-if="!loading && activeCoupons.length" class="seckill-floor">
    <div class="floor-head">
      <div class="floor-title">
        <span class="title-text">🔥 限时秒杀</span>
        <span class="title-sub">低价抢券</span>
      </div>
      <div class="floor-right">
        <span class="cd-label">距结束</span>
        <span class="cd-time">{{ remainText() }}</span>
        <router-link to="/coupons" class="more-link">更多 ›</router-link>
      </div>
    </div>

    <!-- 卡片自适应网格：固定宽度 + 自动换行，有几张排几张 -->
    <div class="card-wrap">
      <SeckillCard v-for="coupon in activeCoupons" :key="coupon.couponId" :coupon="coupon" />
    </div>
  </section>
</template>

<style scoped>
/* 深色渐变底 + 金色标题 */
.seckill-floor {
  margin-top: var(--space-lg);
  padding: var(--space-md) var(--space-lg) var(--space-lg);
  background: linear-gradient(135deg, #2b1a10 0%, #4a2410 100%);
  border-radius: var(--radius-lg);
}

.floor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
}

.floor-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.title-text {
  font-size: 20px;
  font-weight: 700;
  color: #ffd666;
}

.title-sub {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
}

.floor-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cd-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.cd-time {
  padding: 2px 10px;
  font-size: 13px;
  font-weight: 600;
  color: #ffd666;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}

.more-link {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
  text-decoration: none;
}

.more-link:hover {
  color: #ffd666;
}

/* 卡片网格：固定宽度 + 自动换行，左对齐 */
.card-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

@media (max-width: 768px) {
  .seckill-floor {
    padding: var(--space-md) var(--space-sm) var(--space-md);
  }

  .floor-head {
    flex-wrap: wrap;
    gap: 8px;
  }

  .title-text {
    font-size: 17px;
  }
}
</style>
