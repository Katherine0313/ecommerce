package ecommerce.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀券退款单（coupon_seckill_refund 表）。
 * <p>
 * 状态：REVIEWING 待审核 / APPROVING 审核通过处理中 / REFUNDED 已退款 / REJECTED 已拒绝。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponSeckillRefund {
    private Long id;
    private String refundNo;
    private Long seckillOrderId;
    private Long userId;
    private Long couponUserId;
    private BigDecimal refundAmount;
    private String status;
    private String reason;
    private String images;
    private String merchantRemark;
    private LocalDateTime createTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;
}
