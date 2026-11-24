<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMySeckillOrders,
  cancelSeckillOrder,
  paySeckillOrder,
  applySeckillRefund,
} from '@/api/coupon.js'
import { formatPrice } from '@/utils/format.js'

const router = useRouter()

const activeTab = ref(1)
const loading = ref(true)
const operating = ref(false)
const orders = ref([])

// 状态 tab：待支付 / 已支付 / 已取消 / 已退款
const TABS = [
  { label: '待支付', value: 1 },
  { label: '已支付', value: 2 },
  { label: '已取消', value: 3 },
  { label: '已退款', value: 4 },
]

const STATUS_TEXT = {
  1: '待支付',
  2: '已支付',
  3: '已取消',
  4: '已退款',
}

// 待支付订单的截止时间（后端下单 +15 分钟）
const expireMap = ref({})

// now 每秒自增，用来驱动倒计时文案重算（否则要等下次列表请求才刷新）
const now = ref(Date.now())

function expireText(order) {
  const end = expireMap.value[order.orderNo]
  if (!end) return ''
  const left = new Date(String(end).replace(/-/g, '/')).getTime() - now.value
  if (left <= 0) return '订单已超时关闭'
  const totalSec = Math.floor(left / 1000)
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  const pad = (n) => String(n).padStart(2, '0')
  return `剩余 ${pad(m)}:${pad(s)}`
}

function isExpired(order) {
  const end = expireMap.value[order.orderNo]
  if (!end) return false
  return new Date(String(end).replace(/-/g, '/')).getTime() <= now.value
}

// 两个定时器：1 秒走倒计时，60 秒回后端拉一次列表（状态可能已被定时任务关单）
let tickTimer = null
let refreshTimer = null

onMounted(() => {
  load()
  tickTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  refreshTimer = setInterval(load, 60000)
})

onBeforeUnmount(() => {
  if (tickTimer) clearInterval(tickTimer)
  if (refreshTimer) clearInterval(refreshTimer)
  tickTimer = null
  refreshTimer = null
})

function load() {
  getMySeckillOrders({ page: 1, pageSize: 50, status: activeTab.value })
    .then((res) => {
      const list = res?.list || []
      orders.value = list
      // 记录待支付订单的截止时间，用于倒计时
      const next = {}
      list.forEach((o) => {
        if (o.status === 1 && o.expireTime) next[o.orderNo] = o.expireTime
      })
      expireMap.value = next
    })
    .catch((e) => {
      ElMessage.error(e?.message || '秒杀订单加载失败')
    })
    .finally(() => {
      loading.value = false
    })
}

async function handleCancel(order) {
  try {
    await ElMessageBox.confirm('取消后订单关闭，库存回补，确定取消吗？', '取消订单', {
      type: 'warning',
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }
  operating.value = true
  try {
    await cancelSeckillOrder(order.orderNo)
    ElMessage.success('已取消')
    load()
  } catch (e) {
    ElMessage.error(e?.message || '取消失败')
  } finally {
    operating.value = false
  }
}

async function handlePay(order) {
  if (operating.value) return
  operating.value = true
  try {
    await paySeckillOrder(order.orderNo, 'MOCK')
    ElMessage.success('支付成功，优惠券已发放到「我的优惠券」')
    load()
  } catch (e) {
    ElMessage.error(e?.message || '支付失败')
  } finally {
    operating.value = false
  }
}

async function handleRefund(order) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请填写退款原因（选填）', '申请退款', {
      confirmButtonText: '提交申请',
      cancelButtonText: '取消',
      inputPlaceholder: '如：误购 / 不需要了',
      inputValidator: (val) => (val && val.length > 255 ? '退款原因不能超过 255 字' : true),
    })
    reason = result.value || ''
  } catch {
    return
  }
  operating.value = true
  try {
    await applySeckillRefund(order.orderNo, { reason, images: '' })
    ElMessage.success('已提交，等待商家审核')
    load()
  } catch (e) {
    ElMessage.error(e?.message || '退款申请失败')
  } finally {
    operating.value = false
  }
}

