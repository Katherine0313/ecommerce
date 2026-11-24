<template>
  <!-- 促销活动新建 / 编辑弹窗：由列表页用 v-model:visible + activity-id 打开 -->
  <a-modal v-model:visible="visibleProxy" :title="activityId ? '编辑活动' : '新建活动'" :width="860"
           :footer="null">
    <!-- 进行中的活动也能改：保存后新活动价立即生效，已下单的订单价格不变 -->
    <a-alert v-if="editingStatus === 'ACTIVE'" type="warning" show-icon
             message="进行中的活动：保存后新的活动价立即生效，已下单的订单价格不变。"
             style="margin-bottom: 16px" />
    <!-- 已结束 / 已失效：不可编辑，只保留查看与删除 -->
    <a-alert v-else-if="editingReadonly" type="info" show-icon
             message="该活动已结束或已失效，不能再修改；没有产生订单的话可以在下方删除。"
             style="margin-bottom: 16px" />

    <a-spin :spinning="loading">
      <a-form :model="form" :label-col="{ span: 4 }" :disabled="editingReadonly">
        <a-form-item label="活动名称" required>
          <a-input v-model:value="form.name" placeholder="方便运营识别的活动名称" :maxlength="60" />
        </a-form-item>
        <a-form-item label="促销类型" required>
          <a-radio-group v-model:value="form.type" @change="handleTypeChange">
            <a-radio value="DIRECT">直减（改商品价）</a-radio>
            <a-radio value="FULL_REDUCE">满减（改商品小计）</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="玩法" required>
          <a-select v-model:value="form.ruleType" :options="ruleTypeOptions" style="width: 220px"
                    @change="handleRuleTypeChange" />
        </a-form-item>
        <a-form-item label="目标人群">
          <a-select v-model:value="form.targetGroup" style="width: 220px" :options="targetOptions"
                    :disabled="form.ruleType === 'NEW_USER'" />
        </a-form-item>
        <a-form-item v-if="form.type === 'FULL_REDUCE'" label="商品范围">
          <a-radio-group v-model:value="form.scopeType">
            <a-radio value="ALL">全场</a-radio>
            <a-radio value="PRODUCT">指定商品</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="活动时间" required>
          <a-range-picker v-model:value="form.range" show-time style="width: 100%"
                          value-format="YYYY-MM-DD HH:mm:ss" :placeholder="['开始时间', '结束时间']" />
        </a-form-item>
        <a-form-item label="促销标签">
          <a-input v-model:value="form.tagName" placeholder="C 端展示，如「限时立减」" :maxlength="20" />
        </a-form-item>

        <!-- 直减类：选商品 + 逐个设置活动价 -->
        <a-form-item v-if="needProductSelect" label="活动商品" required>
          <a-select v-model:value="form.productIds" mode="multiple" show-search option-filter-prop="label"
                    :options="productOptions" placeholder="搜索并选择商品" style="width: 100%"
                    :max-tag-count="8" />
        </a-form-item>
        <a-form-item v-if="form.type === 'DIRECT' && form.productIds.length" label="活动价">
          <div class="price-list">
            <div v-for="id in form.productIds" :key="id" class="price-row">
              <span class="product-name">{{ productMap[id]?.name || id }}</span>
              <span class="origin-price">原价 ¥{{ money(productMap[id]?.salesPrice) }}</span>
              <span>活动价</span>
              <a-input-number v-model:value="form.prices[id]" :min="0.01" :precision="2" style="width: 120px" />
            </div>
          </div>
        </a-form-item>

        <!-- 满减类：阶梯配置 -->
        <a-form-item v-if="form.type === 'FULL_REDUCE'" label="满减阶梯" required>
          <div class="rule-list">
            <div v-for="(rule, index) in form.rules" :key="index" class="rule-row">
              <span>满</span>
              <a-input-number v-model:value="rule.thresholdAmount" :min="0.01" :precision="2"
                              style="width: 120px" />
              <span>元</span>
              <template v-if="form.ruleType === 'FULL_AMOUNT_REDUCE'">
                <span>减</span>
                <a-input-number v-model:value="rule.discountAmount" :min="0.01" :precision="2"
                                style="width: 120px" />
                <span>元</span>
              </template>
              <template v-else>
                <span>打</span>
                <a-input-number v-model:value="rule.discountRate" :min="0.01" :max="0.99" :step="0.05"
                                :precision="2" style="width: 120px" />
                <span>折（0.85 = 85 折）</span>
              </template>
              <a-button type="link" danger @click="removeRule(index)">删除</a-button>
            </div>
            <a-button type="dashed" @click="addRule">添加阶梯</a-button>
          </div>
        </a-form-item>
      </a-form>
    </a-spin>

    <!-- 底部：左侧 提前结束 / 删除，右侧 取消 / 保存 -->
    <div class="modal-footer">
      <div class="footer-left">
        <template v-if="activityId">
          <a-popconfirm v-if="editingStatus === 'ACTIVE'" title="确定提前结束该活动？"
                        @confirm="handleEnd">
            <a-button v-permission="'business:promotion:end'" style="margin-right: 10px;">提前结束</a-button>
          </a-popconfirm>
          <!-- 删除：没有产生过订单的活动可以删；已经产生订单的只能提前结束 -->
          <a-tooltip v-if="editingOrderCount > 0"
                     :title="`该活动已产生 ${editingOrderCount} 笔订单，只能提前结束，不能删除`">
            <span class="inline-btn"><a-button danger disabled>删除</a-button></span>
          </a-tooltip>
          <a-popconfirm v-else title="确定删除该活动？删除后不可恢复。"
                        @confirm="handleDelete">
            <a-button v-permission="'business:promotion:remove'" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </div>
      <div class="footer-right">
        <a-button @click="close">取消</a-button>
        <a-button v-if="!editingReadonly" type="primary" :loading="saving" @click="handleSubmit">保存</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  addPromotion,
  deletePromotion,
  endPromotion,
  getPromotionDetail,
  updatePromotion,
} from '@/api/promotion.js'
import { getGoodsList } from '@/api/products.js'

