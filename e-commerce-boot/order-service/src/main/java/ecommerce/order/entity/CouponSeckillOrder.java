package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀券购买订单（coupon_seckill_order 表）。
 * <p>
 * 状态：1 待支付 / 2 已支付 / 3 已取消 / 4 已退款。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponSeckillOrder {
    private Long id;
    private String orderNo;
    private Long couponId;
    private Long userId;
    private Long couponUserId;
    private String couponName;
    private BigDecimal payAmount;
    private String payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;
}
