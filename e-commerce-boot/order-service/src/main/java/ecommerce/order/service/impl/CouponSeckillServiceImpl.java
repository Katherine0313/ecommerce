package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.order.dto.SeckillRefundApplyDTO;
import ecommerce.order.entity.CouponSeckillOrder;
import ecommerce.order.entity.CouponSeckillRefund;
import ecommerce.order.entity.Coupon;
import ecommerce.order.entity.UserCoupon;
import ecommerce.order.mapper.CouponMapper;
import ecommerce.order.mapper.CouponSeckillOrderMapper;
import ecommerce.order.mapper.CouponSeckillRefundMapper;
import ecommerce.order.service.CouponSeckillService;
import ecommerce.order.support.CouponConstants;
import ecommerce.order.vo.SeckillCouponVO;
import ecommerce.order.vo.SeckillOrderVO;
import ecommerce.utils.UserContext;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * C 端优惠券秒杀实现。
 * <p>
 * 一人一单：synchronized 在事务外层串行化下单，唯一索引兜底；
 * 防超卖：coupon.version 乐观锁，冲突在事务外层重开事务重试。
 */
@Service
public class CouponSeckillServiceImpl implements CouponSeckillService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 待支付订单有效期（分钟） */
    private static final int PAY_TIMEOUT_MINUTES = 15;
    /** 库存版本冲突最大重试次数 */
    private static final int MAX_RETRY = 3;
    /** 单次扫描超时订单上限 */
    private static final int CLOSE_BATCH = 200;

    private final Object createOrderLock = new Object();

    @Resource
    private CouponMapper couponMapper;
    @Resource
    private CouponSeckillOrderMapper seckillOrderMapper;
    @Resource
    private CouponSeckillRefundMapper refundMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public PageVO<SeckillCouponVO> list(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;
        LocalDateTime now = LocalDateTime.now();
        List<SeckillCouponVO> list = couponMapper.listSeckill(offset, safePageSize)
                .stream().map(t -> toCouponVO(t, now)).toList();
        long total = couponMapper.countSeckill();
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    public SeckillCouponVO detail(Long couponId) {
        if (couponId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券 ID 不能为空");
        }
        Coupon coupon = couponMapper.findSeckillById(couponId);
        if (coupon == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "秒杀券不存在");
        }
        return toCouponVO(coupon, LocalDateTime.now());
    }

    @Override
    public SeckillOrderVO createOrder(Long couponId) {
        if (couponId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券 ID 不能为空");
        }
        Long userId = requireUserId();
        synchronized (createOrderLock) {
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    return transactionTemplate.execute(status -> doCreateOrder(couponId, userId));
                } catch (StockConflictException ignored) {
                    // 版本冲突：事务已回滚，重开事务重试
                }
            }
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "手慢了，库存不足");
    }

    @Override
    public SeckillOrderVO getOrder(String orderNo) {
        Long userId = requireUserId();
        return toOrderVO(requireOrder(orderNo, userId));
    }

    @Override
    public PageVO<SeckillOrderVO> myOrders(int page, int pageSize, Integer status) {
        Long userId = requireUserId();
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;
        List<SeckillOrderVO> list = seckillOrderMapper.listByUserId(userId, status, offset, safePageSize)
                .stream().map(this::toOrderVO).toList();
        long total = seckillOrderMapper.countByUserId(userId, status);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    @Transactional
    public void cancel(String orderNo) {
        Long userId = requireUserId();
        CouponSeckillOrder order = requireOrder(orderNo, userId);
        if (order.getStatus() == null || order.getStatus() != CouponConstants.SECKILL_STATUS_PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只有待支付订单可以取消");
        }
        if (seckillOrderMapper.cancelById(order.getId(), LocalDateTime.now()) == 1) {
            couponMapper.increaseSeckillStock(order.getCouponId());
        }
    }

    @Override
    public SeckillOrderVO pay(String orderNo, String payType) {
        Long userId = requireUserId();
        LocalDateTime now = LocalDateTime.now();
        PayResult result = transactionTemplate.execute(status -> doPay(orderNo, userId, payType, now));
        if (result != null && result.errorMessage() != null) {
            // 已关单并回补库存，事务提交后再提示用户
            throw new BusinessException(HttpStatus.BAD_REQUEST, result.errorMessage());
        }
        return result == null ? null : result.order();
    }

    @Override
    @Transactional
    public void applyRefund(String orderNo, SeckillRefundApplyDTO dto) {
        Long userId = requireUserId();
        CouponSeckillOrder order = requireOrder(orderNo, userId);
        if (order.getStatus() == null || order.getStatus() != CouponConstants.SECKILL_STATUS_PAID) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只有已支付订单可以申请退款");
        }
        if (order.getCouponUserId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单还未发放优惠券");
        }
        UserCoupon userCoupon = couponMapper.findUserCouponById(order.getCouponUserId());
        if (userCoupon == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户券不存在");
        }
        if (CouponConstants.USER_LOCKED.equals(userCoupon.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券正被订单占用，请先取消占用该券的订单");
        }
        if (CouponConstants.USER_USED.equals(userCoupon.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券已使用，请走商品订单售后");
        }
        if (!CouponConstants.USER_UNUSED.equals(userCoupon.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前用户券状态不支持退款");
        }
        if (refundMapper.findBySeckillOrderId(order.getId()) != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已提交退款申请");
        }
        String reason = normalize(dto == null ? null : dto.getReason());
        if (reason != null && reason.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "退款原因不能超过 255 字");
        }
        String images = normalize(dto == null ? null : dto.getImages());
        if (images != null && images.length() > 1024) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "凭证图片地址过长");
        }
        CouponSeckillRefund refund = CouponSeckillRefund.builder()
                .refundNo(generateRefundNo())
                .seckillOrderId(order.getId())
                .userId(userId)
                .couponUserId(order.getCouponUserId())
                .refundAmount(order.getPayAmount())
                .status(CouponConstants.REFUND_REVIEWING)
                .reason(reason)
                .images(images)
                .createTime(LocalDateTime.now())
                .build();
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该订单已提交退款申请");
        }
    }

    @Override
    public int closeExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<CouponSeckillOrder> expired = seckillOrderMapper.listExpired(now, CLOSE_BATCH);
        int closed = 0;
        for (CouponSeckillOrder order : expired) {
            Integer done = transactionTemplate.execute(status -> {
                if (seckillOrderMapper.cancelById(order.getId(), LocalDateTime.now()) == 1) {
                    couponMapper.increaseSeckillStock(order.getCouponId());
                    return 1;
                }
                return 0;
            });
            if (done != null && done == 1) {
                closed++;
            }
        }
        return closed;
    }

    // ==================== 内部方法 ====================

    private SeckillOrderVO doCreateOrder(Long couponId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = couponMapper.findSeckillById(couponId);
        if (coupon == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "秒杀券不存在");
        }
        assertActivityOpen(coupon, now);
        if (seckillOrderMapper.countByCouponIdAndUserId(couponId, userId) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "每个用户只能购买一次");
        }
        Coupon stock = couponMapper.findStock(couponId);
        if (stock == null || stock.getRemainQuantity() == null || stock.getRemainQuantity() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "库存不足");
        }
        if (couponMapper.decreaseSeckillStock(couponId, stock.getVersion()) == 0) {
            throw new StockConflictException();
        }
        CouponSeckillOrder order = CouponSeckillOrder.builder()
                .orderNo(generateOrderNo(now))
                .couponId(couponId)
                .userId(userId)
                .couponName(coupon.getName())
                .payAmount(coupon.getSeckillPrice() == null ? BigDecimal.ZERO : coupon.getSeckillPrice())
                .status(CouponConstants.SECKILL_STATUS_PENDING)
                .createTime(now)
                .expireTime(now.plusMinutes(PAY_TIMEOUT_MINUTES))
                .build();
        try {
            seckillOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "每个用户只能购买一次");
        }
        return toOrderVO(order);
    }

    private PayResult doPay(String orderNo, Long userId, String payType, LocalDateTime now) {
        CouponSeckillOrder order = seckillOrderMapper.findByOrderNoAndUserId(orderNo, userId);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == CouponConstants.SECKILL_STATUS_PAID) {
            return new PayResult(toOrderVO(order), null);
        }
        if (order.getStatus() == null || order.getStatus() != CouponConstants.SECKILL_STATUS_PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单状态不允许支付");
        }
        Coupon coupon = couponMapper.findSeckillById(order.getCouponId());
        if (coupon == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "秒杀券不存在");
        }
        if (coupon.getSeckillEndTime() == null || !now.isBefore(coupon.getSeckillEndTime())) {
            if (seckillOrderMapper.cancelById(order.getId(), now) == 1) {
                couponMapper.increaseSeckillStock(order.getCouponId());
                return new PayResult(null, "秒杀活动已结束，订单已关闭");
            }
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单状态已变化，请刷新后重试");
        }
        if (coupon.getValidEnd() == null || !now.isBefore(coupon.getValidEnd())) {
            if (seckillOrderMapper.cancelById(order.getId(), now) == 1) {
                couponMapper.increaseSeckillStock(order.getCouponId());
                return new PayResult(null, "优惠券已过期，订单已关闭");
            }
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单状态已变化，请刷新后重试");
        }
        String safePayType = normalize(payType) == null ? "MOCK" : payType.trim().toUpperCase();
        UserCoupon userCoupon = buildUserCoupon(coupon, userId, now);
        couponMapper.insertUserCoupon(userCoupon);
        if (seckillOrderMapper.payById(order.getId(), now, safePayType, userCoupon.getId(), now) == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单状态已变化，请刷新后重试");
        }
        order.setStatus(CouponConstants.SECKILL_STATUS_PAID);
        order.setPayTime(now);
        order.setPayType(safePayType);
        order.setCouponUserId(userCoupon.getId());
        return new PayResult(toOrderVO(order), null);
    }

    private void assertActivityOpen(Coupon coupon, LocalDateTime now) {
        if (coupon.getManualStatus() != null && coupon.getManualStatus() == 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀活动已停止");
        }
        if (coupon.getSeckillBeginTime() == null || coupon.getSeckillEndTime() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀时间未配置");
        }
        if (now.isBefore(coupon.getSeckillBeginTime())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀尚未开始");
        }
        if (!now.isBefore(coupon.getSeckillEndTime())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀已结束");
        }
        if (coupon.getValidEnd() == null || !now.isBefore(coupon.getValidEnd())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券已过期");
        }
    }

    private CouponSeckillOrder requireOrder(String orderNo, Long userId) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "订单号不能为空");
        }
        CouponSeckillOrder order = seckillOrderMapper.findByOrderNoAndUserId(orderNo.trim(), userId);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    private UserCoupon buildUserCoupon(Coupon template, Long userId, LocalDateTime now) {
        LocalDateTime start;
        LocalDateTime end;
        if (CouponConstants.VALID_RECEIVE_DAYS.equals(template.getValidType())) {
            start = now;
            end = now.plusDays(template.getValidDays() == null ? 0 : template.getValidDays());
        } else {
            start = template.getValidStart();
            end = template.getValidEnd();
        }
        return UserCoupon.builder()
                .code("CP" + now.format(CODE_FMT)
                        + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)))
                .templateId(template.getId())
                .userId(userId)
                .status(CouponConstants.USER_UNUSED)
                .receiveTime(now)
                .startTime(start)
                .endTime(end)
                .source(CouponConstants.SOURCE_SECKILL)
                .build();
    }

    private SeckillCouponVO toCouponVO(Coupon t, LocalDateTime now) {
        String activityStatus = activityStatus(t, now);
        return SeckillCouponVO.builder()
                .couponId(t.getId())
                .name(t.getName())
                .type(t.getType())
                .typeDesc(CouponConstants.TYPE_DISCOUNT.equals(t.getType()) ? "折扣券"
                        : (CouponConstants.ISSUE_SECKILL.equals(t.getIssueType()) ? "代金券" : "满减券"))
                .faceValue(t.getFaceValue())
                .discountRate(t.getDiscountRate())
                .maxDiscount(t.getMaxDiscount())
                .thresholdAmount(t.getThresholdAmount())
                .seckillPrice(t.getSeckillPrice())
                .seckillBeginTime(t.getSeckillBeginTime())
                .seckillEndTime(t.getSeckillEndTime())
                .totalQuantity(t.getTotalQuantity())
                .remainQuantity(t.getRemainQuantity())
                .activityStatus(activityStatus)
                .activityStatusDesc(activityStatusDesc(activityStatus))
                .description(t.getDescription())
                .build();
    }

    private SeckillOrderVO toOrderVO(CouponSeckillOrder order) {
        return SeckillOrderVO.builder()
                .orderNo(order.getOrderNo())
                .couponId(order.getCouponId())
                .couponName(order.getCouponName())
                .payAmount(order.getPayAmount())
                .status(order.getStatus())
                .statusDesc(orderStatusDesc(order.getStatus()))
                .createTime(order.getCreateTime())
                .expireTime(order.getExpireTime())
                .payTime(order.getPayTime())
                .payType(order.getPayType())
                .build();
    }

    private String activityStatus(Coupon coupon, LocalDateTime now) {
        if (coupon.getManualStatus() != null && coupon.getManualStatus() == 1) {
            return CouponConstants.ACTIVITY_STOPPED;
        }
        if (coupon.getSeckillBeginTime() == null || coupon.getSeckillEndTime() == null) {
            return CouponConstants.ACTIVITY_ENDED;
        }
        if (now.isBefore(coupon.getSeckillBeginTime())) {
            return CouponConstants.ACTIVITY_NOT_STARTED;
        }
        if (now.isBefore(coupon.getSeckillEndTime())) {
            return CouponConstants.ACTIVITY_ACTIVE;
        }
        return CouponConstants.ACTIVITY_ENDED;
    }

    private String activityStatusDesc(String status) {
        return switch (status) {
            case CouponConstants.ACTIVITY_NOT_STARTED -> "未开始";
            case CouponConstants.ACTIVITY_ACTIVE -> "抢购中";
            case CouponConstants.ACTIVITY_STOPPED -> "已停止";
            default -> "已结束";
        };
    }

    private String orderStatusDesc(Integer status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case CouponConstants.SECKILL_STATUS_PENDING -> "待支付";
            case CouponConstants.SECKILL_STATUS_PAID -> "已支付";
            case CouponConstants.SECKILL_STATUS_CANCELED -> "已取消";
            case CouponConstants.SECKILL_STATUS_REFUNDED -> "已退款";
            default -> "-";
        };
    }

    private String generateOrderNo(LocalDateTime now) {
        return "CS" + now.format(NO_FMT) + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String generateRefundNo() {
        return "RF" + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 库存版本冲突，触发外层新事务重试 */
    private static class StockConflictException extends RuntimeException {
    }

    private record PayResult(SeckillOrderVO order, String errorMessage) {
    }
}