function goDetail(order) {
  router.push(`/coupon/seckill/${order.couponId}`)
}

function goCoupons() {
  router.push('/user/coupons')
}
</script>

<template>
  <div v-loading="loading" class="card page">
    <h2 class="section-title">我的秒杀订单</h2>

    <!-- tab-change 与 v-model 同时会改 activeTab，这里只用 v-model 驱动，切页签靠 @tab-change 重新取数 -->
    <el-tabs v-model="activeTab" class="order-tabs" @tab-change="load">
      <el-tab-pane v-for="tab in TABS" :key="tab.value" :label="tab.label" :name="tab.value" />
    </el-tabs>

    <el-empty v-if="!loading && !orders.length" description="暂无秒杀订单">
      <el-button type="primary" @click="router.push('/coupons')">去领券</el-button>
    </el-empty>

    <div class="order-list">
      <article v-for="order in orders" :key="order.orderNo" class="order-item" :class="`is-${order.status}`">
        <div class="order-head">
          <span class="order-no">{{ order.orderNo }}</span>
          <span class="order-status">{{ order.statusDesc || STATUS_TEXT[order.status] }}</span>
        </div>

        <div class="order-body">
          <div class="order-icon">券</div>
          <div class="order-info">
            <p class="coupon-name text-ellipsis">{{ order.couponName }}</p>
            <p class="order-time">下单时间：{{ order.createTime || '—' }}</p>
            <p v-if="order.payTime" class="order-time">支付时间：{{ order.payTime }}</p>
          </div>
          <span class="order-price">¥{{ formatPrice(order.payAmount) }}</span>
        </div>

        <div class="order-foot">
          <span class="foot-tip" :class="{ expired: isExpired(order) }">
            <template v-if="order.status === 1">{{ expireText(order) }}</template>
            <template v-else-if="order.status === 2">优惠券已发放到「我的优惠券」</template>
            <template v-else-if="order.status === 3">订单已取消</template>
            <template v-else>退款已完成，优惠券已作废</template>
          </span>

          <div class="foot-btns">
            <template v-if="order.status === 1">
              <el-button size="small" :loading="operating" @click="handleCancel(order)">取消订单</el-button>
              <el-button v-if="!isExpired(order)" type="primary" size="small" :loading="operating" @click="handlePay(order)">
                去支付
              </el-button>
            </template>
            <template v-else-if="order.status === 2">
              <el-button size="small" @click="goCoupons">查看优惠券</el-button>
              <el-button type="primary" size="small" :loading="operating" @click="handleRefund(order)">
                申请退款
              </el-button>
            </template>
            <template v-else>
              <el-button size="small" @click="goDetail(order)">查看详情</el-button>
            </template>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: var(--space-lg);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
}

.page .section-title {
  margin: 0 0 var(--space-md);
}

.order-tabs {
  margin-bottom: var(--space-md);
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.order-item {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  background: var(--bg-card);
  overflow: hidden;
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px var(--space-md);
  border-bottom: 1px solid var(--border-light);
}

.order-no {
  font-size: 12px;
  color: var(--text-secondary);
}

.order-status {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}

/* 已取消 / 已退款：弱化 */
.order-item.is-3 .order-status,
.order-item.is-4 .order-status {
  color: var(--text-secondary);
}

.order-item.is-3,
.order-item.is-4 {
  opacity: 0.8;
}

.order-body {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
}

.order-icon {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-light));
  border-radius: var(--radius-sm);
}

.order-info {
  flex: 1;
  min-width: 0;
}

.coupon-name {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.order-time {
  margin: 0 0 2px;
  font-size: 12px;
  color: var(--text-secondary);
}

.order-price {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: 10px var(--space-md);
  border-top: 1px solid var(--border-light);
}

.foot-tip {
  font-size: 12px;
  color: var(--color-primary);
}

.foot-tip.expired {
  color: var(--text-secondary);
}

.foot-btns {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .page {
    padding: var(--space-md) var(--space-sm);
  }

  .order-foot {
    flex-direction: column;
    align-items: stretch;
  }

  .foot-btns {
    justify-content: flex-end;
  }
}
</style>
