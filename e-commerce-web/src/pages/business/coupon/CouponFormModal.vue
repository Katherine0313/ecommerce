<template>
  <a-modal :visible="visible" :title="coupon ? '编辑优惠券' : '新建优惠券'" :width="820"
           :confirm-loading="saving" @cancel="closeModal" @ok="handleSubmit">
    <a-form :model="form" :label-col="{ span: 4 }">
      <a-form-item label="券名称" required>
        <a-input v-model:value="form.name" :maxlength="60" placeholder="如「新人立减 10 元」" />
      </a-form-item>
      <a-form-item label="发放方式" required>
        <a-radio-group v-model:value="form.issueType" :disabled="!!coupon">
          <a-radio value="RECEIVE">普通券（免费领取）</a-radio>
          <a-radio value="SECKILL">秒杀券（付费抢购）</a-radio>
        </a-radio-group>
        <span class="unit">创建后不可更改</span>
      </a-form-item>
      <a-form-item label="券类型" required>
        <a-radio-group v-model:value="form.type" :disabled="form.issueType === 'SECKILL'">
          <a-radio v-if="form.issueType === 'SECKILL'" value="FIXED">代金券</a-radio>
          <template v-else>
            <a-radio value="FIXED">满减券</a-radio>
            <a-radio value="DISCOUNT">折扣券</a-radio>
          </template>
        </a-radio-group>
      </a-form-item>
      <a-form-item v-if="form.type === 'FIXED'" :label="form.issueType === 'SECKILL' ? '券面额' : '满减金额'" required>
        <a-input-number v-model:value="form.faceValue" :min="0.01" :precision="2" style="width: 160px" />
        <span class="unit">元</span>
      </a-form-item>
      <template v-else>
        <a-form-item label="折扣率" required>
          <a-input-number v-model:value="form.discountRate" :min="0.01" :max="0.99" :step="0.05"
                          :precision="2" style="width: 160px" />
          <span class="unit">（0.85 = 85 折）</span>
        </a-form-item>
        <a-form-item label="最高优惠">
          <a-input-number v-model:value="form.maxDiscount" :min="0.01" :precision="2" style="width: 160px" />
          <span class="unit">元，留空不封顶</span>
        </a-form-item>
      </template>
      <a-form-item v-if="form.issueType !== 'SECKILL'" label="使用门槛">
        <a-input-number v-model:value="form.thresholdAmount" :min="0" :precision="2" style="width: 160px" />
        <span class="unit">元，0 = 无门槛</span>
      </a-form-item>
      <a-form-item label="适用商品" required>
        <a-radio-group v-model:value="form.scopeType">
          <a-radio value="ALL">全场</a-radio>
          <a-radio value="CATEGORY">指定分类</a-radio>
          <a-radio value="PRODUCT">指定商品</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item v-if="form.scopeType === 'CATEGORY'" label="选择分类" required>
        <a-select v-model:value="form.categoryIds" mode="multiple" show-search option-filter-prop="label"
                  :options="categoryOptions" placeholder="请选择分类" style="width: 100%" :max-tag-count="8" />
      </a-form-item>
      <a-form-item v-if="form.scopeType === 'PRODUCT'" label="选择商品" required>
        <a-select v-model:value="form.productIds" mode="multiple" show-search option-filter-prop="label"
                  :options="productOptions" placeholder="搜索并选择商品" style="width: 100%" :max-tag-count="8" />
      </a-form-item>
      <template v-if="form.issueType === 'SECKILL'">
        <a-form-item label="秒杀价" required>
          <a-input-number v-model:value="form.seckillPrice" :min="0.01" :precision="2" style="width: 160px" />
          <span class="unit">元（用户实际支付金额）</span>
        </a-form-item>
        <a-form-item label="活动时间" required>
          <a-range-picker v-model:value="form.seckillRange" show-time style="width: 100%"
                          value-format="YYYY-MM-DD HH:mm:ss" />
        </a-form-item>
      </template>
      <a-form-item v-if="form.issueType === 'RECEIVE'" label="目标人群">
        <a-select v-model:value="form.targetGroup" style="width: 220px" :options="targetOptions" />
      </a-form-item>
      <a-form-item label="会员门槛">
        <a-select v-model:value="form.minMemberLevel" style="width: 220px" :options="memberLevelOptions" />
        <span class="unit">达到该等级才可领取/使用</span>
      </a-form-item>
      <a-form-item label="发行总量" required>
        <a-input-number v-model:value="form.totalQuantity" :min="1" :precision="0" style="width: 160px"
                        :disabled="form.issueType === 'SECKILL' && !!coupon" />
        <span class="unit">张（每人限领 1 张）{{ form.issueType === 'SECKILL' && coupon ? '，活动创建后库存不可改' : '' }}</span>
      </a-form-item>
      <a-form-item v-if="form.issueType === 'RECEIVE'" label="领取时间" required>
        <a-range-picker v-model:value="form.receiveRange" show-time style="width: 100%"
                        value-format="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <a-form-item v-if="form.issueType === 'RECEIVE'" label="有效期" required>
        <a-radio-group v-model:value="form.validType">
          <a-radio value="FIXED">固定时段</a-radio>
          <a-radio value="RECEIVE_DAYS">领取后 N 天</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item v-if="form.issueType === 'SECKILL' || form.validType === 'FIXED'" label="有效时段" required>
        <a-range-picker v-model:value="form.validRange" show-time style="width: 100%"
                        value-format="YYYY-MM-DD HH:mm:ss" />
        <span v-if="form.issueType === 'SECKILL'" class="unit">秒杀券固定时段，且结束须晚于活动结束</span>
      </a-form-item>
      <a-form-item v-if="form.issueType === 'RECEIVE' && form.validType === 'RECEIVE_DAYS'" label="有效天数" required>
        <a-input-number v-model:value="form.validDays" :min="1" :max="365" :precision="0" style="width: 160px" />
        <span class="unit">天</span>
      </a-form-item>
      <a-form-item label="使用说明">
        <a-textarea v-model:value="form.description" :maxlength="200" :rows="2"
                    placeholder="如「限本店商品使用，不与其他券叠加」" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { addCoupon, updateCoupon } from '@/api/coupon.js'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  coupon: {
    type: Object,
    default: null,
  },
  products: {
    type: Array,
    default: () => [],
  },
  categories: {
    type: Array,
    default: () => [],
  },
  memberLevels: {
    type: Array,
    default: () => [],
  },
})
const emit = defineEmits(['update:visible', 'saved'])

