import request from '@/utils/request'

// 我的优惠券（status：AVAILABLE 可用 / USED 已使用 / EXPIRED 已过期）
export const getMyCoupons = (status) => request.get('/coupons/mine', { params: { status } })

// 结算页可用券：后端按当前商品算好每张券能减多少（body：{ items }）
export const getAvailableCoupons = (data) => request.post('/coupons/available', data)

// 领券中心（可领取的券）
export const getCouponCenter = () => request.get('/coupons/center')

// 领取优惠券
export const claimCoupon = (templateId) => request.post(`/coupons/${templateId}/claim`)

// 商品详情页“国家补贴”横幅；没有可领模板时返回 null，未登录 claimed 恒为 false
export const getProductSubsidy = (productId) =>
  request.get(`/coupons/product/${productId}/subsidy`)

// ===== 优惠券秒杀（/coupons/seckill） =====
// 列表 / 详情免登录；下单、支付、取消、退款、查订单需登录（路由 meta.requiresAuth 控制）

// 秒杀券列表（分页），返回 { list, total, page, pageSize }
export const getSeckillList = (params) => request.get('/coupons/seckill', { params })

// 秒杀券详情
export const getSeckillDetail = (couponId) => request.get(`/coupons/seckill/${couponId}`)

// 创建秒杀订单（扣库存、生成待支付订单），body 为空
export const createSeckillOrder = (couponId) => request.post(`/coupons/seckill/${couponId}/orders`)

// 我的秒杀订单列表（分页）；status：1 待支付 / 2 已支付 / 3 已取消 / 4 已退款
export const getMySeckillOrders = (params) => request.get('/coupons/seckill/orders', { params })

// 查询自己的秒杀订单
export const getSeckillOrder = (orderNo) => request.get(`/coupons/seckill/orders/${orderNo}`)

// 取消待支付订单（回补库存）
export const cancelSeckillOrder = (orderNo) => request.post(`/coupons/seckill/orders/${orderNo}/cancel`)

// 支付订单（v1 模拟支付），body { payType }
export const paySeckillOrder = (orderNo, payType = 'MOCK') =>
  request.post(`/coupons/seckill/orders/${orderNo}/pay`, { payType })

// 申请退款，body { reason, images }；凭证图可选，images 为逗号分隔的 URL 字符串
export const applySeckillRefund = (orderNo, data) =>
  request.post(`/coupons/seckill/orders/${orderNo}/refund`, data || {})