const props = defineProps({
  // 弹窗是否显示（列表页用 v-model:visible 控制）
  visible: { type: Boolean, default: false },
  // 活动 ID：为空表示新建
  activityId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:visible', 'success'])

const visibleProxy = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const form = ref(emptyForm())
const products = ref([])
const loading = ref(false)
const saving = ref(false)
// 编辑时的活动状态与订单数：状态决定能不能改/能不能结束，订单数决定能不能删
const editingStatus = ref(null)
const editingOrderCount = ref(0)

// 已结束 / 已失效的活动不可编辑，弹窗按只读展示（只能删除）
const editingReadonly = computed(
  () => !!props.activityId && editingStatus.value !== 'PENDING' && editingStatus.value !== 'ACTIVE'
)

const productMap = computed(() => {
  const map = {}
  products.value.forEach((p) => (map[p.id] = p))
  return map
})
const productOptions = computed(() =>
  products.value.map((p) => ({ value: p.id, label: `${p.name}（¥${p.salesPrice ?? '-'}）` }))
)
const ruleTypeOptions = computed(() =>
  form.value.type === 'DIRECT'
    ? [
        { value: 'DISCOUNT', label: '限时折扣' },
        { value: 'NEW_USER', label: '新用户专享' },
      ]
    : [
        { value: 'FULL_AMOUNT_REDUCE', label: '满额减' },
        { value: 'FULL_AMOUNT_DISCOUNT', label: '满额折' },
      ]
)
const needProductSelect = computed(
  () => form.value.type === 'DIRECT' || form.value.scopeType === 'PRODUCT'
)

const targetOptions = [
  { value: 'ALL', label: '全部用户' },
  { value: 'NEW_USER', label: '未下单新用户' },
]

function emptyForm() {
  return {
    name: '',
    type: 'DIRECT',
    ruleType: 'DISCOUNT',
    targetGroup: 'ALL',
    scopeType: 'PRODUCT',
    tagName: '',
    range: [],
    productIds: [],
    prices: {},
    rules: [{ thresholdAmount: null, discountAmount: null, discountRate: null }],
  }
}

// 打开弹窗时：新建走空表单，编辑走详情回填；商品列表只在第一次时拉
watch(
  () => [props.visible, props.activityId],
  ([visible, id]) => {
    if (!visible) {
      return
    }
    if (id) {
      loadDetail(id)
    } else {
      editingStatus.value = null
      editingOrderCount.value = 0
      form.value = emptyForm()
    }
    loadProducts()
  },
  { immediate: true }
)

function loadDetail(id) {
  loading.value = true
  getPromotionDetail(id)
    .then((res) => {
      const data = res.data.data
      editingStatus.value = data.status
      editingOrderCount.value = data.orderCount ?? 0
      const prices = {}
      ;(data.items || []).forEach((item) => {
        if (item.promotionPrice !== null && item.promotionPrice !== undefined) {
          prices[item.productId] = Number(item.promotionPrice)
        }
      })
      form.value = {
        name: data.name,
        type: data.type,
        ruleType: data.ruleType,
        targetGroup: data.targetGroup,
        scopeType: data.scopeType,
        tagName: data.tagName || '',
        range: [data.startTime, data.endTime],
        productIds: (data.items || []).map((item) => item.productId),
        prices,
        rules: (data.rules || []).length
          ? data.rules.map((rule) => ({
              thresholdAmount: Number(rule.thresholdAmount),
              discountAmount: rule.discountAmount === null ? null : Number(rule.discountAmount),
              discountRate: rule.discountRate === null ? null : Number(rule.discountRate),
            }))
          : [{ thresholdAmount: null, discountAmount: null, discountRate: null }],
      }
    })
    .finally(() => {
      loading.value = false
    })
}

function loadProducts() {
  if (products.value.length) {
    return
  }
  getGoodsList({ page: 1, pageSize: 500 }).then((res) => {
    products.value = res.data.data.list || []
  })
}

function handleTypeChange() {
  form.value.ruleType = form.value.type === 'DIRECT' ? 'DISCOUNT' : 'FULL_AMOUNT_REDUCE'
  form.value.scopeType = form.value.type === 'DIRECT' ? 'PRODUCT' : 'ALL'
  form.value.productIds = []
  form.value.prices = {}
  form.value.rules = [{ thresholdAmount: null, discountAmount: null, discountRate: null }]
}

function handleRuleTypeChange() {
  if (form.value.ruleType === 'NEW_USER') {
    form.value.targetGroup = 'NEW_USER'
  }
}

function addRule() {
  form.value.rules.push({ thresholdAmount: null, discountAmount: null, discountRate: null })
}

function removeRule(index) {
  if (form.value.rules.length <= 1) {
    message.warning('至少保留一档阶梯')
    return
  }
  form.value.rules.splice(index, 1)
}

function buildPayload() {
  const isDirect = form.value.type === 'DIRECT'
  const specialScope = isDirect || form.value.scopeType === 'PRODUCT'
  const items = specialScope
    ? form.value.productIds.map((id) => ({
        productId: id,
        promotionPrice: isDirect ? form.value.prices[id] : null,
      }))
    : []
  const rules = isDirect
    ? []
    : form.value.rules.map((rule) => ({
        thresholdAmount: rule.thresholdAmount,
        discountAmount: form.value.ruleType === 'FULL_AMOUNT_REDUCE' ? rule.discountAmount : null,
        discountRate: form.value.ruleType === 'FULL_AMOUNT_DISCOUNT' ? rule.discountRate : null,
      }))
  return {
    name: form.value.name,
    type: form.value.type,
    ruleType: form.value.ruleType,
    targetGroup: form.value.ruleType === 'NEW_USER' ? 'NEW_USER' : form.value.targetGroup,
    scopeType: isDirect ? 'PRODUCT' : form.value.scopeType,
    tagName: form.value.tagName,
    startTime: form.value.range?.[0],
    endTime: form.value.range?.[1],
    items,
    rules,
  }
}

function handleSubmit() {
  const payload = buildPayload()
  if (!payload.name) {
    message.warning('请输入活动名称')
    return
  }
  if (!payload.startTime || !payload.endTime) {
    message.warning('请选择活动时间')
    return
  }
  if (payload.type === 'DIRECT') {
    if (!payload.items.length) {
      message.warning('请选择参加活动的商品')
      return
    }
    if (payload.items.some((item) => !item.promotionPrice || item.promotionPrice <= 0)) {
      message.warning('请为每个商品填写活动价')
      return
    }
  }
  if (payload.type === 'FULL_REDUCE') {
    if (payload.scopeType === 'PRODUCT' && !payload.items.length) {
      message.warning('请选择参加活动的商品')
      return
    }
    if (payload.rules.some((rule) => !rule.thresholdAmount)) {
      message.warning('请填写完整的满减阶梯')
      return
    }
  }
  saving.value = true
  const request = props.activityId
    ? updatePromotion(props.activityId, payload)
    : addPromotion(payload)
  request
    .then(() => {
      message.success(props.activityId ? '活动已更新' : '活动已创建')
      close()
      emit('success')
    })
    .finally(() => {
      saving.value = false
    })
}

// 提前结束：只针对当前活动，结束后关掉弹窗并刷新列表
function handleEnd() {
  endPromotion(props.activityId).then(() => {
    message.success('活动已提前结束')
    close()
    emit('success')
  })
}

// 删除：没有产生过订单才能删（后端会再校验一次）
function handleDelete() {
  deletePromotion(props.activityId).then(() => {
    message.success('活动已删除')
    close()
    emit('success')
  })
}

function close() {
  emit('update:visible', false)
}

function money(value) {
  if (value === null || value === undefined || value === '') return '-'
  return Number(value).toFixed(2)
}
</script>

<style scoped>
.price-list,
.rule-list {
  width: 100%;
}

.price-row,
.rule-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.product-name {
  width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.origin-price {
  width: 120px;
  color: #999;
}

/* 弹窗底部：左侧 提前结束 / 删除，右侧 取消 / 保存 */
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-right {
  display: flex;
  gap: 8px;
}

/* 被 tooltip 包住的禁用按钮要占位，否则会塌掉 */
.inline-btn {
  display: inline-block;
}
</style>
