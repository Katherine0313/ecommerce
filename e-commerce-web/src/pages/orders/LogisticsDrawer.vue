<template>
  <!-- 物流轨迹抽屉：轨迹是假数据（按订单 id 生成的演示时间线），快递公司与单号取真实发货时写入的值 -->
  <a-drawer v-model:visible="visible" title="物流轨迹" :width="480" @close="reset">
    <a-spin :spinning="loading">
      <div v-if="logistics" class="head">
        <div class="row">
          <span class="label">快递公司</span>
          <span class="value">{{ logistics.courier || '—' }}</span>
        </div>
        <div class="row">
          <span class="label">快递单号</span>
          <span class="value">{{ logistics.trackingNumber || '—' }}</span>
        </div>
      </div>

      <a-timeline v-if="traces.length">
        <a-timeline-item v-for="(t, i) in traces" :key="i" :color="traceColor(t)">
          <div class="trace-title">{{ t.status }}</div>
          <div class="trace-desc">{{ t.desc }}</div>
          <div class="trace-time">{{ t.time }}</div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-else-if="!loading" description="暂无物流轨迹（订单尚未发货）" />
    </a-spin>
  </a-drawer>
</template>
<script setup>
import {ref, computed} from 'vue'
import {getLogistics} from '@/api/orders.js'

const visible = ref(false)
const loading = ref(false)
const logistics = ref(null)

const traces = computed(() => (logistics.value && logistics.value.traces) || [])

// 节点配色：签收/派送用醒目色，待揽件用灰色，其余走默认蓝
function traceColor(t) {
  if (t.status === '已签收') return 'green'
  if (t.status === '派送中') return 'orange'
  if (t.status === '待揽件') return 'gray'
  return 'blue'
}

// 打开抽屉并拉轨迹（父组件用 ref 调用）
const open = (orderId) => {
  if (!orderId) return
  visible.value = true
  loading.value = true
  logistics.value = null
  getLogistics(orderId)
      .then(res => {
        logistics.value = res.data?.data || null
      })
      .catch(err => {
        console.log(err)
      })
      .finally(() => {
        loading.value = false
      })
}

// 关闭时清空，避免下次打开闪旧数据（traces 由 logistics 派生，清掉 logistics 即可）
const reset = () => {
  logistics.value = null
}

defineExpose({open})
</script>

<style scoped>
.head {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.row {
  display: flex;
  gap: 8px;
  font-size: 14px;
  line-height: 24px;
}

.label {
  color: #999;
  width: 70px;
  flex-shrink: 0;
}

.value {
  color: #333;
  word-break: break-all;
}

.trace-title {
  font-weight: 600;
  color: #333;
}

.trace-desc {
  font-size: 13px;
  color: #666;
}

.trace-time {
  font-size: 12px;
  color: #999;
}
</style>
