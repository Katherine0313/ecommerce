<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCouponCenter, claimCoupon, getSeckillList } from '@/api/coupon.js'
import { formatPrice } from '@/utils/format.js'
import { useUserStore } from '@/stores/user.js'
import SeckillCard from '@/components/seckill/SeckillCard.vue'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('receive')
const loading = ref(true)
const claiming = ref(false)

// 普通券（可领取）
const coupons = ref([])
// 秒杀券
const seckillCoupons = ref([])

const TABS = [
  { label: '普通券 · 免费领', value: 'receive' },
  { label: '限时秒杀 · 抢购', value: 'seckill' },
]

// 秒杀 tab 只展示进行中的活动
const activeSeckillCoupons = computed(() =>
  seckillCoupons.value.filter((c) => c.activityStatus === 'ACTIVE')
)

// 折扣券：discountRate 可能是 0.9（九折）也可能存成 90
function isDiscount(coupon) {
  return coupon.type === 'DISCOUNT' || coupon.discountRate
}

function percentOf(coupon) {
  const rate = Number(coupon.discountRate)
  return rate > 1 ? rate / 10 : rate * 10
}

function faceValue(coupon) {
  if (isDiscount(coupon)) return percentOf(coupon).toFixed(1)
  return formatPrice(coupon.faceValue)
}

function thresholdText(coupon) {
  const threshold = Number(coupon.thresholdAmount || 0)
  return threshold > 0 ? `满 ${threshold} 元可用` : '无门槛'
}

function validityText(coupon) {
  return coupon.validityDesc || ''
}

onMounted(() => {
  loadReceive()
  loadSeckill()
})

function loadReceive() {
  getCouponCenter()
    .then((list) => {
      coupons.value = list || []
    })
    .catch((e) => {
      ElMessage.error(e?.message || '优惠券加载失败')
    })
}

function loadSeckill() {
  getSeckillList({ page: 1, pageSize: 20 })
    .then((res) => {
      seckillCoupons.value = res?.list || []
    })
    .catch((e) => {
      ElMessage.error(e?.message || '秒杀券加载失败')
    })
    .finally(() => {
      loading.value = false
    })
}

// 领取普通券：游客先登录，登录后回本页再点领取
async function handleClaim(coupon) {
  if (claiming.value) return
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  claiming.value = true
  try {
    await claimCoupon(coupon.templateId)
    ElMessage.success('领取成功')
    loadReceive()
  } catch (e) {
    ElMessage.error(e?.message || '领取失败')
  } finally {
    claiming.value = false
  }
}
</script>

<template>
  <div class="container coupon-center">
    <h2 class="section-title">领券中心</h2>

    <el-tabs v-model="activeTab" class="center-tabs">
      <el-tab-pane v-for="tab in TABS" :key="tab.value" :label="tab.label" :name="tab.value" />
    </el-tabs>

    <!-- 普通券 -->
    <div v-if="activeTab === 'receive'" v-loading="loading">
      <el-empty v-if="!loading && !coupons.length" description="暂无可领取的优惠券" />
      <div class="receive-list">
        <article v-for="coupon in coupons" :key="coupon.templateId" class="receive-card">
          <div class="rc-left">
            <p class="rc-face">
              <span v-if="!isDiscount(coupon)" class="face-unit">¥</span>
              <span class="face-value">{{ faceValue(coupon) }}</span>
              <span v-if="isDiscount(coupon)" class="face-unit">折</span>
            </p>
            <p class="rc-scope">{{ coupon.scopeTypeDesc || '全场通用' }}</p>
          </div>

          <span class="rc-divider" aria-hidden="true"></span>

          <div class="rc-right">
            <p class="rc-name text-ellipsis">{{ coupon.name }}</p>
            <p class="rc-threshold">{{ thresholdText(coupon) }}</p>
            <p class="rc-time">{{ validityText(coupon) }}</p>
          </div>

          <div class="rc-action">
            <el-button v-if="coupon.claimed" type="info" size="small" disabled>已领取</el-button>
            <el-button v-else type="primary" size="small" :loading="claiming" @click="handleClaim(coupon)">
              立即领取
            </el-button>
            <span class="rc-remain">剩 {{ coupon.remainQuantity }} 张</span>
          </div>
        </article>
      </div>
    </div>

    <!-- 秒杀券 -->
    <div v-else v-loading="loading">
      <el-empty v-if="!loading && !activeSeckillCoupons.length" description="当前没有进行中的秒杀活动" />
      <div class="seckill-list">
        <SeckillCard v-for="coupon in activeSeckillCoupons" :key="coupon.couponId" :coupon="coupon" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.coupon-center {
  padding: var(--space-lg) 0 var(--space-xl);
}

.center-tabs {
  margin-bottom: var(--space-md);
}

/* 普通券卡片 */
.receive-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.receive-card {
  display: flex;
  align-items: center;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  background: var(--bg-card);
  overflow: hidden;
}

.rc-left {
  width: 136px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: var(--space-md) var(--space-sm);
  background: var(--color-primary-bg);
}

.rc-face {
  display: flex;
  align-items: baseline;
  margin: 0;
  font-family: var(--font-num);
  font-weight: 700;
  color: var(--color-primary);
}

.face-value {
  font-size: 26px;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}

.face-unit {
  margin: 0 2px;
  font-size: 13px;
  font-weight: 500;
}

.rc-scope {
  margin: 0;
  font-size: 12px;
  color: var(--text-regular);
}

/* 票券分割线 */
.rc-divider {
  position: relative;
  width: 0;
  flex-shrink: 0;
  border-left: 1px dashed var(--border-strong);
}

.rc-divider::before,
.rc-divider::after {
  content: '';
  position: absolute;
  left: -6px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--bg-page);
  border: 1px solid var(--border-light);
}

.rc-divider::before {
  top: -6px;
}

.rc-divider::after {
  bottom: -6px;
}

.rc-right {
  flex: 1;
  min-width: 0;
  padding: var(--space-md);
  padding-right: 120px;
}

.rc-name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.rc-threshold {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--text-regular);
}

.rc-time {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.rc-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 120px;
  flex-shrink: 0;
  padding-right: var(--space-md);
}

.rc-remain {
  font-size: 12px;
  color: var(--text-placeholder);
}

/* 秒杀券卡片网格 */
.seckill-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
}

@media (max-width: 768px) {
  .receive-card {
    flex-wrap: wrap;
  }

  .rc-left {
    width: 100px;
  }

  .rc-right {
    padding-right: var(--space-sm);
  }

  .rc-action {
    width: 100%;
    flex-direction: row;
    justify-content: space-between;
    padding: 0 var(--space-md) var(--space-md);
  }
}
</style>
