package ecommerce.order.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.LogDiff;
import ecommerce.log.OperationLog;
import ecommerce.order.dto.AdminCouponDTO;
import ecommerce.order.dto.AdminCouponGrantDTO;
import ecommerce.order.entity.CouponScope;
import ecommerce.order.entity.Coupon;
import ecommerce.order.entity.UserCoupon;
import ecommerce.order.mapper.CouponMapper;
import ecommerce.order.service.AdminCouponService;
import ecommerce.order.support.CouponConstants;
import ecommerce.order.vo.AdminCouponVO;
import ecommerce.order.vo.CouponGrantResultVO;
import ecommerce.order.vo.CouponRecordVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 后台优惠券管理实现。
 */
@Service
public class AdminCouponServiceImpl implements AdminCouponService {

    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final CouponMapper couponMapper;

    public AdminCouponServiceImpl(CouponMapper couponMapper) {
        this.couponMapper = couponMapper;
    }

    @Override
    public PageVO<AdminCouponVO> list(int page, int pageSize, String keyword, String status, String issueType) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String safeKeyword = normalize(keyword);
        String safeStatus = normalizeStatus(status);
        String safeIssueType = normalizeIssueType(issueType);
        LocalDateTime now = LocalDateTime.now();
        int offset = (safePage - 1) * safePageSize;
        List<AdminCouponVO> list = couponMapper
                .listAdmin(safeKeyword, safeStatus, safeIssueType, now, offset, safePageSize)
                .stream().map(t -> toVO(t, now, false)).toList();
        long total = couponMapper.countAdmin(safeKeyword, safeStatus, safeIssueType, now);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    @Override
    public AdminCouponVO get(Long id) {
        Coupon template = requireTemplate(id);
        return toVO(template, LocalDateTime.now(), true);
    }

    @Override
    @Transactional
    @OperationLog(module = "promotion", action = "COUPON_ADD")
    public Long create(AdminCouponDTO dto) {
        validate(dto);
        Coupon template = buildTemplate(null, dto, null, LocalDateTime.now());
        couponMapper.insertTemplate(template);
        saveScopes(template.getId(), dto);
        // 新建没有「旧值」，按纯文本记关键信息（券名 / 面额 / 发放数量）
        LogDetail.add("券名：" + template.getName());
        LogDetail.add("面额：" + faceValueText(template));
        LogDetail.add("发放数量：" + template.getTotalQuantity());
        return template.getId();
    }

    @Override
    @Transactional
    @OperationLog(module = "promotion", action = "COUPON_EDIT", bizId = "#id")
    public void update(Long id, AdminCouponDTO dto) {
        Coupon existing = requireTemplate(id);
        assertStatus(existing, CouponConstants.TEMPLATE_PENDING, "只有未开始发放的券可以编辑");
        // 发放方式不能通过编辑切换；库存创建后固定
        dto.setIssueType(existing.getIssueType());
        validate(dto);
        Coupon template = buildTemplate(id, dto, existing, LocalDateTime.now());
        couponMapper.updateTemplate(template);
        couponMapper.deleteScopes(id);
        saveScopes(id, dto);
        // 变更文案：existing 是改之前的券，逐字段比只记真正变了的
        recordCouponDiff(existing, template);
    }

