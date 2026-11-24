<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSeckillDetail, createSeckillOrder, paySeckillOrder } from '@/api/coupon.js'
import { formatPrice } from '@/utils/format.js'
import { useUserStore } from '@/stores/user.js'
import { useCountdown } from '@/components/seckill/useCountdown.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = ref(null)
const loading = ref(true)
const submitting = ref(false)
const paying = ref(false)

const isDiscount = computed(() => detail.value?.type === 'DISCOUNT' || !!detail.value?.discountRate)

const faceValueText = computed(() => {
  if (!detail.value) return '0'
  if (isDiscount.value) {
    const rate = Number(detail.value.discountRate)
    return (rate > 1 ? rate / 10 : rate * 10).toFixed(1)
  }
  return formatPrice(detail.value.faceValue)
})

const thresholdText = computed(() => {
  const threshold = Number(detail.value?.thresholdAmount || 0)
  return threshold > 0 ? `满 ${threshold} 元可用` : '无门槛'
})

// 倒计时：进行中倒到活动结束，未开始倒到活动开始
const countTarget = computed(() => {
  if (!detail.value) return null
  if (detail.value.activityStatus === 'NOT_STARTED') return detail.value.seckillBeginTime
  return detail.value.seckillEndTime
})

const { remainText } = useCountdown(countTarget)

const statusText = computed(() => {
  const map = {
    NOT_STARTED: '未开始',
    ACTIVE: '进行中',
    ENDED: '已结束',
    STOPPED: '已停止',
  }
  return map[detail.value?.activityStatus] || '—'
})

const soldPercent = computed(() => {
  const total = Number(detail.value?.totalQuantity || 0)
  const remain = Number(detail.value?.remainQuantity || 0)
  if (total <= 0) return 0
  return Math.min(100, Math.max(0, Math.round(((total - remain) / total) * 100)))
})

// 只有进行中且有库存才能买
const canBuy = computed(
  () =>
    detail.value?.activityStatus === 'ACTIVE' &&
    (Number(detail.value?.remainQuantity) || 0) > 0
)

const btnText = computed(() => {
  if (!detail.value) return '加载中'
  if (detail.value.activityStatus === 'NOT_STARTED') return '活动未开始'
  if (detail.value.activityStatus === 'ENDED') return '活动已结束'
  if (detail.value.activityStatus === 'STOPPED') return '活动已停止'
  if ((Number(detail.value.remainQuantity) || 0) <= 0) return '已抢光'
  return '立即抢购'
})

onMounted(() => {
  const id = route.params.id
  if (!id) {
    router.replace('/coupons')
    return
  }
  getSeckillDetail(id)
    .then((data) => {
      detail.value = data
    })
    .catch((e) => {
      ElMessage.error(e?.message || '秒杀券加载失败')
      router.replace('/coupons')
    })
    .finally(() => {
      loading.value = false
    })
})

