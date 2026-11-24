<template>
  <a-modal v-model:visible="settlementVisible" title="订单结算" width="min(900px, 92vw)" :footer="null">

    <div class="settle-wrapper">

      <!-- ===== 左侧：结算商品列表 ===== -->
      <div class="settle-left">
        <div class="settle-top">
          <div class="addr-icon">
            <EnvironmentOutlined />
          </div>
          <!--1. 加点击事件：点击这个容器，打开弹窗，让用户填地址，弹窗用Address.vue封装-->
          <div class="addr-text" @click="handleAddress">
            <template v-if="defaultAddress">
              <div class="addr-title">{{ defaultAddress.consignee }} {{ maskPhone(defaultAddress.phone) }}</div>
              <div class="addr-tip">{{ defaultAddress.region }} {{ defaultAddress.detail_address }}</div>
            </template>
            <template v-else>
              <div class="addr-title">请选择收货地址</div>
              <div class="addr-tip">请先添加收货地址</div>
            </template>
          </div>
          <!-- 有默认地址才显示编辑按钮 -->
          <div class="edit" v-if="defaultAddress">
            <a-button @click="editExistingAddress" style="margin-right:20px;">编辑地址</a-button>
          </div>
        </div>
        <!-- 店铺信息卡片 -->
        <StoreCard />

        <!-- 全选栏 -->
        <div class="settle-header">
          <div class="header-check" @click="toggleSelectAll">
            <CheckCircleFilled v-if="isAllSelected" class="check-icon checked" />
            <span v-else class="check-circle"></span>
          </div>
          <span class="header-label header-label-info">商品信息</span>
          <span class="header-label header-label-price">单价</span>
          <span class="header-label header-label-qty">数量</span>
          <span class="header-label header-label-sub">小计</span>
        </div>

        <!-- 商品卡片列表 -->
        <div class="settle-items">
          <div
              v-for="item in settlementItems"
              :key="item.product_id"
              class="settle-item-card"
              :class="{ selected: selectedRowKeys.includes(item.product_id) }"
          >
            <!-- 复选框 -->
            <div class="item-check" @click="toggleItem(item.product_id)">
              <CheckCircleFilled v-if="selectedRowKeys.includes(item.product_id)" class="check-icon checked" />
              <span v-else class="check-circle"></span>
            </div>

            <!-- 商品图片 -->
            <img
                :src="`${baseUrl}${item.image.replace(/^\//, '')}`"
                class="item-image"
            />

            <!-- 商品信息 -->
            <div class="item-info">
              <div class="item-name">{{ item.name }}</div>
              <div class="item-meta">
                <span class="item-badge">官方标配</span>
              </div>
              <div class="item-price">
                <span class="price-symbol">￥</span>
                <span class="price-value">{{ item.price }}</span>
              </div>
            </div>

            <!-- 数量 -->
            <div class="item-stepper">
              <button class="step-btn"
                      @click="cartStore.updateQuantity(item.product_id, Math.max(1, item.quantity - 1))">−
              </button>
              <span class="step-num">{{ item.quantity }}</span>
              <button class="step-btn" @click="cartStore.updateQuantity(item.product_id, item.quantity + 1)">+</button>
            </div>

            <!-- 小计 -->
            <div class="item-subtotal">
              <span class="subtotal-symbol">￥</span>
              <span class="subtotal-value">{{ (item.price * item.quantity).toFixed(2) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 右侧：结算卡 ===== -->
      <div class="settle-right">
        <div class="checkout-card">

          <!-- 已选商品缩略图 -->
          <div class="checkout-thumbs" v-if="checkedItems.length">
            <img
                v-for="item in checkedItems"
                :key="item.product_id"
                :src="`${baseUrl}${item.image.replace(/^\//, '')}`"
                class="checkout-thumb"
            />
          </div>

          <!-- 结算标题 -->
          <div class="checkout-title">
            <ShoppingCartOutlined class="checkout-cart-icon" />
            <span>结算明细</span>
          </div>

          <!-- 已选摘要 -->
          <div class="checkout-header">
            <span>付款详情 共 <strong>{{ checkedTotalCount }}</strong> 件商品</span>
          </div>

          <!-- 费用明细 -->
          <div class="checkout-lines">
            <div class="checkout-row">
              <span class="checkout-label">商品总额</span>
              <span class="checkout-value">￥{{ settlementSubtotal }}</span>
            </div>
            <div class="checkout-row" v-if="Number(settlementInsuranceFee) > 0">
              <span class="checkout-label">增值保障</span>
              <span class="checkout-value">￥{{ settlementInsuranceFee }}</span>
            </div>
            <div class="checkout-row">
              <span class="checkout-label">运费</span>
              <span class="checkout-value shipping">{{ shippingText }}</span>
            </div>
          </div>

          <!-- 实付款 -->
          <div class="checkout-total">
            <span class="total-label">合计</span>
            <span class="total-price">
              <span class="total-symbol">￥</span>
              <span class="total-value">{{ settlementTotal }}</span>
            </span>
          </div>

          <!-- 按钮 -->
          <div class="btn-row">
            <a-button class="btn-return" @click="handleReturn">
              返回
            </a-button>
            <a-button class="btn-pay" @click="handlePay">
              提交订单
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </a-modal>
  <!-- 地址管理组件-->
  <Address ref="addressRef" @saved="fetchAddressList" />
  <!--支付订单组件-->
  <Paid ref="paidRef" />
</template>

<script setup>
import {ref, computed} from 'vue';
import {ShoppingCartOutlined, CheckCircleFilled, EnvironmentOutlined} from "@ant-design/icons-vue";
import {useCartStore} from '@/store/cart.js'
import Address from './Address.vue'
import {getAddressList} from '@/api/address.js'
import {addOrder} from '@/api/orders.js'
import {message} from 'ant-design-vue'
import Paid from './Paid.vue'
import StoreCard from './StoreCard.vue'
import {maskPhone} from '@/utils/mask.js'

const baseUrl = import.meta.env.BASE_URL
// 定义打开子组件Paid.vue的实例
const paidRef = ref(null)
// 接收父组件传入的勾选商品 ID 列表
const props = defineProps({
  settlementKeys: {
    type: Array,
    default: () => []
  }
})

// ============ 选中逻辑 ============
const selectedRowKeys = ref([])

const toggleItem = (id) => {
  const idx = selectedRowKeys.value.indexOf(id)
  if (idx > -1) {
    selectedRowKeys.value.splice(idx, 1)
  } else {
    selectedRowKeys.value.push(id)
  }
}

const isAllSelected = computed(() => {
  return settlementItems.value.length > 0 && settlementItems.value.every(i => selectedRowKeys.value.includes(i.product_id))
})

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedRowKeys.value = []
  } else {
    selectedRowKeys.value = settlementItems.value.map(i => i.product_id)
  }
}

