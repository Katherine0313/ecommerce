<template>
  <a-modal v-model:visible="mallVisible" class="preview-modal" title="商品预览" :footer="null" @cancel="handleCancel">
    <div class="mall-container">
      <div class="l">
        <StoreCard />
        <a-image :src="product.image ? `${baseUrl}${product.image.replace(/^\//, '')}` : ''" class="product-img" />
        <div class="detail-title"><span>商品详情</span></div>
        <div class="detail-content" v-html="product.body"></div>
      </div>
      <div class="r">
        <div class="r-content">

          <!-- ===== 国家补贴横幅 ===== -->
          <div class="subsidy-banner">
            <div class="subsidy-left">
              <AccountBookOutlined class="subsidy-icon" />
              <span>领取国家补贴15%</span>
            </div>
            <a-button class="subsidy-btn">立即领取</a-button>
          </div>

          <!-- ===== 商品标题 ===== -->
          <h1 class="product-title">{{ product.title }}</h1>
          <p class="product-tagline">{{ product.subTitle }}</p>

          <!-- ===== 价格卡 ===== -->
          <div class="price-card">
            <div class="price-main">
              <span class="price-label">平台加补后</span>
              <span class="price-symbol">￥</span>
              <span class="price-value">{{ product.salesPrice }}</span>
            </div>
            <!-- 标签被「在网店显示」关掉后接口返回 null，这里不判断会留一个空胶囊 -->
            <div class="tag" v-if="product.tag_name">{{ product.tag_name }}</div>
          </div>

          <!-- ===== 商品信息 ===== -->
          <div class="info-card">
            <div class="info-row">
              <span class="info-label">规格</span>
              <span class="info-value">官方标配</span>
            </div>
            <div class="info-row">
              <span class="info-label">配送</span>
              <span class="info-value">商家配送，预计1-3天送达</span>
            </div>
            <div class="info-row">
              <span class="info-label">库存</span>
              <span class="info-value stock">326件</span>
            </div>
          </div>

          <!-- ===== 服务保障 ===== -->
          <div class="assurance-row">
            <div class="assurance-item">
              <SafetyCertificateOutlined />
              <span>正品保证</span>
            </div>
            <div class="assurance-item">
              <ThunderboltOutlined />
              <span>极速退款</span>
            </div>
            <div class="assurance-item">
              <ClockCircleOutlined />
              <span>七天无理由</span>
            </div>
            <div class="assurance-item">
              <EnvironmentOutlined />
              <span>全国联保</span>
            </div>
          </div>
          <!-- ===== 数量选择器 ===== -->
          <div class="quantity-row">
            <span class="quantity-label">数量</span>
            <div class="quantity-stepper">
              <button class="step-btn" @click="quantity > 1 && quantity--">−</button>
              <span class="step-value">{{ quantity }}</span>
              <button class="step-btn" @click="quantity++">+</button>
            </div>
          </div>

          <!-- ===== 增值保障 ===== -->
          <div class="insurance-section">
            <div class="insurance-title">增值保障</div>
            <div class="insurance-cards">
              <div
                  class="insurance-card"
                  :class="{ active: selectedInsurance2 }"
                  @click="selectedInsurance2 = !selectedInsurance2"
              >
                <div class="insurance-name">一年意外保</div>
                <div class="insurance-price">¥{{ insurance2 }}</div>
              </div>
              <div
                  class="insurance-card"
                  :class="{ active: selectedInsurance1 }"
                  @click="selectedInsurance1 = !selectedInsurance1"
              >
                <div class="insurance-name">全面保修</div>
                <div class="insurance-price">¥{{ insurance1 }}</div>
              </div>
              <div
                  class="insurance-card"
                  :class="{ active: selectedBattery }"
                  @click="selectedBattery = !selectedBattery"
              >
                <div class="insurance-name">电池无忧</div>
                <div class="insurance-price">¥20</div>
              </div>
            </div>
          </div>

          <!-- ===== 购买按钮 ===== -->
          <div class="buy-row">
            <a-button class="btn-cart" @click="handleAddToCart">加入购物车</a-button>
            <a-button class="btn-buy" @click="handlePay">立即购买</a-button>
          </div>

          <!-- 最终支付价格 -->
          <div class="final-price" v-if="product.salesPrice">
            合计：<span>￥{{ finalPrice }}</span>
          </div>

        </div>
      </div>
    </div>
  </a-modal>
  <!--购物车弹窗-->
  <a-modal v-model:visible="cartVisible" class="cart-modal" centered>
    <div class="cart-text">
      <span> <!-- 绿色填充圆形+白色对勾，和淘宝弹窗同款 -->
      <svg width="28" height="28" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <!-- 绿色圆底色 -->
        <circle cx="12" cy="12" r="12" fill="#22c55e" />
        <!-- 白色对勾 -->
        <polyline points="7 12 10.5 15.5 17 8.5" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round"
                  stroke-linejoin="round" />
      </svg></span>
      <span>成功加入购物车</span>
    </div>
    <div class="cart-btn">
      <a-button style="background: #FF6700; border:1px solid #FF6700; color:#fff" @click="handleGoCart">
        去购物车
      </a-button>
      <a-button type="primary" @click="mallVisible = false; cartVisible = false">继续购物</a-button>
    </div>
    <template #footer></template>
  </a-modal>
  <!--引入购物车弹窗，点击去购物车，打开该弹窗-->
  <Cart ref="cartRef" />
  <!--引入支付弹窗-->
  <Settlement ref="settlementRef" :settlementKeys="[productId]" />