    @Override
    @OperationLog(module = "promotion", action = "COUPON_STOP", bizId = "#id")
    public void stop(Long id) {
        Coupon template = requireTemplate(id);
        String status = templateStatus(template, LocalDateTime.now());
        if (CouponConstants.TEMPLATE_ENDED.equals(status)
                || CouponConstants.TEMPLATE_STOPPED.equals(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该券已结束或已停止");
        }
        couponMapper.stopNow(id, LocalDateTime.now());
        // 变更文案：券状态从旧状态改成「已停止」
        LogDetail.add(LogDiff.of("优惠券状态", status, CouponConstants.TEMPLATE_STOPPED,
                value -> statusDesc(String.valueOf(value))));
    }

    @Override
    @OperationLog(module = "promotion", action = "COUPON_REMOVE", bizId = "#id")
    public void delete(Long id) {
        Coupon template = requireTemplate(id);
        if (CouponConstants.ISSUE_SECKILL.equals(template.getIssueType())
                && couponMapper.countPaidSeckillOrders(id) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "已有人购买，只能停止");
        }
        assertStatus(template, CouponConstants.TEMPLATE_PENDING, "只有未开始发放的券可以删除");
        if (couponMapper.countIssued(id) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "已有用户领取，不能删除，请改为停止发放");
        }
        couponMapper.softDelete(id, LocalDateTime.now());
        // 软删不是物理删除，日志里点明一下，避免审计时误以为券不见了
        LogDetail.add("删除优惠券（软删）：" + template.getName());
    }

    @Override
    @Transactional
    @OperationLog(module = "promotion", action = "COUPON_GRANT", bizId = "#id")
    public CouponGrantResultVO grant(Long id, AdminCouponGrantDTO dto) {
        Coupon template = requireTemplate(id);
        LocalDateTime now = LocalDateTime.now();
        if (CouponConstants.ISSUE_SECKILL.equals(template.getIssueType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀券不支持后台发放");
        }
        if (template.getManualStatus() != null && template.getManualStatus() == 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该券已停止发放");
        }
        if (now.isBefore(template.getReceiveStart()) || !now.isBefore(template.getReceiveEnd())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不在领取时间内，不能发放");
        }

        List<Long> userIds = resolveUserIds(dto);
        // 定向发券先校验用户是否存在：把券发给不存在的 id，券就永远没人看得到，运营还以为是发成功了
        if (!Boolean.TRUE.equals(dto.getAllUsers())) {
            Set<Long> exists = new HashSet<>(couponMapper.listExistingUserIds(userIds));
            List<String> missing = userIds.stream()
                    .filter(userId -> !exists.contains(userId))
                    .map(String::valueOf)
                    .toList();
            if (!missing.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "用户 ID 不存在：" + String.join("、", missing));
            }
        }
        int granted = 0;
        int skipped = 0;
        for (Long userId : userIds) {
            if (couponMapper.findUserCouponByUserAndTemplate(userId, id) != null) {
                skipped++;
                continue;
            }
            try {
                couponMapper.insertUserCoupon(buildUserCoupon(template, userId, now));
                granted++;
            } catch (DuplicateKeyException e) {
                // 并发下已被领取：跳过
                skipped++;
            }
        }
        Coupon latest = couponMapper.findTemplateById(id);
        CouponGrantResultVO result = CouponGrantResultVO.builder()
                .granted(granted)
                .skipped(skipped)
                .remainQuantity(latest == null ? 0 : latest.getRemainQuantity())
                .build();
        // 变更文案：记实际发放数量（跳过的没发出去，不算变更）
        LogDetail.add("发放优惠券 " + result.getGranted() + " 张（跳过 " + result.getSkipped() + " 张）");
        return result;
    }

    @Override
    public PageVO<CouponRecordVO> records(Long id, int page, int pageSize) {
        requireTemplate(id);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;
        List<CouponRecordVO> list = couponMapper.listRecords(id, offset, safePageSize);
        list.forEach(r -> r.setStatusDesc(recordStatusDesc(r.getStatus())));
        long total = couponMapper.countRecords(id);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    // ==================== 内部方法 ====================

    private Coupon requireTemplate(Long id) {
        if (id == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券 ID 不能为空");
        }
        Coupon template = couponMapper.findTemplateById(id);
        if (template == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "优惠券不存在");
        }
        return template;
    }

    private void assertStatus(Coupon template, String expected, String message) {
        if (!expected.equals(templateStatus(template, LocalDateTime.now()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String templateStatus(Coupon template, LocalDateTime now) {
        if (template.getManualStatus() != null && template.getManualStatus() == 1) {
            return CouponConstants.TEMPLATE_STOPPED;
        }
        if (CouponConstants.ISSUE_SECKILL.equals(template.getIssueType())) {
            if (template.getSeckillBeginTime() == null || template.getSeckillEndTime() == null) {
                return CouponConstants.TEMPLATE_PENDING;
            }
            if (now.isBefore(template.getSeckillBeginTime())) {
                return CouponConstants.TEMPLATE_PENDING;
            }
            if (now.isBefore(template.getSeckillEndTime())) {
                return CouponConstants.TEMPLATE_ACTIVE;
            }
            return CouponConstants.TEMPLATE_ENDED;
        }
        if (now.isBefore(template.getReceiveStart())) {
            return CouponConstants.TEMPLATE_PENDING;
        }
        if (!now.isBefore(template.getReceiveEnd())) {
            return CouponConstants.TEMPLATE_ENDED;
        }
        return CouponConstants.TEMPLATE_ACTIVE;
    }

    private Coupon buildTemplate(Long id, AdminCouponDTO dto, Coupon existing, LocalDateTime now) {
        boolean seckill = CouponConstants.ISSUE_SECKILL.equals(dto.getIssueType());
        Integer totalQuantity;
        Integer remainQuantity;
        Integer version;
        if (existing != null) {
            // 编辑：库存与发放方式以数据库为准
            totalQuantity = existing.getTotalQuantity();
            remainQuantity = existing.getRemainQuantity();
            version = existing.getVersion();
        } else {
            totalQuantity = dto.getTotalQuantity() == null ? 0 : Math.max(0, dto.getTotalQuantity());
            remainQuantity = totalQuantity;
            version = 0;
        }
        LocalDateTime receiveStart = dto.getReceiveStart();
        LocalDateTime receiveEnd = dto.getReceiveEnd();
        if (seckill) {
            // 秒杀券不要求领取时间，落库用秒杀时间兜底（receive_start/end 为 NOT NULL）
            receiveStart = receiveStart == null ? dto.getSeckillBeginTime() : receiveStart;
            receiveEnd = receiveEnd == null ? dto.getSeckillEndTime() : receiveEnd;
        }
        return Coupon.builder()
                .id(id)
                .name(dto.getName())
                .type(dto.getType())
                .issueType(dto.getIssueType())
                .seckillPrice(seckill ? dto.getSeckillPrice() : null)
                .seckillBeginTime(seckill ? dto.getSeckillBeginTime() : null)
                .seckillEndTime(seckill ? dto.getSeckillEndTime() : null)
                .version(version)
                .faceValue(dto.getFaceValue())
                .discountRate(dto.getDiscountRate())
                .maxDiscount(dto.getMaxDiscount())
                .thresholdAmount(dto.getThresholdAmount())
                .scopeType(dto.getScopeType())
                .targetGroup(dto.getTargetGroup())
                .minMemberLevel(dto.getMinMemberLevel() == null ? 0L : dto.getMinMemberLevel())
                .totalQuantity(totalQuantity)
                .remainQuantity(remainQuantity)
                .perUserLimit(1)
                .receiveStart(receiveStart)
                .receiveEnd(receiveEnd)
                .validType(dto.getValidType())
                .validStart(dto.getValidStart())
                .validEnd(dto.getValidEnd())
                .validDays(dto.getValidDays())
                .manualStatus(0)
                .isDeleted(0)
                .description(dto.getDescription())
                .createTime(now)
                .updateTime(id == null ? null : now)
                .build();
    }

    private void saveScopes(Long templateId, AdminCouponDTO dto) {
        List<CouponScope> scopes = new ArrayList<>();
        if (CouponConstants.SCOPE_CATEGORY.equals(dto.getScopeType())) {
            for (Long categoryId : new LinkedHashSet<>(dto.getCategoryIds())) {
                scopes.add(CouponScope.builder()
                        .couponId(templateId)
                        .targetType(CouponConstants.SCOPE_CATEGORY)
                        .targetId(categoryId)
                        .build());
            }
        } else if (CouponConstants.SCOPE_PRODUCT.equals(dto.getScopeType())) {
            for (Long productId : new LinkedHashSet<>(dto.getProductIds())) {
                scopes.add(CouponScope.builder()
                        .couponId(templateId)
                        .targetType(CouponConstants.SCOPE_PRODUCT)
                        .targetId(productId)
                        .build());
            }
        }
        if (!scopes.isEmpty()) {
            couponMapper.insertScopes(scopes);
        }
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
                .code("CP" + now.format(CODE_FMT) + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)))
                .templateId(template.getId())
                .userId(userId)
                .status(CouponConstants.USER_UNUSED)
                .receiveTime(now)
                .startTime(start)
                .endTime(end)
                .source(CouponConstants.SOURCE_ADMIN)
                .build();
    }

    private List<Long> resolveUserIds(AdminCouponGrantDTO dto) {
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "发放参数不能为空");
        }
        if (Boolean.TRUE.equals(dto.getAllUsers())) {
            return couponMapper.listActiveUserIds();
        }
        if (dto.getUserIds() == null || dto.getUserIds().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择要发放的用户");
        }
        return new ArrayList<>(new LinkedHashSet<>(dto.getUserIds()));
    }

    private AdminCouponVO toVO(Coupon t, LocalDateTime now, boolean withScopes) {
        String status = templateStatus(t, now);
        List<Long> categoryIds = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        if (withScopes) {
            for (CouponScope scope : couponMapper.listScopes(t.getId())) {
                if (CouponConstants.SCOPE_CATEGORY.equals(scope.getTargetType())) {
                    categoryIds.add(scope.getTargetId());
                } else if (CouponConstants.SCOPE_PRODUCT.equals(scope.getTargetType())) {
                    productIds.add(scope.getTargetId());
                }
            }
        }
        return AdminCouponVO.builder()
                .id(t.getId())
                .name(t.getName())
                .type(t.getType())
                .typeDesc(couponTypeDesc(t.getType(), t.getIssueType()))
                .issueType(t.getIssueType())
                .issueTypeDesc(issueTypeDesc(t.getIssueType()))
                .seckillPrice(t.getSeckillPrice())
                .seckillBeginTime(t.getSeckillBeginTime())
                .seckillEndTime(t.getSeckillEndTime())
                .faceValue(t.getFaceValue())
                .discountRate(t.getDiscountRate())
                .maxDiscount(t.getMaxDiscount())
                .thresholdAmount(t.getThresholdAmount())
                .scopeType(t.getScopeType())
                .scopeTypeDesc(scopeDesc(t.getScopeType()))
                .targetGroup(t.getTargetGroup())
                .targetGroupDesc(targetGroupDesc(t.getTargetGroup()))
                .minMemberLevel(t.getMinMemberLevel())
                .receiveStart(t.getReceiveStart())
                .receiveEnd(t.getReceiveEnd())
                .validType(t.getValidType())
                .validTypeDesc(validTypeDesc(t.getValidType()))
                .validStart(t.getValidStart())
                .validEnd(t.getValidEnd())
                .validDays(t.getValidDays())
                .totalQuantity(t.getTotalQuantity())
                .remainQuantity(t.getRemainQuantity())
                .perUserLimit(t.getPerUserLimit())
                .description(t.getDescription())
                .status(status)
                .statusDesc(statusDesc(status))
                .issuedCount(couponMapper.countIssued(t.getId()))
                .usedCount(couponMapper.countUsed(t.getId()))
                .usedDiscount(couponMapper.sumUsedDiscount(t.getId()))
                .categoryIds(withScopes ? categoryIds : null)
                .productIds(withScopes ? productIds : null)
                .createTime(t.getCreateTime())
                .build();
    }

    private void validate(AdminCouponDTO dto) {
        if (dto == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券参数不能为空");
        }
        String name = normalize(dto.getName());
        if (name == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券名称不能为空");
        }
        if (name.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券名称不能超过 128 字");
        }
        dto.setName(name);

        String type = upper(dto.getType());
        if (!CouponConstants.TYPE_FIXED.equals(type) && !CouponConstants.TYPE_DISCOUNT.equals(type)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券类型不合法");
        }
        dto.setType(type);

        BigDecimal threshold = dto.getThresholdAmount() == null ? BigDecimal.ZERO : dto.getThresholdAmount();
        if (threshold.signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "使用门槛不能为负数");
        }
        dto.setThresholdAmount(threshold);

        if (CouponConstants.TYPE_FIXED.equals(type)) {
            if (dto.getFaceValue() == null || dto.getFaceValue().signum() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "满减金额必须大于 0");
            }
            dto.setDiscountRate(null);
            dto.setMaxDiscount(null);
        } else {
            if (dto.getDiscountRate() == null || dto.getDiscountRate().signum() <= 0
                    || dto.getDiscountRate().compareTo(BigDecimal.ONE) >= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "折扣率必须在 0 到 1 之间（如 0.85）");
            }
            if (dto.getMaxDiscount() != null && dto.getMaxDiscount().signum() < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "最高优惠不能为负数");
            }
            dto.setFaceValue(null);
        }

        String scopeType = dto.getScopeType() == null ? CouponConstants.SCOPE_ALL : upper(dto.getScopeType());
        if (!Set.of(CouponConstants.SCOPE_ALL, CouponConstants.SCOPE_CATEGORY,
                CouponConstants.SCOPE_PRODUCT).contains(scopeType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "适用范围不合法");
        }
        dto.setScopeType(scopeType);
        if (CouponConstants.SCOPE_CATEGORY.equals(scopeType)
                && (dto.getCategoryIds() == null || dto.getCategoryIds().isEmpty())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择适用分类");
        }
        if (CouponConstants.SCOPE_PRODUCT.equals(scopeType)
                && (dto.getProductIds() == null || dto.getProductIds().isEmpty())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择适用商品");
        }

        String target = dto.getTargetGroup() == null ? CouponConstants.TARGET_ALL : upper(dto.getTargetGroup());
        if (!CouponConstants.TARGET_ALL.equals(target) && !CouponConstants.TARGET_NEW_USER.equals(target)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "目标人群不合法");
        }
        dto.setTargetGroup(target);
        if (dto.getMinMemberLevel() != null && dto.getMinMemberLevel() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "会员等级门槛不合法");
        }
        if (dto.getMinMemberLevel() == null) {
            dto.setMinMemberLevel(0L);
        }

        String issueType = dto.getIssueType() == null
                ? CouponConstants.ISSUE_RECEIVE : upper(dto.getIssueType());
        if (!CouponConstants.ISSUE_RECEIVE.equals(issueType)
                && !CouponConstants.ISSUE_SECKILL.equals(issueType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "发放方式不合法");
        }
        dto.setIssueType(issueType);

        if (CouponConstants.ISSUE_SECKILL.equals(issueType)) {
            validateSeckill(dto);
        } else {
            validateReceive(dto);
        }

        String description = normalize(dto.getDescription());
        if (description != null && description.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "使用说明不能超过 255 字");
        }
        dto.setDescription(description);
    }

    /** 秒杀券：固定有效期、限购 1、活动时间合法、有效期晚于活动结束 */
    private void validateSeckill(AdminCouponDTO dto) {
        // 秒杀券只能是代金券（固定面额 FIXED）：折扣券是免费领的打折券，
        // 花钱买折扣率在业务上不成立（用户花 80 买张 8 折券，买 100 元商品只省 20，净亏 60）。
        if (!CouponConstants.TYPE_FIXED.equals(dto.getType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀券必须为代金券类型");
        }
        // 秒杀券是用户花钱买的，不设使用门槛
        dto.setThresholdAmount(BigDecimal.ZERO);
        if (dto.getSeckillPrice() == null || dto.getSeckillPrice().signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀价必须大于 0");
        }
        if (dto.getTotalQuantity() == null || dto.getTotalQuantity() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀库存必须大于 0");
        }
        if (dto.getSeckillBeginTime() == null || dto.getSeckillEndTime() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀开始与结束时间不能为空");
        }
        if (!dto.getSeckillBeginTime().isBefore(dto.getSeckillEndTime())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀开始时间必须早于结束时间");
        }
        if (dto.getPerUserLimit() != null && dto.getPerUserLimit() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀券每人限购 1 张");
        }
        dto.setPerUserLimit(1);

        dto.setValidType(CouponConstants.VALID_FIXED);
        dto.setValidDays(null);
        if (dto.getValidStart() == null || dto.getValidEnd() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "秒杀券必须填写固定有效期");
        }
        if (!dto.getValidStart().isBefore(dto.getValidEnd())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "有效期开始必须早于结束");
        }
        if (!dto.getValidEnd().isAfter(dto.getSeckillEndTime())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "有效期结束必须晚于秒杀结束时间");
        }

        // 领取时间可不填，落库用秒杀时间兜底（receive_start/end 为 NOT NULL）
        if (dto.getReceiveStart() == null) {
            dto.setReceiveStart(dto.getSeckillBeginTime());
        }
        if (dto.getReceiveEnd() == null) {
            dto.setReceiveEnd(dto.getSeckillEndTime());
        }
        if (!dto.getReceiveStart().isBefore(dto.getReceiveEnd())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "领取开始时间必须早于结束时间");
        }
    }

    /** 普通券：不限库存，只校验领取时间与有效期 */
    private void validateReceive(AdminCouponDTO dto) {
        dto.setSeckillPrice(null);
        dto.setSeckillBeginTime(null);
        dto.setSeckillEndTime(null);
        if (dto.getTotalQuantity() == null || dto.getTotalQuantity() < 0) {
            dto.setTotalQuantity(0);
        }
        dto.setPerUserLimit(1);

        if (dto.getReceiveStart() == null || dto.getReceiveEnd() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "领取时间不能为空");
        }
        if (!dto.getReceiveStart().isBefore(dto.getReceiveEnd())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "领取开始时间必须早于结束时间");
        }

        String validType = dto.getValidType() == null ? CouponConstants.VALID_FIXED : upper(dto.getValidType());
        if (!CouponConstants.VALID_FIXED.equals(validType)
                && !CouponConstants.VALID_RECEIVE_DAYS.equals(validType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "有效期类型不合法");
        }
        dto.setValidType(validType);
        if (CouponConstants.VALID_FIXED.equals(validType)) {
            if (dto.getValidStart() == null || dto.getValidEnd() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "固定有效期不能为空");
            }
            if (!dto.getValidStart().isBefore(dto.getValidEnd())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "有效期开始必须早于结束");
            }
            dto.setValidDays(null);
        } else {
            if (dto.getValidDays() == null || dto.getValidDays() <= 0 || dto.getValidDays() > 365) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "领取后有效天数必须在 1~365 之间");
            }
            dto.setValidStart(null);
            dto.setValidEnd(null);
        }
    }

    private String scopeDesc(String scope) {
        return switch (scope == null ? "" : scope) {
            case CouponConstants.SCOPE_CATEGORY -> "指定分类";
            case CouponConstants.SCOPE_PRODUCT -> "指定商品";
            default -> "全场通用";
        };
    }

    /** 目标人群文案 */
    private String targetGroupDesc(String targetGroup) {
        return CouponConstants.TARGET_NEW_USER.equals(targetGroup) ? "新用户专享" : "全部用户";
    }

    /** 有效期类型文案 */
    private String validTypeDesc(String validType) {
        return CouponConstants.VALID_RECEIVE_DAYS.equals(validType) ? "领取后 N 天" : "固定时段";
    }

    /**
     * 券面额文案：满减券看面额，折扣券看折扣率（顺带写清是否封顶）。
     * <p>
     * 新建券时日志只有「新值」，没有旧值可比，所以按纯文本记。
     */
    private String faceValueText(Coupon template) {
        if (CouponConstants.TYPE_DISCOUNT.equals(template.getType())) {
            String max = template.getMaxDiscount() == null
                    ? "不封顶" : "最高优惠 " + template.getMaxDiscount() + " 元";
            return "折扣率 " + template.getDiscountRate() + "（" + max + "）";
        }
        return "满减 " + template.getFaceValue() + " 元";
    }

    /**
     * 记录券的字段变更（只记真正变了的）。
     * <p>
     * edit 是整单覆盖式更新（updateTemplate 把所有列都刷一遍），所以每个字段都要跟旧券比。
     */
    private void recordCouponDiff(Coupon old, Coupon now) {
        LogDetail.add(LogDiff.of("券名", old.getName(), now.getName()));
        addDecimalDiff("面额", old.getFaceValue(), now.getFaceValue());
        addDecimalDiff("折扣率", old.getDiscountRate(), now.getDiscountRate());
        addDecimalDiff("最高优惠", old.getMaxDiscount(), now.getMaxDiscount());
        addDecimalDiff("使用门槛", old.getThresholdAmount(), now.getThresholdAmount());
        LogDetail.add(LogDiff.of("适用范围", old.getScopeType(), now.getScopeType(),
                value -> scopeDesc(String.valueOf(value))));
        LogDetail.add(LogDiff.of("目标人群", old.getTargetGroup(), now.getTargetGroup(),
                value -> targetGroupDesc(String.valueOf(value))));
        LogDetail.add(LogDiff.of("会员等级门槛", old.getMinMemberLevel(), now.getMinMemberLevel()));
        LogDetail.add(LogDiff.of("发行总量", old.getTotalQuantity(), now.getTotalQuantity()));
        LogDetail.add(LogDiff.of("领取开始时间", old.getReceiveStart(), now.getReceiveStart()));
        LogDetail.add(LogDiff.of("领取结束时间", old.getReceiveEnd(), now.getReceiveEnd()));
        LogDetail.add(LogDiff.of("有效期类型", old.getValidType(), now.getValidType(),
                value -> validTypeDesc(String.valueOf(value))));
        LogDetail.add(LogDiff.of("固定有效期开始", old.getValidStart(), now.getValidStart()));
        LogDetail.add(LogDiff.of("固定有效期结束", old.getValidEnd(), now.getValidEnd()));
        LogDetail.add(LogDiff.of("领取后有效天数", old.getValidDays(), now.getValidDays()));
        LogDetail.add(LogDiff.of("使用说明", old.getDescription(), now.getDescription()));
    }

    /**
     * 金额/比率类字段的变更比较。
     * <p>
     * 为什么不用 LogDiff 自带的比较：BigDecimal 的 equals 比的是「值 + 小数位」，
     * 10.00 与 10.0 用 equals 判不相等，直接丢给 LogDiff 会写出「从【10.00】修改为【10.0】」这种假变更。
     */
    private void addDecimalDiff(String label, BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null && newValue == null) {
            return;
        }
        if (oldValue != null && newValue != null && oldValue.compareTo(newValue) == 0) {
            return;
        }
        LogDetail.add(LogDiff.of(label, oldValue, newValue));
    }

    private String statusDesc(String status) {
        return switch (status) {
            case CouponConstants.TEMPLATE_PENDING -> "未开始";
            case CouponConstants.TEMPLATE_ACTIVE -> "发放中";
            case CouponConstants.TEMPLATE_STOPPED -> "已停止";
            default -> "已结束";
        };
    }

    private String recordStatusDesc(String status) {
        return switch (status) {
            case CouponConstants.USER_USED -> "已使用";
            case CouponConstants.USER_LOCKED -> "使用中";
            case CouponConstants.USER_REFUNDED -> "已退款";
            default -> "未使用";
        };
    }

    /** 发放方式文案 */
    private String issueTypeDesc(String issueType) {
        return CouponConstants.ISSUE_SECKILL.equals(issueType) ? "秒杀券" : "普通券";
    }

    /** 券类型文案：折扣券统一显示「折扣券」；FIXED 在秒杀场景显示「代金券」，普通场景显示「满减券」 */
    private String couponTypeDesc(String type, String issueType) {
        if (CouponConstants.TYPE_DISCOUNT.equals(type)) {
            return "折扣券";
        }
        return CouponConstants.ISSUE_SECKILL.equals(issueType) ? "代金券" : "满减券";
    }

    private String normalizeIssueType(String issueType) {
        String value = normalize(issueType);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase();
        if (!CouponConstants.ISSUE_RECEIVE.equals(value) && !CouponConstants.ISSUE_SECKILL.equals(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "发放方式不合法");
        }
        return value;
    }

    private String normalizeStatus(String status) {
        String value = normalize(status);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase();
        if (!Set.of(CouponConstants.TEMPLATE_PENDING, CouponConstants.TEMPLATE_ACTIVE,
                CouponConstants.TEMPLATE_ENDED, CouponConstants.TEMPLATE_STOPPED).contains(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "券状态不合法");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }
}
