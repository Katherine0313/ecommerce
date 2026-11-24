<template>
  <a-modal v-model:visible="exchangeDetailVisible" title="售后详情"
           width="min(900px, 92vw)" class="detail">
    <div class="item">
      <div class="item-title">售后信息</div>
      <a-descriptions bordered :column="isMobile ? 1 : 2" :label-style="isMobile ? {} : { width: '160px' }">
        <a-descriptions-item label="售后单号">{{ currentOrder?.afterSaleNo }}</a-descriptions-item>
        <a-descriptions-item label="申请时间">{{ currentOrder?.createdAt }}</a-descriptions-item>
        <a-descriptions-item label="售后类型"> {{ afterSaleStatus.typeMap[currentOrder?.afterSaleType] }}
        </a-descriptions-item>
        <a-descriptions-item label="售后原因">{{
            afterSaleStatus.reasonMap[currentOrder?.reason]
          }}
        </a-descriptions-item>
        <a-descriptions-item label="货物状态" :span="2">{{ currentOrder?.goodsStatusDesc || '—' }}</a-descriptions-item>
        <a-descriptions-item label="补充说明" :span="2">{{ currentOrder?.remark }}</a-descriptions-item>
      </a-descriptions>
    </div>
    <div class="item">
      <div class="item-title">用户信息</div>
      <a-descriptions bordered :column="isMobile ? 1 : 2" :label-style="isMobile ? {} : { width: '160px' }">
        <a-descriptions-item label="收货人">{{ currentOrder?.consignee }}</a-descriptions-item>
        <a-descriptions-item label="手机号码">{{ maskPhone(currentOrder?.phone) }}</a-descriptions-item>
        <a-descriptions-item label="收货地址" :span="2">
          {{ (currentOrder?.region || '') + ' ' + (currentOrder?.detailAddress || '') }}
        </a-descriptions-item>
      </a-descriptions>
    </div>
    <div class="item">
      <div class="item-title">订单信息</div>
      <a-descriptions bordered :column="isMobile ? 1 : 2" :label-style="isMobile ? {} : { width: '160px' }">
        <a-descriptions-item label="订单号">{{ currentOrder?.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="支付时间">{{ currentOrder?.payAt }}</a-descriptions-item>
        <a-descriptions-item label="支付方式">{{ currentOrder?.payTypeDesc }}</a-descriptions-item>
        <a-descriptions-item label="换货商品金额">￥{{ refundTotal.toFixed(2) }}</a-descriptions-item>
        <a-descriptions-item v-if="currentOrder?.pickupAt" label="上门取件">
          {{ currentOrder.pickupAt }}<span v-if="currentOrder.pickupNo"> · {{ currentOrder.pickupNo }}</span>
        </a-descriptions-item>
        <a-descriptions-item v-if="currentOrder?.shipTrackingNumber" label="补发物流" :span="2">
          {{ currentOrder.shipCourier || '—' }} {{ currentOrder.shipTrackingNumber }}
          <span v-if="currentOrder.shipAt"> · {{ currentOrder.shipAt }}</span>
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
          <a-step title="申请售后" :description="currentOrder?.createdAt" />
          <a-step title="商家审核"
                  :status="currentOrder?.status === 'rejected' ? 'error' : undefined"
                  :description="stepDescription(1)"></a-step>
          <a-step title="买家退货" :description="stepDescription(2)"></a-step>
          <a-step title="商家换货" :description="stepDescription(3)"></a-step>
          <a-step title="换货完成" :description="stepDescription(4)"></a-step>
        </a-steps>
      </div>
    </div>
    <AfterSaleLogTimeline :logs="logs" />
  </a-modal>
</template>
<script setup>
import {ref, computed} from "vue";
import {getAfterSale, getAfterSaleLogs} from '@/api/afterSale.js'
import AfterSaleLogTimeline from '@/components/AfterSaleLogTimeline.vue'
import {useIsMobile} from '@/composables/useMediaQuery'
import {maskPhone} from '@/utils/mask.js'

// 手机端描述列表改单列，避免 160px 标签把内容挤没
const isMobile = useIsMobile()
const exchangeDetailVisible = ref(false)
const currentOrder = ref()

// 根据售后状态，算进度条走到第几步（换货流程：申请→审核→买家退货→商家换货→完成）
const stepCurrent = computed(() => {
  const status = currentOrder.value?.status
  if (status === 'reviewing') return 1                                // 商家审核中 → 停在「商家审核」
  if (status === 'returning') return 2                                // 买家退货中 → 走到「买家退货」
  if (status === 'exchanging') return 3                              // 商家换货中 → 走到「商家换货」
  if (['exchanged', 'refunded'].includes(status)) return 5           // 完成 → 全部走完
  return 1
})

// 进度条每一步的描述文字
const stepDescription = (index) => {
  const o = currentOrder.value
  const status = o?.status
  const done = ['exchanged', 'refunded']
  if (index === 0) return o?.createdAt || ''
  if (index === 1) {           // 商家审核
    if (status === 'reviewing') return '审核中'
    if (status === 'rejected') return o?.rejectedAt || '不通过'
    return o?.reviewAt || '已完成'
  }
  if (index === 2) {           // 买家退货
    if (status === 'rejected') return '—'
    // 买家已提交退货方式（含换货仍停在 returning 的情况）→ 显示提交时间
    if (o?.returnAt) return o.returnAt
    if (status === 'returning') return '退货中'
    return '已完成'
  }
  if (index === 3) {           // 商家换货
    if (status === 'rejected') return '—'
    // 商家已确认收到退货，开始换货 → 显示确认时间
    if (o?.exchangeAt) return o.exchangeAt
    if (status === 'exchanging') return '处理中'
    if (done.includes(status)) return '已发货'
    return '待处理'
  }
  if (index === 4) {           // 换货完成
    if (status === 'rejected') return '—'
    if (done.includes(status)) return o?.exchangedAt || '已完成'
    return '待处理'
  }
  return ''
}
// 变更记录（after_sale_log）
const logs = ref([])
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
  // 变更记录单独拉，失败不影响详情展示
  getAfterSaleLogs(id)
      .then(res => {
        logs.value = res.data.data || []
      })
      .catch(err => {
        console.log('变更记录加载失败：', err.response?.status)
      })
}
defineProps({
  afterSaleStatus: {type: Object, default: () => ({map: {}, reasonMap: {}, typeMap: {}})}
})

// 定义打开弹窗的方法，并暴露给父组件Exchange.vue
const exchangeDetailOpen = (record) => {
  exchangeDetailVisible.value = true
  currentOrder.value = record
  loadDetailOrders(record.id)
}
defineExpose({exchangeDetailOpen})
</script>

<style scoped>
.detail .item {
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.item .item-title {
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.1);
  margin-bottom: 5px;
  padding: 8px 0 8px 10px;
  font-size: 16px;
  border-radius: 8px;
}


.item .table-title:deep(.ant-table-tbody >tr >td) {
  font-size: 14px;
  text-align: center;
}

.item .table-item {
  border-radius: 8px;
}

/*底部*/
.return {
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  border: 1px solid #ccc;
}

.return .head {
  height: 40px;
  border-bottom: 1px solid #ccc;
  background: #FF6700;
  border-radius: 8px;
  font-size: 20px;
  color: #fff;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;

}

.return .content {
  display: flex;
  padding: 10px 0;
}

.return .content .l {
  width: 50%;
  border-right: 1px solid #ccc;
  display: flex;
  flex-direction: column;
  padding: 0 10px;
}

.return .content .r {
  flex: 1;
  display: flex;
  padding: 0 10px;
  justify-content: space-between;
}
/*退货时间*/
.return .content .time .list-l,
.return .content .time .list-r {
  width: 120px;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  border: 1px solid #ccc;
  cursor: pointer;
  text-align: center;
  line-height: 24px;
  padding: 5px 0;
  transition: border-color .2s, background-color .2s, color .2s;
}

.return .content .time .list-l.active,
.return .content .time .list-r.active {
  border-color: #FF6700;
  background: #fff7f0;
  color: #FF6700;
}

.return .content .time {
  display: flex;
  justify-content: space-around;
}

/* 手机：退货方式左右两栏改上下，时间段各占一半 */
@media (max-width: 767px) {
  .return .head {
    font-size: 16px;
  }
  .return .content {
    flex-direction: column;
  }
  .return .content .l {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #ccc;
  }
  .return .content .r {
    width: 100%;
  }
  .return .content .time .list-l,
  .return .content .time .list-r {
    width: calc(50% - 6px);
  }
}

/*圆圈*/
.content .dot {
  width: 25px;
  height: 25px;
  border-radius: 50%;
  border: 1px solid #ccc;
  cursor: pointer;
  transition: background-color .2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content .dot.active {
  background-color: #1890ff;
  border-color: #1890ff;
}

.content .dot.active::after {
  content: '';
  display: block;
  width: 7px;
  height: 12px;
  border: 2px solid #fff;
  border-top: none;
  border-left: none;
  transform: rotate(45deg);
  margin-top: -2px;
}

#l-top {
  display: flex;
  justify-content: space-between;
}
.list{
  margin:10px 0;
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