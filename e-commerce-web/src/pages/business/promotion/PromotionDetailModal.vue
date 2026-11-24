<template>
  <!-- 促销活动详情弹窗：由列表页用 v-model:visible + activity-id 打开 -->
  <a-modal v-model:visible="visibleProxy" title="活动详情" :width="900" :footer="null">
    <a-spin :spinning="loading">
      <template v-if="detail">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="活动名称">{{ detail.name }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(detail.status)">{{ detail.statusDesc }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="类型">{{ detail.typeDesc }} / {{ detail.ruleTypeDesc }}</a-descriptions-item>
          <a-descriptions-item label="人群范围">{{ detail.targetGroupDesc }} · {{ detail.scopeTypeDesc }}</a-descriptions-item>
          <a-descriptions-item label="活动时间" :span="2">{{ detail.startTime }} ~ {{ detail.endTime }}</a-descriptions-item>
          <a-descriptions-item label="使用订单数">{{ detail.stats?.orderCount ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="支付单数">{{ detail.stats?.paidOrderCount ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="累计优惠" :span="2">¥{{ money(detail.stats?.discountAmount) }}</a-descriptions-item>
        </a-descriptions>

        <!-- 活动商品：图片 + 价格，活动价用醒目色标出 -->
        <div v-if="detail.items?.length" class="detail-block">
          <div class="detail-title">活动商品（{{ detail.items.length }}）</div>
          <a-table :dataSource="detail.items" :columns="itemColumns" size="small" row-key="productId"
                   :pagination="false">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'image'">
                <img v-if="record.image" :src="record.image" class="goods-image" alt="商品主图" loading="lazy"/>
                <span v-else class="goods-image goods-image-empty">无图</span>
              </template>
              <template v-else-if="column.key === 'promotionPrice'">
                <span :class="record.promotionPrice != null ? 'promo-price' : ''">
                  {{ record.promotionPrice != null ? '¥' + money(record.promotionPrice) : '-' }}
                </span>
              </template>
              <template v-else-if="column.key === 'salesPrice'">
                ¥{{ money(record.salesPrice) }}
              </template>
            </template>
          </a-table>
        </div>

        <!-- 满减阶梯：只有满减类活动才有 -->
        <div v-if="detail.rules?.length" class="detail-block">
          <div class="detail-title">满减阶梯</div>
          <a-table :dataSource="detail.rules" :columns="ruleColumns" size="small" row-key="thresholdAmount"
                   :pagination="false">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'discountRate'">
                {{ record.discountRate != null ? (Number(record.discountRate) * 10).toFixed(1) + ' 折' : '-' }}
              </template>
              <template v-else-if="column.key === 'discountAmount'">
                {{ record.discountAmount != null ? '¥' + money(record.discountAmount) : '-' }}
              </template>
            </template>
          </a-table>
        </div>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { getPromotionDetail } from '@/api/promotion.js'

const props = defineProps({
  // 弹窗是否显示（列表页用 v-model:visible 控制）
  visible: { type: Boolean, default: false },
  // 要查看的活动 ID
  activityId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:visible'])

const visibleProxy = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const loading = ref(false)
const detail = ref(null)

const itemColumns = [
  { title: '图片', key: 'image', width: 80 },
  { title: '商品名称', dataIndex: 'productName', key: 'productName' },
  { title: '商品 ID', dataIndex: 'productId', key: 'productId', width: 90 },
  { title: '原价', dataIndex: 'salesPrice', key: 'salesPrice', width: 100 },
  { title: '活动价', dataIndex: 'promotionPrice', key: 'promotionPrice', width: 110 },
]
const ruleColumns = [
  { title: '满额门槛', dataIndex: 'thresholdAmount', key: 'thresholdAmount' },
  { title: '满减金额', dataIndex: 'discountAmount', key: 'discountAmount' },
  { title: '折扣率', dataIndex: 'discountRate', key: 'discountRate' },
]

// 打开弹窗时按 ID 拉详情
watch(
  () => [props.visible, props.activityId],
  ([visible, id]) => {
    if (visible && id) {
      loadDetail(id)
    }
  },
  { immediate: true }
)

function loadDetail(id) {
  loading.value = true
  detail.value = null
  getPromotionDetail(id)
    .then((res) => {
      detail.value = res.data.data
    })
    .finally(() => {
      loading.value = false
    })
}

function statusColor(status) {
  return { PENDING: 'blue', ACTIVE: 'green', ENDED: 'default', CANCELLED: 'red' }[status] || 'default'
}

function money(value) {
  if (value === null || value === undefined || value === '') return '-'
  return Number(value).toFixed(2)
}
</script>

<style scoped>
.detail-block {
  margin-top: 16px;
}

.detail-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.goods-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
  display: block;
}

.goods-image-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 12px;
}

.promo-price {
  color: #ff4d4f;
  font-weight: 600;
}
</style>