</template>
<script setup>
import {ref, watch, computed} from 'vue'
import {getGoodsDetail} from '@/api/products.js'
import {useCartStore} from '@/store/cart.js'
import {
  AccountBookOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  CheckCircleOutlined,
  SafetyOutlined,
  CarOutlined,
  DollarOutlined
} from '@ant-design/icons-vue'
import Cart from './Cart.vue'
import StoreCard from './StoreCard.vue'
import Settlement from './Settlement.vue'
// 父组件传入：visible 控制弹窗显隐，productId 指定要预览的商品
const props = defineProps({
  visible: {type: Boolean, default: false},
  productId: {type: [String, Number], default: ''}
})
const emit = defineEmits(['update:visible'])

// v-model 不能直接绑 prop，用 computed 做中转：读从 prop，写走 emit
const mallVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// 商品详情数据
const product = ref({})
// 购买数量
const quantity = ref(1)
// 保障服务选中状态
const selectedInsurance1 = ref(false)  // 全面保修
const selectedInsurance2 = ref(false)  // 一年意外保
const selectedBattery = ref(false)     // 电池无忧

// 保障服务价格：售价的 1%
const insurance1 = computed(() => {
  if (!product.value.salesPrice) return ''
  return (product.value.salesPrice * 0.01).toFixed(2)
})
// 保障服务价格：售价的 2%
const insurance2 = computed(() => {
  if (!product.value.salesPrice) return ''
  return (product.value.salesPrice * 0.02).toFixed(2)
})

// 选中的增值保障明细（JSON 数组，随购物车/订单一起存）
const insuranceDetail = computed(() => {
  const list = []
  if (selectedInsurance1.value) list.push({code: 'warranty', name: '全面保修', fee: Number(insurance1.value)})
  if (selectedInsurance2.value) list.push({code: 'accident', name: '一年意外保', fee: Number(insurance2.value)})
  if (selectedBattery.value) list.push({code: 'battery', name: '电池无忧', fee: 20})
  return list
})

// 单件保障费合计
const insuranceFee = computed(() => {
  let fee = 0
  if (selectedInsurance1.value) fee += Number(insurance1.value)
  if (selectedInsurance2.value) fee += Number(insurance2.value)
  if (selectedBattery.value) fee += 20
  return fee.toFixed(2)
})

// 最终支付价格 = 商品售价 + 单件保障费（与后端 salesPrice 计费口径一致）
const finalPrice = computed(() => {
  const base = Number(product.value.salesPrice) || 0
  return (base + Number(insuranceFee.value)).toFixed(2)
})
// Vite base 路径，确保图片在开发和生产环境都能正确加载
const baseUrl = import.meta.env.BASE_URL

// 购物车 store
const cartStore = useCartStore()
// 购物车弹窗
const cartVisible = ref(false)
// 加入购物车
const handleAddToCart = () => {
  cartStore.addItem(
      props.productId,
      {
        name: product.value.name,
        image: product.value.image,
        price: Number(product.value.salesPrice) || 0,   // 纯商品售价（不含保障费）
        description: product.value.title,
        insurance_detail: insuranceDetail.value,       // 增值保障明细
        insurance_fee: Number(insuranceFee.value)      // 单件保障费
      },
      quantity.value
  )
  cartVisible.value = true
}

