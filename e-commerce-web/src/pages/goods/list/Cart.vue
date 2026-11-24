<template>
  <a-modal v-model:visible="myCartVisible" width="min(900px, 92vw)" :footer="null">
    <div class="cart-wrapper">

      <!-- ===== 左侧：商品列表 ===== -->
      <div class="cart-left">

        <!-- 店铺信息卡片 -->
        <StoreCard />

        <!-- 全选栏 -->
        <div class="cart-header">
          <div class="header-check" @click="toggleSelectAll">
            <CheckCircleFilled v-if="isAllSelected" class="check-icon checked" />
            <span v-else class="check-circle"></span>
          </div>
          <span class="header-label header-label-info">商品信息</span>
          <span class="header-label header-label-price">单价</span>
          <span class="header-label header-label-qty">数量</span>
          <span class="header-label header-label-sub">小计</span>
          <span class="header-label header-label-act">操作</span>
        </div>

        <!-- 商品卡片列表 -->
        <div class="cart-items">
          <div
              v-for="item in cartStore.items"
              :key="item.product_id"
              class="cart-item-card"
              :class="{ selected: selectedIds.has(item.product_id) }"
          >
            <!-- 复选框 -->
            <div class="item-check" @click="toggleSelect(item.product_id)">
              <CheckCircleFilled v-if="selectedIds.has(item.product_id)" class="check-icon checked" />
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

            <!-- 操作 -->
            <div class="item-actions">
              <a-button class="action-btn" @click="cartStore.removeItem(item.product_id)">
                <DeleteOutlined />
                删除
              </a-button>
              <a-button class="action-btn">
                <StarOutlined />
                收藏
              </a-button>
            </div>
          </div>

          <!-- 操作通栏 -->
          <div v-if="cartStore.items.length" class="cart-action-bar">
            <span>已选中 <strong>{{ selectedCount }}</strong> 件商品</span>
            <a-button class="batch-delete-btn" :disabled="!selectedIds.size" @click="handleBatchDelete">
              <DeleteOutlined />
              批量删除
            </a-button>
          </div>

          <!-- 空购物车 -->
          <div v-if="!cartStore.items.length" class="cart-empty">
            <ShoppingCartOutlined class="empty-icon" />
            <p>购物车是空的，去逛逛吧</p>
          </div>
        </div>
      </div>

      <!-- ===== 右侧：结算卡 ===== -->
      <div class="cart-right">
        <div class="checkout-card">

          <!-- 已选商品摘要 -->
          <div class="checkout-header">
            <ShoppingCartOutlined class="checkout-cart-icon" />
            <span>已选 <strong>{{ selectedCount }}</strong> 件商品</span>
          </div>

          <!-- 费用明细 -->
          <div class="checkout-lines">
            <div class="checkout-row">
              <span class="checkout-label">商品总额</span>
              <span class="checkout-value">￥{{ selectedSubtotal }}</span>
            </div>
            <div class="checkout-row" v-if="Number(selectedInsuranceFee) > 0">
              <span class="checkout-label">增值保障</span>
              <span class="checkout-value">￥{{ selectedInsuranceFee }}</span>
            </div>
            <div class="checkout-row">
              <span class="checkout-label">运费</span>
              <span class="checkout-value shipping">{{ shippingText }}</span>
            </div>
          </div>

          <!-- 实付款 -->
          <div class="checkout-total">
            <span class="total-label">实付款</span>
            <span class="total-price">
              <span class="total-symbol">￥</span>
              <span class="total-value">{{ finalTotal }}</span>
            </span>
          </div>

          <!-- 按钮 -->
          <div class="btn-row">
            <a-button class="btn-settle" @click="handleSettlement" :disabled="!selectedCount">
              去支付
            </a-button>
            <a-button class="btn-continue" @click="cancelSettlement">
              继续购物
            </a-button>
          </div>

          <!-- 服务保障 -->
          <div class="checkout-services">
            <div class="cs-item">
              <SafetyCertificateOutlined />
              <span>正品保证</span></div>
            <div class="cs-item">
              <ThunderboltOutlined />
              <span>极速发货</span></div>
            <div class="cs-item">
              <ClockCircleOutlined />
              <span>七天退换</span></div>
            <div class="cs-item">
              <EnvironmentOutlined />
              <span>官方售后</span></div>
          </div>
        </div>
      </div>
    </div>
  </a-modal>

  <!-- 结算弹窗 -->
  <Settlement ref="settlementRef" :settlement-keys="[...selectedIds]" @back="myCartVisible = true" />
