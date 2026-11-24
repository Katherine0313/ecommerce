<script setup>
import { computed } from 'vue'
import { formatPrice } from '@/utils/format.js'

const props = defineProps({
  coupon: { type: Object, required: true },
})

// 折扣券：discountRate 可能是 0.9（九折）也可能存成 90
const isDiscount = computed(() => props.coupon.type === 'DISCOUNT' || !!props.coupon.discountRate)

const faceValueText = computed(() => {
  if (isDiscount.value) {
    const rate = Number(props.coupon.discountRate)
    return (rate > 1 ? rate / 10 : rate * 10).toFixed(1)
  }
  return formatPrice(props.coupon.faceValue)
})

const thresholdText = computed(() => {
  const threshold = Number(props.coupon.thresholdAmount || 0)
  return threshold > 0 ? `满 ${threshold} 元可用` : '无门槛'
})

// 库存进度：已抢百分比
const soldPercent = computed(() => {
  const total = Number(props.coupon.totalQuantity || 0)
  const remain = Number(props.coupon.remainQuantity || 0)
  if (total <= 0) return 0
  return Math.min(100, Math.max(0, Math.round(((total - remain) / total) * 100)))
})

const soldText = computed(() => {
  const total = Number(props.coupon.totalQuantity || 0)
  const remain = Number(props.coupon.remainQuantity || 0)
  return `已抢 ${Math.max(0, total - remain)} / ${total}`
})
</script>

<template>
  <article class="seckill-card">
    <!-- 左上角「秒杀」角标 -->
    <span class="card-badge">秒杀</span>

    <!-- 券面值 + 类型 -->
    <p class="card-face">
      <span v-if="!isDiscount" class="face-unit">¥</span>
      <span class="face-value">{{ faceValueText }}</span>
      <span v-if="isDiscount" class="face-unit">折</span>
      <span class="face-type">{{ coupon.type === 'DISCOUNT' ? '折扣券' : '代金券' }}</span>
    </p>

    <p class="card-name text-ellipsis">{{ coupon.name }}</p>
    <p class="card-threshold">{{ thresholdText }}</p>

    <!-- 秒杀价 + 划线券面价 -->
    <div class="card-price">
      <span class="price">¥{{ formatPrice(coupon.seckillPrice) }}</span>
      <span class="origin">¥{{ formatPrice(coupon.faceValue) }}</span>
    </div>

    <!-- 库存进度 -->
    <div class="stock">
      <div class="stock-bar">
        <span class="stock-fill" :style="{ width: soldPercent + '%' }"></span>
      </div>
      <span class="stock-text">{{ soldText }}</span>
    </div>

    <router-link :to="`/coupon/seckill/${coupon.couponId}`" class="grab-btn">马上抢</router-link>
  </article>
</template>

<style scoped>
.seckill-card {
  /* 固定宽度 + flex 布局：几张排几张，1 张也不拉伸
     210px：5 张 + 4 个 16px 间距 = 1114px，正好放得进楼层 1132px 的内宽 */
  flex: 0 0 210px;
  width: 210px;
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 14px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-sizing: border-box;
  transition: transform 0.2s, box-shadow 0.2s;
}

.seckill-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

/* 左上角「秒杀」角标 */
.card-badge {
  position: absolute;
  top: 0;
  left: 0;
  padding: 1px 8px;
  font-size: 12px;
  color: #fff;
  background: var(--color-primary);
  border-radius: 0 0 8px 0;
}

.card-face {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin: 14px 0 0;
  font-family: var(--font-num);
  font-weight: 700;
  color: var(--color-primary);
}

.face-value {
  font-size: 24px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.face-unit {
  font-size: 13px;
  font-weight: 500;
}

.face-type {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary);
}

.card-name {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-threshold {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

/* 秒杀价 + 划线券面价 */
.card-price {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 10px;
}

.price {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.origin {
  font-size: 12px;
  color: var(--text-secondary);
  text-decoration: line-through;
}

/* 库存进度 */
.stock {
  margin-top: 10px;
}

.stock-bar {
  height: 6px;
  border-radius: 3px;
  background: var(--bg-hover);
  overflow: hidden;
}

.stock-fill {
  display: block;
  height: 100%;
  background: var(--color-primary);
  border-radius: 3px;
}

.stock-text {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

/* 抢购按钮 */
.grab-btn {
  display: block;
  margin-top: 12px;
  height: 36px;
  line-height: 36px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: var(--color-primary);
  border-radius: var(--radius-sm);
  transition: background 0.15s;
}

.grab-btn:hover {
  background: var(--color-primary-dark);
}

/* 窄屏：卡片固定宽度略收窄，避免一行放不下 */
@media (max-width: 768px) {
  .seckill-card {
    flex-basis: 180px;
    width: 180px;
  }
}
</style>
