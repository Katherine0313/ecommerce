package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.order.entity.CouponSeckillRefund;
import ecommerce.order.mapper.CouponMapper;
import ecommerce.order.mapper.CouponSeckillOrderMapper;
import ecommerce.order.mapper.CouponSeckillRefundMapper;
import ecommerce.order.service.AdminCouponRefundService;
import ecommerce.order.support.CouponConstants;
import ecommerce.order.vo.AdminCouponRefundVO;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 后台秒杀券退款审核实现。
 * <p>
 * 退款单、秒杀订单、用户券三处状态在同一事务内变更；退款不回补库存。
 */
@Service
public class AdminCouponRefundServiceImpl implements AdminCouponRefundService {

    @Resource
    private CouponSeckillRefundMapper refundMapper;
    @Resource
    private CouponSeckillOrderMapper seckillOrderMapper;
    @Resource
    private CouponMapper couponMapper;

    @Override
    public PageVO<AdminCouponRefundVO> list(int page, int pageSize, String status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String safeStatus = normalizeStatus(status);
        int offset = (safePage - 1) * safePageSize;
        List<AdminCouponRefundVO> list = refundMapper.listAdmin(safeStatus, offset, safePageSize);
        list.forEach(vo -> vo.setStatusDesc(statusDesc(vo.getStatus())));
        long total = refundMapper.countAdmin(safeStatus);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    @Transactional
    public void approve(Long refundId) {
        CouponSeckillRefund refund = requireRefund(refundId);
        if (!CouponConstants.REFUND_REVIEWING.equals(refund.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该退款单已处理，不能重复审核");
        }
        if (refund.getCouponUserId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "退款单未关联用户券，无法退款");
        }
        LocalDateTime now = LocalDateTime.now();
        if (refundMapper.approveById(refundId, now) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "退款单状态已变化，请刷新后重试");
        }
        if (seckillOrderMapper.markRefunded(refund.getSeckillOrderId(), now) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀订单状态已变化，无法退款");
        }
        if (couponMapper.refundUserCoupon(refund.getCouponUserId()) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券已被使用或状态已变化，无法退款");
        }
        if (refundMapper.finishRefund(refundId, now) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "退款单状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void reject(Long refundId, String merchantRemark) {
        CouponSeckillRefund refund = requireRefund(refundId);
        if (!CouponConstants.REFUND_REVIEWING.equals(refund.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该退款单已处理，不能重复审核");
        }
        String remark = normalize(merchantRemark);
        if (remark != null && remark.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "备注不能超过 255 字");
        }
        if (refundMapper.rejectById(refundId, remark, LocalDateTime.now()) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "退款单状态已变化，请刷新后重试");
        }
    }

    private CouponSeckillRefund requireRefund(Long refundId) {
        if (refundId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "退款单 ID 不能为空");
        }
        CouponSeckillRefund refund = refundMapper.findById(refundId);
        if (refund == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "退款单不存在");
        }
        return refund;
    }

    private String normalizeStatus(String status) {
        String value = normalize(status);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase();
        if (!Set.of(CouponConstants.REFUND_REVIEWING, CouponConstants.REFUND_APPROVING,
                CouponConstants.REFUND_REFUNDED, CouponConstants.REFUND_REJECTED).contains(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "退款单状态不合法");
        }
        return value;
    }

    private String statusDesc(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case CouponConstants.REFUND_REVIEWING -> "待审核";
            case CouponConstants.REFUND_APPROVING -> "处理中";
            case CouponConstants.REFUND_REFUNDED -> "已退款";
            case CouponConstants.REFUND_REJECTED -> "已拒绝";
            default -> "-";
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