</template>

<script setup>
import {ref, computed} from "vue";
import {
  ShoppingCartOutlined,
  CheckCircleFilled,
  DeleteOutlined,
  StarOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined
} from '@ant-design/icons-vue'
import {Modal, message} from 'ant-design-vue'
import {useCartStore} from "@/store/cart.js";
import Settlement from "@/pages/goods/list/Settlement.vue";
import StoreCard from './StoreCard.vue'

const baseUrl = import.meta.env.BASE_URL
const myCartVisible = ref(false);
const cartStore = useCartStore()

// ============ 选中逻辑 ============
const selectedIds = ref(new Set())

const toggleSelect = (id) => {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}

const isAllSelected = computed(() => {
  return cartStore.items.length > 0 && cartStore.items.every(i => selectedIds.value.has(i.product_id))
})

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(cartStore.items.map(i => i.product_id))
  }
}

// ============ 批量删除 ============
const handleBatchDelete = () => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除选中的 ${selectedIds.value.size} 件商品吗？`,
    okText: '确定删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => {
      selectedIds.value.forEach(id => cartStore.removeItem(id))
      selectedIds.value = new Set()
      message.success('已删除')
    }
  })
}

// ============ 计算属性 ============
const selectedItems = computed(() => {
  return cartStore.items.filter(i => selectedIds.value.has(i.product_id))
})

const selectedCount = computed(() => {
  return selectedItems.value.reduce((sum, i) => sum + i.quantity, 0)
})

const selectedSubtotal = computed(() => {
  return selectedItems.value.reduce((sum, i) => sum + i.price * i.quantity, 0).toFixed(2)
})

// 增值保障费合计（单件保障费 × 数量）
const selectedInsuranceFee = computed(() => {
  return selectedItems.value.reduce((sum, i) => sum + (Number(i.insurance_fee) || 0) * i.quantity, 0).toFixed(2)
})

// 全场免运费
const shippingText = '免运费'

const finalTotal = computed(() => {
  return (Number(selectedSubtotal.value) + Number(selectedInsuranceFee.value)).toFixed(2)
})

// ============ 弹窗控制 ============
const myCartVisibleOpen = () => {
  myCartVisible.value = true;
  selectedIds.value = new Set()
}
defineExpose({myCartVisibleOpen})

const settlementRef = ref(null)
const handleSettlement = () => {
  if (!selectedCount.value) return
  settlementRef.value.settlementVisibleOpen()
  myCartVisible.value = false
}
const cancelSettlement = () => {
  myCartVisible.value = false
}
</script>

<style scoped lang="less">
/* ══════════════════════════════════════════════════════
   整体布局
   ══════════════════════════════════════════════════════ */
.cart-wrapper {
  display: flex;
  gap: 16px;
}

.cart-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ══════════════════════════════════════════════════════
   店铺信息卡片已抽成 StoreCard.vue（三处共用）
   ══════════════════════════════════════════════════════ */


/* ══════════════════════════════════════════════════════
   全选栏
   ══════════════════════════════════════════════════════ */
.cart-header {
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
  width: 80px;
  text-align: right;
}

.header-label-act {
  width: 72px;
  text-align: center;
}

/* ══════════════════════════════════════════════════════
   操作通栏
   ══════════════════════════════════════════════════════ */
.cart-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, .04);
  padding: 16px 20px;
  font-size: 14px;
  color: #666;
}

.cart-action-bar strong {
  color: #FF6700;
  font-size: 16px;
}

.batch-delete-btn {
  border: 1px solid #ffccc7;
  color: #ff4d4f;
  background: #fff;
  font-size: 13px;
  border-radius: 8px;
  padding: 4px 16px;
}

.batch-delete-btn:hover {
  color: #fff;
  background: #ff4d4f;
  border-color: #ff4d4f;
}

.batch-delete-btn:disabled {
  color: #d9d9d9;
  border-color: #e0e0e0;
  background: #f5f5f5;
}

/* ══════════════════════════════════════════════════════
   商品卡片列表
   ══════════════════════════════════════════════════════ */
.cart-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cart-item-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, .04);
  padding: 6px 14px;
  border: 2px solid transparent;
  transition: border-color .2s;
}

.cart-item-card.selected {
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

.cart-item-card.selected .check-circle {
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
  margin-bottom: 6px;
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
  width: 20px;
  height: 20px;
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
  width: 40px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: #111;
}

/* 小计 */
.item-subtotal {
  flex-shrink: 0;
  text-align: right;
  min-width: 70px;
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

/* 操作按钮 */
.item-actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.action-btn {
  border: none;
  background: none;
  color: #999;
  font-size: 13px;
  padding: 2px 4px;
  cursor: pointer;
  box-shadow: none;
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-btn:hover {
  color: #FF6700;
}

/* 空购物车 */
.cart-empty {
  text-align: center;
  padding: 60px 0;
  color: #ccc;
}

.empty-icon {
  font-size: 64px;
}

.cart-empty p {
  font-size: 16px;
  margin-top: 12px;
  color: #999;
}

/* ══════════════════════════════════════════════════════
   右侧 Sticky 结算卡
   ══════════════════════════════════════════════════════ */
.cart-right {
  width: 280px;
  flex-shrink: 0;
  position: sticky;
  top: 10px;
  align-self: flex-start;
}

.checkout-card {
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, .05);
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 已选摘要 */
.checkout-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: #333;
  padding-bottom: 14px;
  border-bottom: 1px solid #f0f0f0;
}

.checkout-header strong {
  color: #FF6700;
  font-size: 18px;
}

.checkout-cart-icon {
  font-size: 20px;
  color: #FF6700;
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
  padding: 10px 0;
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

.btn-settle {
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

.btn-settle:hover {
  opacity: .9;
  color: #fff;
}

.btn-settle:disabled {
  opacity: .4;
  cursor: not-allowed;
}

.btn-continue {
  width: 120px;
  height: 40px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 500;
  background: #fff;
  color: #999;
  border: 1px solid #e0e0e0;
}

.btn-continue:hover {
  border-color: #FFB380;
  color: #FF6700;
}

/* 服务保障 */
.checkout-services {
  display: flex;
  flex-wrap: wrap;
  row-gap: 6px;
  padding-top: 8px;
  border-top: 1px solid #f5f5f5;
}

.cs-item {
  flex: 0 0 50%;
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #999;
}

.cs-item :deep(.anticon) {
  font-size: 14px;
  color: #ccc;
}

/* ══════════════════════════════════════════════════════
   响应式：笔记本双栏可换行，手机单列
   ══════════════════════════════════════════════════════ */
@media (max-width: 1200px) {
  .cart-wrapper {
    flex-wrap: wrap;
  }
  .cart-left {
    flex: 1 1 480px;
  }
  .cart-right {
    width: auto;
    flex: 1 1 260px;
    position: static;
  }
}

@media (max-width: 767px) {
  .cart-wrapper {
    display: block;
  }
  /* 手机上表头与卡片行结构对不上，直接隐藏表头 */
  .cart-header {
    display: none;
  }
  .cart-right {
    width: 100%;
    margin-top: 16px;
  }
  .cart-item-card {
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
  .item-actions {
    order: 7;
    width: 100%;
    flex-direction: row;
    justify-content: flex-end;
  }
  .cart-action-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
