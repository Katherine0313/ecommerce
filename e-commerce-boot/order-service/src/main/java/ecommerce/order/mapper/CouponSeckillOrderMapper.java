package ecommerce.order.mapper;

import ecommerce.order.entity.CouponSeckillOrder;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 秒杀券购买订单 Mapper（coupon_seckill_order）。
 * <p>
 * 时间统一由 Java 传入，不使用 MySQL NOW()（服务器时区 UTC）。
 */
public interface CouponSeckillOrderMapper {

    int insert(CouponSeckillOrder order);

    CouponSeckillOrder findByOrderNoAndUserId(@Param("orderNo") String orderNo,
                                              @Param("userId") Long userId);

    CouponSeckillOrder findById(@Param("id") Long id);

    // 我的秒杀订单：按用户 + 可选状态分页查询
    List<CouponSeckillOrder> listByUserId(@Param("userId") Long userId,
                                          @Param("status") Integer status,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    // 我的秒杀订单总数（status 为 null 时统计全部）
    long countByUserId(@Param("userId") Long userId, @Param("status") Integer status);

    long countByCouponIdAndUserId(@Param("couponId") Long couponId, @Param("userId") Long userId);

    // 支付成功：待支付 -> 已支付
    int payById(@Param("id") Long id, @Param("payTime") LocalDateTime payTime,
                @Param("payType") String payType, @Param("couponUserId") Long couponUserId,
                @Param("now") LocalDateTime now);

    // 取消：待支付 -> 已取消
    int cancelById(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 退款：已支付 -> 已退款
    int markRefunded(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 超时未支付订单（status=1 且 expire_time <= now）
    List<CouponSeckillOrder> listExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);

    // ==================== 财务看板 ====================

    BigDecimal sumFinancePaid(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                              @Param("channel") String channel);

    BigDecimal sumFinanceRefunded(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                  @Param("channel") String channel);

    List<Map<String, Object>> sumFinancePaidByDay(@Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end,
                                                  @Param("channel") String channel);

    List<Map<String, Object>> sumFinanceRefundedByDay(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      @Param("channel") String channel);

    List<Map<String, Object>> sumFinancePaidByChannel(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      @Param("channel") String channel);
}
