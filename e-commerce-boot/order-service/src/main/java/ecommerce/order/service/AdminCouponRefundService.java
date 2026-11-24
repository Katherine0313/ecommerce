package ecommerce.order.service;

import ecommerce.common.PageVO;
import ecommerce.order.vo.AdminCouponRefundVO;

/**
 * 后台秒杀券退款审核 Service。
 */
public interface AdminCouponRefundService {

    /** 退款单列表；status 为空查全部 */
    PageVO<AdminCouponRefundVO> list(int page, int pageSize, String status);

    /** 审核通过并执行退款（仅退未使用的券，不回补库存） */
    void approve(Long refundId);

    /** 拒绝退款 */
    void reject(Long refundId, String merchantRemark);
}