// ============ 弹窗控制 ============
const settlementVisible = ref(false)

const settlementVisibleOpen = () => {
  settlementVisible.value = true
  // 打开时默认全选
  selectedRowKeys.value = [...props.settlementKeys]
  // 加载地址列表
  fetchAddressList()
}

// ============ 购物车 ============
const cartStore = useCartStore()

// ============ 计算属性 ============
const settlementItems = computed(() => {
  return cartStore.items.filter(item => props.settlementKeys.includes(item.product_id))
})

const checkedItems = computed(() => {
  return settlementItems.value.filter(item => selectedRowKeys.value.includes(item.product_id))
})

const checkedTotalCount = computed(() => {
  return checkedItems.value.reduce((sum, item) => sum + item.quantity, 0)
})

const settlementSubtotal = computed(() => {
  return checkedItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0).toFixed(2)
})

// 增值保障费合计（单件保障费 × 数量）
const settlementInsuranceFee = computed(() => {
  return checkedItems.value.reduce((sum, item) => sum + (Number(item.insurance_fee) || 0) * item.quantity, 0).toFixed(2)
})

// 全场免运费
const shippingText = '免运费'

const settlementTotal = computed(() => {
  return (Number(settlementSubtotal.value) + Number(settlementInsuranceFee.value)).toFixed(2)
})

// ============ 按钮 ============
// 1. 定义返回的方法,子组件通知父组件用emit
const emit = defineEmits(['back'])
const handleReturn = () => {
  settlementVisible.value = false
  // 2. 通知到父组件，要返回到父组件Cart.vue
  emit('back')
  // 3.回到父组件Cart.vue的 <Settlement />加上 @back
}
defineExpose({settlementVisibleOpen})
// ================= 地址管理 ===================
const addressList = ref([])
const defaultAddress = computed(() => {
  return addressList.value.find(a => a.is_default === 1) || addressList.value[0] || null
})