// 监听 productId 变化，拉取对应商品详情
watch(() => props.productId, (id) => {
  if (!id) return
  // 切换商品时重置增值保障选中状态和数量，避免上一个商品的状态被误带到当前商品
  selectedInsurance1.value = false
  selectedInsurance2.value = false
  selectedBattery.value = false
  quantity.value = 1
  getGoodsDetail(id)
      .then(res => {
        if (String(props.productId) !== String(id)) return
        product.value = res.data.data
        // 详情富文本里的图片是原图，打开弹窗会几十张全量加载导致卡顿；
        // 给每个 <img> 补 loading="lazy"，首屏只加载可视区图片（浏览器原生懒加载）
        if (product.value && product.value.body) {
          product.value.body = product.value.body.replace(/<img\b/gi, '<img loading="lazy" ')
        }
        console.log(res)
      })
      .catch(err => {
        console.log('商品详情加载失败', err)
      })
}, {immediate: true})


// 关闭弹窗
const handleCancel = () => {
  emit('update:visible', false)
}
// ============购物车弹窗 ===================
// 定义购物车弹窗实例
const cartRef = ref(null)
// 去购物车
const handleGoCart = () => {
  // 打开弹窗
  cartRef.value.myCartVisibleOpen()
  cartVisible.value = false
  mallVisible.value = false
}
// 立即购买按钮：先清购物车旧记录 → 加入当前商品 → 打开结算页（结算页内部完成 选地址 → 下单 → 支付）
const settlementRef = ref(null)
const handlePay = async () => {
  // 先清掉购物车里该商品的旧记录，保证结算数量 = 当前选择的数量
  await cartStore.removeItem(props.productId)
  await cartStore.addItem(
      props.productId,
      {
        name: product.value.name,
        image: product.value.image,
        price: Number(product.value.salesPrice) || 0,
        description: product.value.title,
        insurance_detail: insuranceDetail.value,
        insurance_fee: Number(insuranceFee.value),
      },
      quantity.value
  )
  settlementRef.value.settlementVisibleOpen()
}

</script>
<style scoped>
.mall-container {
  width: 100%;      /* 填满弹窗 content，给 flex 明确可用空间 */
  display: flex;
  margin: 0 auto;
  gap:20px;
}

.mall-container .l {
  max-width:400px;
  min-width: 0;     /* 允许收缩，防止内容撑破弹窗 */
}

.l .product-img {
  max-width: 100%;
  object-fit: cover;
}

/* ══════════════════════════════════════════════════════
   模块 01 — 店铺信息卡片已抽成 StoreCard.vue（三处共用）
   ══════════════════════════════════════════════════════ */

/* ══════════════════════════════════════════════════════
   模块 02 — 右侧面板容器 + Sticky
   ══════════════════════════════════════════════════════ */
.mall-container .r {
  flex: 1;
  min-width: 0; /* 允许收缩，内容换行而不是溢出弹窗 */
  position: sticky; /* 右侧固定不动，左侧滚动 */
  align-self: flex-start; /* flex 子项不拉伸，贴顶对齐 */
  top: 1px; /* 距滚动容器顶部 10px 时固定 */
}

/* ══════════════════════════════════════════════════════
   模块 03 — 左侧商品详情区
   ══════════════════════════════════════════════════════ */
.detail-title { /* 商品详情 分隔标题 */
  display: flex;
  align-items: center;
  margin: 40px 0;
  font-size: 30px;
  font-weight: 600;
  color: #333;
}

.detail-title::before,
.detail-title::after { /* 标题两侧装饰线 */
  content: "";
  flex: 1;
  max-width: 120px;
  height: 2px;
  background: #e5e5e5;
}

.detail-title::before {
  margin-right: 24px;
}

.detail-title::after {
  margin-left: 24px;
}

.detail-content :deep(img) {
  max-width: 100%;
}

/* ══════════════════════════════════════════════════════
   模块 04 — 右侧面板整体卡片容器
   ══════════════════════════════════════════════════════ */
.r-content { /* 白色卡片：18px 圆角 + 浅阴影 + 28px 内边距 */
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, .05);
  padding: 0 18px;
  display: flex;
  flex-direction: column;
  gap: 12px; /* 各子模块统一间距 */
}

/* ══════════════════════════════════════════════════════
   模块 05 — 国家补贴横幅
   ══════════════════════════════════════════════════════ */
.subsidy-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f0faf4;
  border-radius: 12px;
  padding: 14px 18px;
}

.subsidy-left { /* 左侧：图标+文字 */
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: #333;
}

.subsidy-icon { /* 补贴图标 */
  font-size: 20px;
  color: #22BC66;
}

.subsidy-btn { /* 立即领取 绿色胶囊按钮 */
  background: #22BC66;
  color: #fff;
  border: none;
  border-radius: 999px;
  height: 34px;
  padding: 0 18px;
  font-size: 13px;
}

