<template>
  <a-modal v-model:visible="returnRefundReviewVisible"
           title="售后审核" width="min(900px, 92vw)" class="review">
    <div class="item">
      <div class="item-title">售后信息</div>
      <a-descriptions bordered :column="isMobile ? 1 : 3" :label-style="isMobile ? {} : { width: '106px' }">
        <a-descriptions-item label="售后单号">{{ currentOrder?.afterSaleNo }}</a-descriptions-item>
        <a-descriptions-item label="申请时间">{{ currentOrder?.createdAt }}</a-descriptions-item>
        <a-descriptions-item label="售后类型">{{ afterSaleStatus.typeMap[currentOrder?.afterSaleType] }}
        </a-descriptions-item>
        <a-descriptions-item label="售后原因">{{ afterSaleStatus.reasonMap[currentOrder?.reason] }}</a-descriptions-item>
        <a-descriptions-item label="货物状态">{{ currentOrder?.goodsStatusDesc || '—' }}</a-descriptions-item>
        <a-descriptions-item label="退款金额">￥{{ refundTotal.toFixed(2) }}</a-descriptions-item>
        <a-descriptions-item label="订单号">{{ currentOrder?.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="支付时间">{{ currentOrder?.payAt }}</a-descriptions-item>
        <a-descriptions-item label="支付方式">{{ currentOrder?.payTypeDesc }}</a-descriptions-item>
        <a-descriptions-item label="收货人">{{ currentOrder?.consignee }}</a-descriptions-item>
        <a-descriptions-item label="手机号码">{{ maskPhone(currentOrder?.phone) }}</a-descriptions-item>
        <a-descriptions-item label="补充说明">{{ currentOrder?.remark || '—' }}</a-descriptions-item>
        <a-descriptions-item label="收货地址" :span="isMobile ? 1 : 3">
          {{ (currentOrder?.region || '') + ' ' + (currentOrder?.detailAddress || '') }}
        </a-descriptions-item>
      </a-descriptions>
    </div>
    <div class="item">
      <div class="item-title">商品信息</div>
      <a-table :dataSource="items" row-key="productId" :pagination="false" size="small"
               style="margin-bottom: 16px;" class="table-title">
        <a-table-column title="商品名称" dataIndex="productName"></a-table-column>
        <a-table-column title="商品图片">
          <template #default="{record}">
            <img v-lazy="record.image" width="80" />
          </template>
        </a-table-column>
        <a-table-column title="单价" width="100">
          <template #default="{ record }">￥{{ record.salesPrice }}</template>
        </a-table-column>
        <a-table-column title="增值保障" width="100">
          <template #default="{ record }">
            <span v-if="Number(record.insuranceFee) > 0">￥{{ record.insuranceFee }}</span>
            <span v-else>-</span>
          </template>
        </a-table-column>
        <a-table-column title="购买数量" dataIndex="quantity" width="100"></a-table-column>
        <a-table-column title="单品小计">
          <template #default="{ record }">￥{{ record.refundAmount }}</template>
        </a-table-column>
      </a-table>
    </div>
    <div class="item">
      <div class="item-title">售后进度</div>
      <div style="margin-top:15px">
        <a-steps :current="stepCurrent" size="small">
          <a-step title="用户申请售后" :description="currentOrder?.createdAt" />
          <a-step title="商家审核"
                  :status="currentOrder?.status === 'rejected' ? 'error' : undefined"
                  :description="stepDescription(1)"></a-step>
          <a-step title="买家退货" :description="stepDescription(2)"></a-step>
          <a-step title="商家退款" :description="stepDescription(3)"></a-step>
          <a-step title="退款成功" :description="stepDescription(4)"></a-step>
        </a-steps>
      </div>
    </div>
    <a-form :model="confirmForm" layout="inline" class="remark-form">
      <a-form-item label="商家内部备注" name="merchantRemark">
        <a-input v-model:value="confirmForm.merchantRemark" placeholder="如拒绝, 请填写原因"
                 allow-clear style="width: min(420px, 60vw)" />
      </a-form-item>
    </a-form>
    <a-alert v-if="!primaryAction && !rejectAction" type="info" show-icon
             message="当前状态没有可执行的操作" />
    <template #footer>
      <a-space>
        <a-button v-if="primaryAction" type="primary" :loading="submitting"
                  @click="submitReview(primaryAction.status)">{{ primaryAction.label }}</a-button>
        <a-button v-if="rejectAction" danger :loading="submitting"
                  @click="submitReview(rejectAction.status)">{{ rejectAction.label }}</a-button>
        <a-button @click="returnRefundReviewVisible = false">取消</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
<script setup>
import {ref, computed} from "vue";
import {getAfterSale, reviewAfterSale} from '@/api/afterSale.js'
import {message} from 'ant-design-vue'
import {useIsMobile} from '@/composables/useMediaQuery'
import {maskPhone} from '@/utils/mask.js'

// 手机端描述列表改单列，避免 160px 标签把内容挤没
const isMobile = useIsMobile()
const returnRefundReviewVisible = ref(false)
const currentOrder = ref()
// =================== 加载订单详情 ============
const items = ref([])
// 退款金额 = 售后明细单品小计之和（单品售后=该商品小计）
const refundTotal = computed(() =>
  (items.value || []).reduce((sum, i) => sum + (Number(i.refundAmount) || 0), 0)
)
const loadDetailOrders = (id) => {
  getAfterSale(id)
      .then(res => {
        const detail = res.data.data
        currentOrder.value = detail
        items.value = detail.items
      })
      .catch(err => {
        console.log('详情请求失败：', err.response?.status, err.response?.data)
      })

}
defineProps({
  afterSaleStatus: {type: Object, default: () => ({map: {}, reasonMap: {}, typeMap: {}})}
})

// 定义打开弹窗的方法，暴露给父组件ReturnRefund.vue
const returnRefundReviewOpen = (record) => {
  returnRefundReviewVisible.value = true
  currentOrder.value = record
  // 每次打开重置表单，避免上一次的备注残留
  confirmForm.value = {result: undefined, merchantRemark: ''}
  loadDetailOrders(record.id)
}
defineExpose({returnRefundReviewOpen})
// ================ 商家审核 =================
const confirmForm = ref({result: undefined, merchantRemark: ''})
const submitting = ref(false)

// 可执行动作由后端按状态机下发（availableActions），前端只按 kind 分主/危险按钮，
// 不再自己维护一份状态表（两份拷贝必然漂移）
const availableActions = computed(() => currentOrder.value?.availableActions || [])
const primaryAction = computed(() => availableActions.value.find(a => a.kind === 'PRIMARY'))
const rejectAction = computed(() => availableActions.value.find(a => a.kind === 'DANGER'))

// 根据售后状态，算进度条走到第几步
const stepCurrent = computed(() => {
  const status = currentOrder.value?.status
  if (status === 'reviewing') return 1                // 商家审核中 → 停在「商家审核」
  if (status === 'returning') return 2                // 买家退货中 → 走到「买家退货」
  if (['refunding', 'exchanging'].includes(status)) return 3  // 商家确认退款 → 走到「商家确认退款」
  if (['refunded', 'exchanged'].includes(status)) return 5  // 完成 → 全部走完
  return 1                                            // 其它（含拒绝）默认停「商家审核」
})

// 进度条每一步的描述文字
const stepDescription = (index) => {
  const o = currentOrder.value
  const status = o?.status
  const done = ['refunded', 'exchanged']
  const processing = ['refunding', 'exchanging']
  const isReturning = status === 'returning'
  if (index === 0) return o?.createdAt || ''
  if (index === 1) {           // 商家审核
    if (status === 'reviewing') return '审核中'
    if (status === 'rejected') return o?.rejectedAt || '不通过'
    if (isReturning || processing.includes(status) || done.includes(status)) return o?.reviewAt || '已完成'
    return '待处理'
  }
  if (index === 2) {           // 买家退货
    if (status === 'rejected') return '—'
    if (isReturning) return '退货中'
    if (processing.includes(status) || done.includes(status)) return o?.returnAt || '已完成'
    return '待处理'
  }
  if (index === 3) {           // 商家退款
    if (status === 'rejected') return '—'
    if (processing.includes(status)) return '退款中'
    if (done.includes(status)) return o?.refundAt || '已完成'
    return '待处理'
  }
  if (index === 4) {           // 退款成功
    if (status === 'rejected') return '—'
    if (done.includes(status)) return o?.refundedAt || '已完成'
    return '待处理'
  }
  return ''
}

const emit = defineEmits(['refresh'])
// 按目标状态提交审核（按钮直接给状态，不再经过下拉框转换）
const submitReview = (targetStatus) => {
  if (!targetStatus) {
    message.warning('请先选择审核结果')
    return
  }
  // 拒绝售后必须写明原因
  if (targetStatus === 'rejected' && !(confirmForm.value.merchantRemark || '').trim()) {
    message.warning('拒绝售后必须填写备注原因')
    return
  }
  submitting.value = true
  reviewAfterSale(currentOrder.value.id, {
    status: targetStatus,
    merchantRemark: confirmForm.value.merchantRemark,
  })
      .then(() => {
        message.success('审核提交成功')
        confirmForm.value = {result: undefined, merchantRemark: ''}
        returnRefundReviewVisible.value = false
        loadDetailOrders(currentOrder.value.id)
        emit('refresh')
      })
      .catch(err => {
        message.error(err.response?.data?.message || '审核失败')
      })
      .finally(() => {
        submitting.value = false
      })
}

</script>

<style scoped>
.review .item {
  margin-bottom: 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
}

.item .item-title {
  margin-bottom: 8px;
  padding-left: 8px;
  font-size: 14px;
  font-weight: 600;
  border-left: 3px solid #1677ff;
  line-height: 1.4;
}

.remark-form {
  margin-top: 4px;
}

.remark-form :deep(.ant-form-item) {
  margin-bottom: 8px;
}


.item .table-title:deep(.ant-table-tbody >tr >td) {
  font-size: 14px;
  text-align: center;
}

.item .table-item {
  border-radius: 8px;
}

/* 售后进度条：窄弹窗下缩小字体、允许换行，避免标题/时间戳被挡 */
.item :deep(.ant-steps-item-title) {
  font-size: 13px;
  white-space: normal;
  line-height: 1.4;
}

.item :deep(.ant-steps-item-description) {
  font-size: 12px;
  white-space: normal;
  word-break: break-all;
  line-height: 1.4;
}
</style>