const fetchAddressList = () => {
  getAddressList().then(res => {
    addressList.value = res.data.data || []
  })
}

const addressRef = ref(null)
const handleAddress = () => {
  addressRef.value.addressVisibleOpen()
}
/* ══════════════════════════════════════════════════════
   编辑地址的按钮
   ══════════════════════════════════════════════════════ */
// 编辑地址
const editExistingAddress = () => {
  addressRef.value.addressVisibleOpen(defaultAddress.value)
}
/* ══════════════════════════════════════════════════════
   提交订单的按钮
   ══════════════════════════════════════════════════════ */
const handlePay = () => {
  if (!defaultAddress.value) {
    message.warning('请先填写收货地址');
    return
  }
  if (!checkedItems.value.length) {
    message.warning('请选择要购买的商品');
    return
  }

  addOrder({
    consignee: defaultAddress.value.consignee,
    phone: defaultAddress.value.phone,
    region: defaultAddress.value.region,
    detail_address: defaultAddress.value.detail_address,
    remark: '',
    items: checkedItems.value.map(i => ({
      product_id: i.product_id,
      quantity: i.quantity,
      insurance_detail: i.insurance_detail || [],
      insurance_fee: i.insurance_fee || 0,
    })),
  }).then(res => {
    const orderId = res.data.data.id
    checkedItems.value.forEach(i => cartStore.removeItem(i.product_id))
    return orderId
  }).then(orderId => {
    // 打开支付订单弹窗
    paidRef.value.paidVisibleOpen(orderId)
    settlementVisible.value = false
  }).catch(e => {
    message.error(e?.response?.data?.message || '下单失败')
  })
}
</script>

<style scoped>
/* ══════════════════════════════════════════════════════
   地址栏
   ══════════════════════════════════════════════════════ */
.settle-top {
  display: flex;
  gap: 14px;
  align-items: center;
  background: #fff;
  padding: 18px 22px;
  border-radius: 16px;
  border: 1px solid #f2f2f2;
  cursor: pointer;
  transition: all 0.2s ease;
}

.settle-top:hover {
  border-color: #ff7820;
  background-color: #fff9f4;
}

.addr-icon {
  font-size: 26px;
  color: #ff7820; /* 和页面橙色主题统一 */
}

.addr-text {
  flex: 1;
}

.addr-title {
  font-size: 16px;
  color: #222;
  font-weight: 500;
}

.addr-tip {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}

.addr-arrow {
  font-size: 18px;
  color: #cccccc;
}

/* ══════════════════════════════════════════════════════
   整体内容布局
   ══════════════════════════════════════════════════════ */
.settle-wrapper {
  display: flex;
  gap: 24px;
}

.settle-left {
  max-width:570px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settle-right {
  flex:1;
  flex-shrink: 0;
  position: sticky;
  top: 10px;
  align-self: flex-start;
}

/* ══════════════════════════════════════════════════════
   店铺信息卡片已抽成 StoreCard.vue（三处共用）
   ══════════════════════════════════════════════════════ */


/* ══════════════════════════════════════════════════════
   全选栏
   ══════════════════════════════════════════════════════ */
.settle-header {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fafafa;
  border-radius: 12px;
  padding: 14px 20px;
  font-size: 16px;
}

.header-check {
  cursor: pointer;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.header-label {
  font-weight: 500;
}

.header-label-info {
  flex: 1;
}

.header-label-price {
  width: 60px;
  text-align: center;
}

.header-label-qty {
  width: 120px;
  text-align: center;
}

.header-label-sub {
  width: 96px;
  text-align: right;
}

/* ══════════════════════════════════════════════════════
   商品卡片列表
   ══════════════════════════════════════════════════════ */
.settle-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settle-item-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, .04);
  padding: 6px 20px;
  border: 2px solid transparent;
  transition: border-color .2s;
}

.settle-item-card.selected {
  border-color: #FFB380;
}

/* 复选框 */
.item-check {
  cursor: pointer;
  flex-shrink: 0;
}

.check-circle {
  display: block;
  width: 22px;
  height: 22px;
  border: 2px solid #d0d0d0;
  border-radius: 50%;
  transition: border-color .2s;
}

