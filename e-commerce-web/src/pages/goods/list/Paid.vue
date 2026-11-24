<template>
  <a-modal
      v-model:visible="paidVisible"
      :width="900"
      :footer="null"
      :closable="false"
      wrap-class-name="pay-modal"
  >
    <div class="pay-wrapper">

      <!-- 顶栏 -->
      <div class="pay-header">
        <div class="pay-header-left">
          <span class="pay-header-title">收银台</span>
          <span class="pay-header-sub">订单支付</span>
        </div>
        <a-button class="pay-close" @click="paidVisible = false">
          <template #icon>
            <CloseOutlined />
          </template>
        </a-button>
      </div>

      <!-- 主体 -->
      <div class="pay-body" v-if="orderData">

        <!-- 左侧：订单信息 -->
        <div class="pay-left">

          <div class="info-card">
            <div class="info-row">
              <span>订单号 {{ orderData.order_no }}</span>
              <span>{{ formatTime(orderData.created_at) }}</span>
            </div>
          </div>

          <!-- 商品列表 -->
          <div class="goods-list">
            <div
                class="goods-item"
                v-for="item in orderData.items"
                :key="item.id"
            >
              <img :src="item.image" class="goods-img" alt="商品" />
              <div class="goods-info">
                <div class="goods-name">{{ item.product_name }}</div>
                <div class="goods-meta">
                  <span class="goods-price">¥{{ item.sales_price }}</span>
                  <span class="goods-qty">× {{ item.quantity }}</span>
                </div>
              </div>
              <span class="goods-sub">¥{{ item.sub_total }}</span>
            </div>
          </div>

          <!-- 费用明细 -->
          <div class="cost-lines">
            <div class="cost-row">
              <span class="cost-label">商品总额</span>
              <span class="cost-val">¥{{ orderData.pay_amount }}</span>
            </div>
            <div class="cost-row">
              <span class="cost-label">运费</span>
              <span class="cost-val free">免运费</span>
            </div>
          </div>
        </div>

        <!-- 右侧：支付方式 -->
        <div class="pay-right">
          <div class="pay-methods">
            <div
                class="method-item"
                :class="{ active: payType === 'WeChat' }"
                @click="payType = 'WeChat'"
            >
              <span class="method-name">微信支付</span>
              <CheckCircleFilled v-if="payType === 'WeChat'" class="method-check" />
            </div>
            <div
                class="method-item"
                :class="{ active: payType === 'Alipay' }"
                @click="payType = 'Alipay'"
            >
              <span class="method-name">支付宝</span>
              <CheckCircleFilled v-if="payType === 'Alipay'" class="method-check" />
            </div>
            <div
                class="method-item"
                :class="{ active: payType === 'BankCard' }"
                @click="payType = 'BankCard'"
            >
              <span class="method-name">银行卡</span>
              <CheckCircleFilled v-if="payType === 'BankCard'" class="method-check" />
            </div>
          </div>

          <div class="qrcode-card">
            <div class="qrcode-img">
              <a-image :src="`${baseUrl}image/QRcode.webp`" :preview="false" />
            </div>
            <p class="qrcode-text">{{ payTypeLabel }}</p>
            <p class="qrcode-hint">请使用{{ payTypeName }}扫描二维码完成支付</p>
          </div>

        </div>

      </div>

      <!-- 底部操作栏 -->
      <div class="pay-footer" v-if="orderData">
        <span class="footer-countdown">支付剩余 <strong>{{ countdown }}</strong></span>
        <div class="footer-btns">
          <a-button class="btn-cancel" @click="paidVisible = false">稍后支付</a-button>
          <a-button class="btn-confirm" @click="handlePay(orderData.id)">我已支付 ¥{{ orderData.pay_amount }}</a-button>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
// ==================== 依赖导入 ====================
import {ref, computed} from "vue";
import {message} from "ant-design-vue";
import {CloseOutlined, CheckCircleFilled} from "@ant-design/icons-vue";
import {getPendingOrderList, addPaidOrder} from '@/api/orders.js'

// ==================== 基础配置 ====================
const baseUrl = import.meta.env.BASE_URL;

// 支付方式映射
const PAY_METHODS = {
  WeChat:  { label: '打开微信扫一扫', name: '微信' },
  Alipay:  { label: '打开支付宝扫一扫', name: '支付宝' },
  BankCard: { label: '输入银行卡号支付', name: '银行卡' },
};

// ==================== 响应式状态 ====================
const paidVisible = ref(false);    // 弹窗显隐
const orderData = ref(null);       // 订单详情数据
const countdown = ref('');         // 支付倒计时
const payType = ref('WeChat');     // 当前选中的支付方式

// ==================== 计算属性 ====================
const payTypeLabel = computed(() => PAY_METHODS[payType.value]?.label || '');
const payTypeName  = computed(() => PAY_METHODS[payType.value]?.name  || '');

// ==================== 弹窗控制 ====================
// 父组件通过 ref 调用：打开弹窗并加载订单数据
const paidVisibleOpen = (id) => {
  paidVisible.value = true;
  getPendingOrderList(id).then(res => {
    orderData.value = res.data.data;
    startCountdown(res.data.data.created_at);
  });
};

