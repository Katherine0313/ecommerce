<template>
  <!-- 打印专用版式：屏幕上隐藏，@media print 时接管整页 -->
  <div id="stock-order-print" v-if="order">
    <h1 class="p-title">{{ titleText }}</h1>
    <div class="p-info">
      <div><span>单据号：</span>{{ order.orderNo }}</div>
      <div><span>业务类型：</span>{{ bizTypeLabel }}</div>
      <div><span>关联单号：</span>{{ order.relatedNo || '—' }}</div>
      <div><span>状态：</span>{{ order.status === 1 ? '已完成' : '已取消' }}</div>
      <div><span>操作人：</span>{{ order.operatorName || '—' }}</div>
      <div><span>时间：</span>{{ order.createTime || '—' }}</div>
      <div class="p-remark"><span>备注：</span>{{ order.remark || '—' }}</div>
    </div>
    <table class="p-table">
      <thead>
        <tr>
          <th style="width: 36px">#</th>
          <th>SKU编码</th>
          <th>商品名</th>
          <th>规格</th>
          <th style="width: 80px; text-align: right">数量</th>
          <th v-if="hasCost" style="width: 90px; text-align: right">成本价</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(item, i) in order.items || []" :key="item.id || i">
          <td>{{ i + 1 }}</td>
          <td>{{ item.skuCode || '—' }}</td>
          <td>{{ item.skuName || '—' }}</td>
          <td>{{ item.specText || '—' }}</td>
          <td style="text-align: right">{{ item.qty }}</td>
          <td v-if="hasCost" style="text-align: right">{{ item.costPrice ?? '—' }}</td>
        </tr>
      </tbody>
      <tfoot>
        <tr>
          <td :colspan="hasCost ? 4 : 4" style="text-align: right"><b>合计数量</b></td>
          <td style="text-align: right"><b>{{ totalQty }}</b></td>
          <td v-if="hasCost"></td>
        </tr>
      </tfoot>
    </table>
    <div class="p-sign">
      <div>制单人：{{ order.operatorName || '____________' }}</div>
      <div>仓管员：____________</div>
      <div>财务复核：____________</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  order: { type: Object, default: null },
  type: { type: String, default: '' }, // RECEIPT / SHIPMENT / ADJUST
})

const titleText = computed(() => ({
  RECEIPT: '入 库 单',
  SHIPMENT: '出 库 单',
  ADJUST: '库 存 调 整 单',
}[props.type] || '库 存 单 据'))

const hasCost = computed(() => (props.order?.items || []).some(i => i.costPrice != null))
const totalQty = computed(() => (props.order?.items || []).reduce((s, i) => s + (i.qty || 0), 0))

const bizTypeLabel = computed(() => {
  const bt = props.order?.bizType
  if (!bt) return '—'
  // MANUAL 在入库单是手工补录、在出库单是手工出库
  if (bt === 'MANUAL') return props.type === 'SHIPMENT' ? '手工出库' : '手工补录'
  return {
    PURCHASE: '采购入库', RETURN: '退货归还',
    ORDER_SHIP: '订单发货', LOSS: '盘亏出库', ADJUST: '盘点调整',
  }[bt] || bt
})
</script>

<!-- 打印样式必须全局生效，不用 scoped -->
<style>
#stock-order-print { display: none; }

@media print {
  body * { visibility: hidden; }
  #stock-order-print, #stock-order-print * { visibility: visible; }
  #stock-order-print {
    display: block;
    position: absolute;
    left: 0; top: 0;
    width: 100%;
    padding: 24px 32px;
    color: #000;
    font-size: 12px;
  }
  #stock-order-print .p-title { text-align: center; font-size: 20px; letter-spacing: 4px; margin: 0 0 16px; }
  #stock-order-print .p-info { display: flex; flex-wrap: wrap; margin-bottom: 12px; }
  #stock-order-print .p-info > div { width: 33.33%; padding: 3px 0; }
  #stock-order-print .p-info .p-remark { width: 100%; }
  #stock-order-print .p-info span { color: #666; }
  #stock-order-print .p-table { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
  #stock-order-print .p-table th, #stock-order-print .p-table td {
    border: 1px solid #333; padding: 6px 8px; text-align: left; font-weight: normal;
  }
  #stock-order-print .p-table th { background: #f0f0f0; }
  #stock-order-print .p-sign { display: flex; justify-content: space-between; margin-top: 40px; }
}
</style>