// 抢购：游客先登录，登录后回本页再点抢购
async function handleBuy() {
  if (submitting.value || !canBuy.value) return
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  submitting.value = true
  try {
    // 下单成功即进入模拟支付
    const order = await createSeckillOrder(detail.value.couponId)
    await paySeckillOrder(order.orderNo, 'MOCK')
    ElMessage.success('抢购成功，优惠券已发放到「我的优惠券」')
    router.push('/user/seckill-orders')
  } catch (e) {
    ElMessage.error(e?.message || '抢购失败')
  } finally {
    submitting.value = false
    paying.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="container seckill-detail">
    <div v-if="detail" class="detail-wrap">
      <!-- 券主信息 -->
      <section class="hero">
        <p class="hero-face">
          <span v-if="!isDiscount" class="face-unit">¥</span>
          <span class="face-value">{{ faceValueText }}</span>
          <span v-if="isDiscount" class="face-unit">折</span>
          <span class="face-type">{{ detail.type === 'DISCOUNT' ? '折扣券' : '代金券' }}</span>
        </p>
        <h1 class="hero-name">{{ detail.name }}</h1>
        <div class="hero-price">
          <span class="price">¥{{ formatPrice(detail.seckillPrice) }}</span>
          <span class="origin">券面价 ¥{{ formatPrice(detail.faceValue) }}</span>
        </div>
      </section>

      <!-- 活动信息 -->
      <section class="info-card">
        <div class="info-row">
          <span class="info-label">活动状态</span>
          <span class="info-value">{{ detail.activityStatusDesc || statusText }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">活动时间</span>
          <span class="info-value">{{ detail.seckillBeginTime }} ~ {{ detail.seckillEndTime }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">{{ detail.activityStatus === 'NOT_STARTED' ? '距开始' : '距结束' }}</span>
          <span class="info-value countdown">{{ remainText() || '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">使用门槛</span>
          <span class="info-value">{{ thresholdText }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">剩余库存</span>
          <span class="info-value">{{ detail.remainQuantity }} / {{ detail.totalQuantity }} 张</span>
        </div>

        <!-- 库存进度 -->
        <div class="stock">
          <div class="stock-bar">
            <span class="stock-fill" :style="{ width: soldPercent + '%' }"></span>
          </div>
          <span class="stock-text">已抢 {{ Math.max(0, detail.totalQuantity - detail.remainQuantity) }} / {{ detail.totalQuantity }}</span>
        </div>
      </section>

      <!-- 使用说明 -->
      <section v-if="detail.description" class="info-card">
        <h3 class="card-title">使用说明</h3>
        <p class="desc-text">{{ detail.description }}</p>
      </section>

      <!-- 活动规则 -->
      <section class="info-card">
        <h3 class="card-title">活动规则</h3>
        <ul class="rule-list">
          <li>每人限购 1 张，先到先得</li>
          <li>下单后 15 分钟内完成支付，超时订单自动关闭</li>
          <li>支付成功后优惠券发放到「我的优惠券」</li>
          <li>未使用的券可在「我的秒杀订单」申请退款，由商家审核</li>
        </ul>
      </section>

      <!-- 底部操作栏 -->
      <div class="bottom-bar">
        <div class="bar-price">
          <span class="price">¥{{ formatPrice(detail.seckillPrice) }}</span>
          <span class="bar-tip">每人限购 1 张</span>
        </div>
        <el-button
          type="primary"
          size="large"
          class="buy-btn"
          :disabled="!canBuy"
          :loading="submitting"
          @click="handleBuy"
        >
          {{ btnText }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.seckill-detail {
  padding: var(--space-lg) 0 var(--space-xl);
}

.detail-wrap {
  max-width: 720px;
  margin: 0 auto;
}

/* 券主信息：品牌橙渐变 */
.hero {
  padding: var(--space-lg);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);
  border-radius: var(--radius-lg);
  color: #fff;
}

.hero-face {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin: 0;
  font-family: var(--font-num);
  font-weight: 700;
}

.face-value {
  font-size: 44px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.face-unit {
  font-size: 18px;
  font-weight: 500;
}

.face-type {
  margin-left: 8px;
  font-size: 13px;
  font-weight: 400;
  opacity: 0.85;
}

.hero-name {
  margin: var(--space-sm) 0 0;
  font-size: 19px;
  font-weight: 600;
}

.hero-price {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: var(--space-md);
}

.hero-price .price {
  font-size: 30px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.hero-price .origin {
  font-size: 13px;
  opacity: 0.8;
  text-decoration: line-through;
}

/* 信息卡 */
.info-card {
  margin-top: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.info-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.info-value {
  font-size: 13px;
  color: var(--text-primary);
}

.info-value.countdown {
  font-weight: 600;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

/* 库存进度 */
.stock {
  margin-top: var(--space-sm);
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
}

.stock-text {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.card-title {
  margin: 0 0 var(--space-sm);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.desc-text {
  margin: 0;
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.7;
}

.rule-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.9;
}

/* 底部操作栏 */
.bottom-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.bar-price {
  display: flex;
  flex-direction: column;
}

.bar-price .price {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.bar-tip {
  font-size: 12px;
  color: var(--text-secondary);
}

.buy-btn {
  min-width: 150px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 999px;
}

@media (max-width: 768px) {
  .bottom-bar {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-sm);
  }

  .buy-btn {
    width: 100%;
  }
}
</style>