const saving = ref(false)
const form = ref(emptyForm())

const targetOptions = [
  { value: 'ALL', label: '全部用户' },
  { value: 'NEW_USER', label: '未下单新用户' },
]

const productOptions = computed(() =>
  props.products.map((p) => ({ value: p.id, label: `${p.name}（¥${p.salesPrice ?? '-'}）` }))
)
const memberLevelOptions = computed(() => [
  { value: 0, label: '不限等级' },
  ...props.memberLevels.map((level) => ({ value: level.id, label: level.name })),
])
const categoryOptions = computed(() => {
  const options = []
  const walk = (nodes, prefix) => {
    nodes.forEach((c) => {
      options.push({ value: c.id, label: prefix + c.name })
      if (c.children?.length) walk(c.children, prefix + '　')
    })
  }
  walk(props.categories, '')
  return options
})

// 切换为秒杀券时：券类型锁定为代金券(FIXED)、无门槛、有效期强制固定时段(FIXED)
watch(
  () => form.value.issueType,
  (val) => {
    if (val === 'SECKILL') {
      form.value.type = 'FIXED'
      form.value.thresholdAmount = 0
      form.value.validType = 'FIXED'
    }
  }
)

watch(() => props.visible, (visible) => {
  if (!visible) return
  form.value = props.coupon ? couponToForm(props.coupon) : emptyForm()
})

function emptyForm() {
  return {
    name: '',
    issueType: 'RECEIVE',
    type: 'FIXED',
    faceValue: null,
    discountRate: null,
    maxDiscount: null,
    thresholdAmount: 0,
    scopeType: 'ALL',
    categoryIds: [],
    productIds: [],
    seckillPrice: null,
    seckillRange: [],
    targetGroup: 'ALL',
    minMemberLevel: 0,
    totalQuantity: 100,
    receiveRange: [],
    validType: 'FIXED',
    validRange: [],
    validDays: 30,
    description: '',
  }
}

function couponToForm(data) {
  return {
    name: data.name,
    issueType: data.issueType || 'RECEIVE',
    type: data.type,
    faceValue: data.faceValue === null ? null : Number(data.faceValue),
    discountRate: data.discountRate === null ? null : Number(data.discountRate),
    maxDiscount: data.maxDiscount === null ? null : Number(data.maxDiscount),
    thresholdAmount: Number(data.thresholdAmount || 0),
    scopeType: data.scopeType,
    categoryIds: data.categoryIds || [],
    productIds: data.productIds || [],
    seckillPrice:
      data.seckillPrice === null || data.seckillPrice === undefined
        ? null
        : Number(data.seckillPrice),
    seckillRange: data.seckillBeginTime ? [data.seckillBeginTime, data.seckillEndTime] : [],
    targetGroup: data.targetGroup,
    minMemberLevel: data.minMemberLevel || 0,
    totalQuantity: data.totalQuantity,
    receiveRange: data.receiveStart ? [data.receiveStart, data.receiveEnd] : [],
    validType: data.validType,
    validRange: data.validStart ? [data.validStart, data.validEnd] : [],
    validDays: data.validDays || 30,
    description: data.description || '',
  }
}

