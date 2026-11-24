package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.dto.SeckillRefundApplyDTO;
import ecommerce.order.vo.SeckillCouponVO;
import ecommerce.order.vo.SeckillOrderVO;

/**
 * C 端优惠券秒杀 Service。
 */
public interface CouponSeckillService {

    /** 秒杀券列表（分页） */
    PageVO<SeckillCouponVO> list(int page, int pageSize);

    /** 秒杀券详情 */
    SeckillCouponVO detail(Long couponId);

    /** 创建待支付订单（预占库存） */
    SeckillOrderVO createOrder(Long couponId);

    /** 查询自己的订单 */
    SeckillOrderVO getOrder(String orderNo);

    /** 我的秒杀订单列表（分页，status 为 null 时返回全部） */
    PageVO<SeckillOrderVO> myOrders(int page, int pageSize, Integer status);

    /** 取消待支付订单并回补库存 */
    void cancel(String orderNo);

    /** 模拟支付：发券并置为已支付 */
    SeckillOrderVO pay(String orderNo, String payType);

    /** 申请退款（仅未使用的券） */
    void applyRefund(String orderNo, SeckillRefundApplyDTO dto);

    /** 关闭超时未支付订单，返回处理条数 */
    int closeExpired();
}