.subsidy-btn:hover {
  background: #1da85a;
  color: #fff;
}

/* ══════════════════════════════════════════════════════
   模块 06 — 商品标题 + 卖点
   ══════════════════════════════════════════════════════ */
.product-title { /* 34px 粗体标题 */
  font-size: 20px;
  font-weight: 700;
  color: #111;
  margin: 0;
}

.product-tagline { /* 灰色卖点描述 */
  font-size: 15px;
  color: #999;
  margin: 0;
}

/* ══════════════════════════════════════════════════════
   模块 07 — 红橙渐变价格卡
   ══════════════════════════════════════════════════════ */
.price-card { /* 红橙渐变背景卡片 */
  background: linear-gradient(135deg, #FF4E2A 0%, #FF7A00 100%);
  border-radius: 16px;
  padding: 8px 24px;
  color: #fff;
  overflow: hidden;
  position: relative;
}
.price-card .tag{
  position: absolute;
  right:18px;
  bottom:22px;
  background: rgba(225,225,225,0.2);
  border-radius: 32px;
  padding: 6px 16px;
  font-size: 16px;
  font-weight: 600;
}
.price-main { /* 补贴后价格行 */
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-bottom: 6px;
}

.price-label { /* "平台加补后" */
  font-size: 14px;
  opacity: .9;
}

.price-symbol { /* ￥符号 */
  font-size: 18px;
  font-weight: 600;
  margin-left: 6px;
}

.price-value { /* 价格数字 */
  font-size: 38px;
  font-weight: 700;
  letter-spacing: -1px;
}


/* ══════════════════════════════════════════════════════
   模块 08 — 商品信息卡（规格/配送/库存）
   ══════════════════════════════════════════════════════ */
.info-card { /* 浅灰背景圆角卡 */
  background: #fafafa;
  border-radius: 12px;
  padding: 0 20px;
}

.info-row { /* 单行：标签 + 值 */
  display: flex;
  align-items: center;
  padding: 8px 0;
}

.info-row + .info-row { /* 行与行之间分割线 */
  border-top: 1px solid #f0f0f0;
}

.info-label { /* 左侧标签（规格/配送/库存） */
  width: 56px;
  font-size: 14px;
  color: #999;
  flex-shrink: 0;
}

.info-value { /* 右侧值 */
  font-size: 14px;
  color: #333;
}

.info-value.stock { /* 库存数字：橙色加粗 */
  color: #FF6700;
  font-weight: 600;
}

/* ══════════════════════════════════════════════════════
   模块 09 — 服务保障图标行
   ══════════════════════════════════════════════════════ */
.assurance-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.assurance-item { /* 图标+文字 */
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #666;
}

.assurance-item :deep(.anticon) { /* 图标：橙色 */
  font-size: 15px;
  color: #FF6700;
}

/* ══════════════════════════════════════════════════════
   模块 10 — 数量选择器 [ − 1 + ]
   ══════════════════════════════════════════════════════ */
.quantity-row {
  display: flex;
  align-items: center;
  gap: 28px;
}

.quantity-label { /* "数量"标签 */
  font-size: 15px;
  color: #333;
  font-weight: 500;
}

.quantity-stepper { /* 步进器外框 */
  display: inline-flex;
  align-items: center;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  overflow: hidden;
}

.step-btn { /* − / + 按钮 */
  width: 42px;
  height: 30px;
  border: none;
  background: #fff;
  font-size: 22px;
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

.step-btn:first-child { /* − 按钮右侧分割线 */
  border-right: 1px solid #e0e0e0;
}

.step-btn:last-child { /* + 按钮左侧分割线 */
  border-left: 1px solid #e0e0e0;
}

.step-value { /* 中间数量数字 */
  width: 56px;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: #111;
}

/* ══════════════════════════════════════════════════════
   模块 11 — 增值保障（三张小卡片）
   ══════════════════════════════════════════════════════ */
.insurance-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.insurance-title { /* "增值保障"标题 */
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.insurance-cards { /* 三张卡片横向排列 */
  display: flex;
  gap: 12px;
}

.insurance-card { /* 单张保障卡片 */
  flex: 1;
  text-align: center;
  padding: 4px 8px;
  border: 1.5px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: all .2s;
  background: #fff;
}

.insurance-card:hover { /* Hover：边框变浅橙 */
  border-color: #FFB380;
}

.insurance-card.active { /* 选中态：橙边框 + 浅橙背景 */
  border-color: #FF6700;
  background: #FFF7F0;
}

.insurance-name { /* 保障名称 */
  font-size: 13px;
  color: #555;
}

.insurance-price { /* 保障价格：橙色加粗 */
  font-size: 16px;
  font-weight: 700;
  color: #FF6700;
}

/* ══════════════════════════════════════════════════════
   模块 12 — 购买按钮（加入购物车 / 立即购买）
   ══════════════════════════════════════════════════════ */
.buy-row { /* 两按钮并排 */
  display: flex;
  gap: 16px;
}

.btn-cart { /* 加入购物车：白底橙色描边胶囊 */
  flex: 1;
  height: 40px;
  align-items: center;
  border-radius: 999px;
  font-size: 17px;
  font-weight: 600;
  background: #fff;
  color: #FF6700;
  border: 1.5px solid #FF6700;
  transition: all .2s;

}

.btn-cart:hover { /* Hover：浅橙背景 */
  background: #FFF7F0;
  color: #FF6700;
  border-color: #FF6700;
}

.btn-buy { /* 立即购买：红橙渐变胶囊 */
  flex: 1;
  height: 40px;
  align-items: center;
  border-radius: 999px;
  font-size: 17px;
  font-weight: 600;
  border: none;
  background: linear-gradient(135deg, #FF4E2A, #FF7A00);
  color: #fff;
  transition: opacity .2s;
}

.btn-buy:hover {
  opacity: .9;
  color: #fff;
}

/* ══════════════════════════════════════════════════════
   模块 13 — 底部服务图标行
   ══════════════════════════════════════════════════════ */
.footer-services { /* 四图标均分 */
  display: flex;
  justify-content: space-around;
  padding-top: 4px;
}

.fs-item { /* 图标+文字 */
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #888;
}

.fs-item :deep(.anticon) { /* 图标：浅灰 */
  font-size: 15px;
  color: #bbb;
}

/* ══════════════════════════════════════════════════════
   模块 14 — 最终合计价格
   ══════════════════════════════════════════════════════ */
.final-price { /* 右对齐合计 */
  text-align: right;
  font-size: 16px;
  color: #333;
}

.final-price span { /* 价格数字：红色加粗 */
  font-size: 24px;
  color: #FF4E2A;
  font-weight: bold;
}

/* ══════════════════════════════════════════════════════
   响应式：笔记本双栏可换行，手机单列
   ══════════════════════════════════════════════════════ */
@media (max-width: 1200px) {
  .mall-container {
    flex-wrap: wrap;
  }
  .mall-container .l {
    flex: 1 1 320px;
    max-width: 100%;
  }
  .mall-container .r {
    position: static;
    flex: 1 1 320px;
  }
}

@media (max-width: 767px) {
  .mall-container {
    display: block;
  }
  .mall-container .l,
  .mall-container .r {
    max-width: 100%;
  }
  .mall-container .r {
    margin-top: 16px;
  }
  .detail-title {
    margin: 20px 0;
    font-size: 22px;
  }
  .detail-title::before,
  .detail-title::after {
    max-width: 40px;
  }
  .detail-title::before {
    margin-right: 12px;
  }
  .detail-title::after {
    margin-left: 12px;
  }
  .product-title {
    font-size: 22px;
  }
  .price-value {
    font-size: 30px;
  }
  .subsidy-banner,
  .assurance-row,
  .insurance-cards,
  .buy-row {
    flex-wrap: wrap;
    gap: 8px;
  }
  .insurance-card {
    flex: 1 1 96px;
  }
  .btn-cart,
  .btn-buy {
    flex: 1 1 140px;
  }
}
</style>
<!-- 购物车弹窗样式，必须非 scoped —— a-modal 渲染在 body 下 -->
<style>
/* 预览弹窗：宽度最大 900px、随窗口收缩的弹性布局（不写死 :width prop） */
.preview-modal .ant-modal-content {
  width: 900px;
  max-width: calc(100vw - 64px); /* 小屏留边距，随窗口收缩 */
}

.cart-modal {
  text-align: center;
}

.cart-modal .cart-text {
  font-size: 25px;
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  margin: 25px 0;
}

.cart-modal .cart-text span {
  display: inline-flex;
  align-items: center;
}

.cart-btn {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
}

.cart-btn .ant-btn {
  width: 120px;
  height: 40px;
}

/* 手机：预览弹窗只留 8px 边距 */
@media (max-width: 767px) {
  .preview-modal .ant-modal-content {
    max-width: calc(100vw - 16px);
  }
}
</style>