function buildPayload() {
  const isSeckill = form.value.issueType === 'SECKILL'
  const payload = {
    name: form.value.name,
    issueType: form.value.issueType,
    type: form.value.type,
    faceValue: form.value.type === 'FIXED' ? form.value.faceValue : null,
    discountRate: form.value.type === 'DISCOUNT' ? form.value.discountRate : null,
    maxDiscount: form.value.type === 'DISCOUNT' ? form.value.maxDiscount : null,
    thresholdAmount: form.value.thresholdAmount || 0,
    scopeType: form.value.scopeType,
    targetGroup: form.value.targetGroup,
    minMemberLevel: form.value.minMemberLevel || 0,
    totalQuantity: form.value.totalQuantity,
    perUserLimit: 1,
    description: form.value.description,
    categoryIds: form.value.scopeType === 'CATEGORY' ? form.value.categoryIds : [],
    productIds: form.value.scopeType === 'PRODUCT' ? form.value.productIds : [],
  }
  if (isSeckill) {
    // 秒杀券：有效期强制 FIXED，不使用领取时间 / 领取后 N 天
    payload.seckillPrice = form.value.seckillPrice
    payload.seckillBeginTime = form.value.seckillRange?.[0]
    payload.seckillEndTime = form.value.seckillRange?.[1]
    payload.validType = 'FIXED'
    payload.validStart = form.value.validRange?.[0]
    payload.validEnd = form.value.validRange?.[1]
    payload.receiveStart = null
    payload.receiveEnd = null
    payload.validDays = null
  } else {
    // 普通券：忽略秒杀字段
    payload.seckillPrice = null
    payload.seckillBeginTime = null
    payload.seckillEndTime = null
    payload.receiveStart = form.value.receiveRange?.[0]
    payload.receiveEnd = form.value.receiveRange?.[1]
    payload.validType = form.value.validType
    payload.validStart = form.value.validType === 'FIXED' ? form.value.validRange?.[0] : null
    payload.validEnd = form.value.validType === 'FIXED' ? form.value.validRange?.[1] : null
    payload.validDays = form.value.validType === 'RECEIVE_DAYS' ? form.value.validDays : null
  }
  return payload
}

function handleSubmit() {
  const payload = buildPayload()
  if (!payload.name) {
    message.warning('请输入券名称')
    return
  }
  if (payload.type === 'FIXED' && !payload.faceValue) {
    message.warning('请填写满减金额')
    return
  }
  if (payload.type === 'DISCOUNT' && !payload.discountRate) {
    message.warning('请填写折扣率')
    return
  }
  if (payload.issueType === 'SECKILL') {
    if (!payload.seckillPrice || payload.seckillPrice <= 0) {
      message.warning('请填写秒杀价（须大于 0）')
      return
    }
    if (!payload.seckillBeginTime || !payload.seckillEndTime) {
      message.warning('请选择秒杀活动时间')
      return
    }
    if (payload.seckillBeginTime >= payload.seckillEndTime) {
      message.warning('秒杀活动开始时间须早于结束时间')
      return
    }
    if (!payload.validStart || !payload.validEnd) {
      message.warning('请选择有效时段')
      return
    }
    if (payload.validEnd <= payload.seckillEndTime) {
      message.warning('有效时段结束须晚于秒杀活动结束时间')
      return
    }
  } else {
    if (!payload.receiveStart || !payload.receiveEnd) {
      message.warning('请选择领取时间')
      return
    }
    if (payload.validType === 'FIXED' && (!payload.validStart || !payload.validEnd)) {
      message.warning('请选择有效期')
      return
    }
  }
  if (payload.scopeType === 'CATEGORY' && !payload.categoryIds.length) {
    message.warning('请选择适用分类')
    return
  }
  if (payload.scopeType === 'PRODUCT' && !payload.productIds.length) {
    message.warning('请选择适用商品')
    return
  }
  saving.value = true
  const request = props.coupon
    ? updateCoupon(props.coupon.id, payload)
    : addCoupon(payload)
  request
    .then(() => {
      message.success(props.coupon ? '优惠券已更新' : '优惠券已创建')
      emit('update:visible', false)
      emit('saved')
    })
    .finally(() => {
      saving.value = false
    })
}

function closeModal() {
  emit('update:visible', false)
}
</script>

<style scoped>
.unit {
  margin-left: 8px;
  color: #999;
}
</style>