.settle-item-card.selected .check-circle {
  border-color: #FF6700;
}

.check-icon.checked {
  font-size: 24px;
  color: #FF6700;
}

/* 商品图片 */
.item-image {
  width: 60px;
  height: 50px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
}

/* 商品信息 */
.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 16px;
  font-weight: 600;
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-badge {
  font-size: 12px;
  color: #999;
  background: #f5f5f5;
  padding: 2px 10px;
  border-radius: 4px;
}

.item-price {
  color: #FF6700;
  font-weight: 600;
}

.price-symbol {
  font-size: 13px;
}

.price-value {
  font-size: 18px;
}

/* 数量步进器 */
.item-stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
}

.step-btn {
  width: 26px;
  height: 26px;
  border: none;
  background: #fff;
  font-size: 20px;
  color: #333;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background .15s;
}

.step-btn:hover {
  background: #f5f5f5;
}

.step-btn:first-child {
  border-right: 1px solid #e0e0e0;
}

.step-btn:last-child {
  border-left: 1px solid #e0e0e0;
}

.step-num {
  width: 48px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: #111;
}

/* 小计 */
.item-subtotal {
  flex-shrink: 0;
  text-align: right;
  min-width: 96px;
}

.subtotal-symbol {
  font-size: 13px;
  color: #FF6700;
}

.subtotal-value {
  font-size: 20px;
  color: #FF6700;
  font-weight: 700;
}

/* ══════════════════════════════════════════════════════
   右侧 Sticky 结算卡
   ══════════════════════════════════════════════════════ */
.checkout-card {
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, .05);
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 商品缩略图 */
.checkout-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.checkout-thumb {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid #eee;
}

/* 结算标题 */
.checkout-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.checkout-cart-icon {
  font-size: 28px;
  color: #FF6700;
}

/* 已选摘要 */
.checkout-header {
  text-align: center;
  font-size: 14px;
  color: #FF6700;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0f0f0;
}

.checkout-header strong {
  font-size: 18px;
}

/* 费用明细 */
.checkout-lines {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.checkout-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
}

.checkout-label {
  color: #666;
}

.checkout-value {
  color: #333;
  font-weight: 500;
}

.checkout-value.discount {
  color: #FF6700;
}

.checkout-value.shipping {
  color: #22BC66;
}

/* 实付款 */
.checkout-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 16px 0;
  border-top: 1px solid #f0f0f0;
}

.total-label {
  font-size: 15px;
  color: #333;
  font-weight: 600;
}

.total-symbol {
  font-size: 16px;
  color: #FF4E2A;
}

.total-value {
  font-size: 32px;
  color: #FF4E2A;
  font-weight: 700;
}

/* 按钮 */
.btn-row {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.btn-return {
  width: 120px;
  height: 40px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 500;
  background: #fff;
  color: #999;
  border: 1px solid #e0e0e0;
}

.btn-return:hover {
  border-color: #FFB380;
  color: #FF6700;
}

.btn-pay {
  width: 120px;
  height: 40px;
  border-radius: 999px;
  font-size: 17px;
  font-weight: 600;
  border: none;
  background: linear-gradient(135deg, #FF4E2A, #FF7A00);
  color: #fff;
  transition: opacity .2s;
}

.btn-pay:hover {
  opacity: .9;
  color: #fff;
}

/* ══════════════════════════════════════════════════════
   响应式：笔记本双栏可换行，手机单列
   ══════════════════════════════════════════════════════ */
@media (max-width: 1200px) {
  .settle-wrapper {
    flex-wrap: wrap;
  }
  .settle-left {
    max-width: 100%;
    flex: 1 1 480px;
  }
  .settle-right {
    width: 100%;
    flex: 1 1 100%;
    position: static;
  }
}

@media (max-width: 767px) {
  .settle-wrapper {
    display: block;
  }
  .settle-header {
    display: none;
  }
  .settle-right {
    margin-top: 16px;
  }
  .settle-top {
    flex-wrap: wrap;
    gap: 8px;
  }
  .settle-item-card {
    flex-wrap: wrap;
    gap: 10px;
    padding: 12px;
  }
  .item-info {
    flex: 1 1 calc(100% - 110px);
  }
  .item-stepper {
    order: 5;
    margin-left: auto;
  }
  .item-subtotal {
    order: 6;
  }
}
</style>
