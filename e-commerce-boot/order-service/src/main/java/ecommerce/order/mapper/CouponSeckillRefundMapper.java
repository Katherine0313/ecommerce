package ecommerce.order.mapper;

import ecommerce.order.entity.CouponSeckillRefund;
import ecommerce.order.vo.AdminCouponRefundVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀券退款单 Mapper（coupon_seckill_refund）。
 */
public interface CouponSeckillRefundMapper {

    int insert(CouponSeckillRefund refund);

    CouponSeckillRefund findById(@Param("id") Long id);

    CouponSeckillRefund findBySeckillOrderId(@Param("seckillOrderId") Long seckillOrderId);

    // 待审核 -> 审核通过处理中
    int approveById(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 处理中 -> 已退款
    int finishRefund(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 待审核 -> 已拒绝
    int rejectById(@Param("id") Long id, @Param("merchantRemark") String merchantRemark,
                   @Param("now") LocalDateTime now);

    List<AdminCouponRefundVO> listAdmin(@Param("status") String status,
                                        @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countAdmin(@Param("status") String status);
}