// ==================== 工具函数 ====================
// 格式化 ISO 时间为 yyyy-MM-dd HH:mm
const formatTime = (iso) => {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

// 启动 30 分钟支付倒计时
const startCountdown = (createdAt) => {
  clearInterval(timer);
  const end = new Date(createdAt).getTime() + 30 * 60 * 1000;
  const tick = () => {
    const left = Math.max(0, end - Date.now());
    const m = Math.floor(left / 60000);
    const s = Math.floor((left % 60000) / 1000);
    countdown.value = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    if (left <= 0) clearInterval(timer);
  };
  tick();
  timer = setInterval(tick, 1000);
};
let timer = null;

// ==================== 订单操作 ====================
// 确认支付 → PUT /api/orders/:id/pay
const handlePay = (id) => {
  addPaidOrder(id, { pay_type: payType.value }).then(() => {
    message.success('支付成功');
    paidVisible.value = false;
    emit('paid');
  }).catch(e => {
    message.error(e?.response?.data?.message || '支付失败');
  });
};

// ==================== 暴露给父组件 ====================
const emit = defineEmits(['paid'])
defineExpose({paidVisibleOpen})
</script>

<style scoped>
/* ── 全局 ── */
.pay-wrapper {
  font-family: 'PingFang SC', -apple-system, BlinkMacSystemFont, sans-serif;
  padding: 0 4px;
}

/* ── 顶栏 ── */
.pay-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 20px;
}

.pay-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.pay-header-title {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a1a;
}

.pay-header-sub {
  font-size: 13px;
  color: #999;
}

.pay-close {
  border: none;
  color: #999;
  font-size: 16px;
  box-shadow: none;
}

/* ── 主体 ── */
.pay-body {
  display: flex;
  gap: 28px;
}

/* ── 左侧 ── */
.pay-left {
  flex: 1;
  min-width: 0;
}

/* 金额卡 */
.info-card {
  background: #fff;
  border-radius: 12px;
  padding: 10px 16px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, .04);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #666;
}

/* 商品列表 */
.goods-list {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, .04);
  padding: 8px 0;
  margin-bottom: 12px;
}

.goods-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
}

.goods-item + .goods-item {
  border-top: 1px solid #f5f5f5;
}

.goods-img {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  min-width: 0;
}

.goods-name {
  font-size: 14px;
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.goods-meta {
  display: flex;
  gap: 12px;
}

.goods-price {
  font-size: 13px;
  color: #999;
}

.goods-qty {
  font-size: 13px;
  color: #999;
}

.goods-sub {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

/* 费用明细 */
.cost-lines {
  background: #f8f9fc;
  border-radius: 12px;
  padding: 12px 20px;
}

.cost-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 13px;
}

.cost-label {
  color: #888;
}

.cost-val {
  color: #333;
  font-weight: 500;
}

.cost-val.free {
  color: #52c41a;
}

.cost-val.discount {
  color: #fa541c;
}

/* ── 右侧 ── */
.pay-right {
  width: 280px;
  flex-shrink: 0;
}

/* 支付方式 */
.pay-methods {
  background: #f8f9fc;
  border-radius: 12px;
  padding: 4px;
  display: flex;
  margin-bottom: 16px;
}

.method-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  border-radius: 10px;
  font-size: 13px;
  color: #888;
  cursor: pointer;
  transition: all .2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.method-item.active {
  background: #fff;
  color: #FF6700;
  font-weight: 500;
  box-shadow: 0 1px 4px rgba(0, 0, 0, .06);
}

.method-check {
  font-size: 14px;
}

/* 二维码卡 */
.qrcode-card {
  background: #f8f9fc;
  border-radius: 12px;
  padding: 24px;
  text-align: center;
  margin-bottom: 16px;
}

.qrcode-img {
  width: 160px;
  height: 160px;
  margin: 0 auto 12px;
  background: #fff;
  border-radius: 10px;
  padding: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, .04);
}

.qrcode-img :deep(.ant-image) {
  width: 100%;
  height: 100%;
}

.qrcode-img :deep(.ant-image-img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.qrcode-text {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin: 0 0 4px;
}

.qrcode-hint {
  font-size: 12px;
  color: #999;
  margin: 0;
}

/* 底部操作栏 */
.pay-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 20px;
  margin-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.footer-countdown {
  font-size: 13px;
  color: #999;
}

.footer-countdown strong {
  color: #FF6700;
  font-weight: 600;
}

.footer-btns {
  display: flex;
  gap: 10px;
}

.btn-cancel {
  width: 100%;
  height: 40px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  background: #fff;
  color: #666;
  font-size: 14px;
  box-shadow: none;
}

.btn-confirm {
  width: 100%;
  height: 40px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(135deg, #FF6700, #FF8533);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(255, 78, 42, .3);
  transition: all .2s;
}

.btn-confirm:hover {
  background: linear-gradient(135deg, #FF6700, #FFB380);
  box-shadow: 0 6px 20px rgba(255, 78, 42, .4);
  color: #fff;
}

/* ── 响应式：笔记本双栏可换行，手机单列 ── */
@media (max-width: 1200px) {
  .pay-body {
    flex-wrap: wrap;
  }
  .pay-left {
    flex: 1 1 420px;
  }
  .pay-right {
    flex: 1 1 260px;
  }
}

@media (max-width: 767px) {
  .pay-body {
    display: block;
  }
  .pay-right {
    margin-top: 16px;
  }
  .pay-footer {
    flex-wrap: wrap;
    gap: 10px;
  }
  .footer-btns {
    width: 100%;
  }
  .goods-item {
    flex-wrap: wrap;
  }
  .goods-sub {
    margin-left: auto;
  }
}
</style>

<style>
.pay-modal .ant-modal-content {
  border-radius: 16px;
  overflow: hidden;
}

.pay-modal .ant-modal-header {
  display: none;
}

.pay-modal .ant-modal-body {
  padding: 24px 28px 28px;
}
</style>
