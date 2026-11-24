package ecommerce.order.service.impl;

import ecommerce.exception.BusinessException;
import ecommerce.order.dto.FinanceDashboardQueryDTO;
import ecommerce.order.enums.PayTypeEnum;
import ecommerce.order.mapper.CouponSeckillOrderMapper;
import ecommerce.order.mapper.FinanceReceiptMapper;
import ecommerce.order.mapper.OrderMapper;
import ecommerce.order.service.FinanceDashboardService;
import ecommerce.order.vo.FinanceDashboardVO;
import ecommerce.utils.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 财务看板：交易现金流与收款单审核状态分开统计。
 */
@Service
public class FinanceDashboardServiceImpl implements FinanceDashboardService {

    private static final String ALL = "ALL";
    private static final String PRODUCT = "PRODUCT";
    private static final String SECKILL = "SECKILL";
    private static final Set<String> BIZ_TYPES = Set.of(ALL, PRODUCT, SECKILL);
    private static final String ALL_PERMISSION = "*:*:*";
    private static final String TODO_PERMISSION = "finance:receipt:list";
    private static final int TODO_LIMIT = 8;
    private static final int MAX_DAYS = 366;

    private final OrderMapper orderMapper;
    private final CouponSeckillOrderMapper seckillOrderMapper;
    private final FinanceReceiptMapper financeReceiptMapper;

    public FinanceDashboardServiceImpl(OrderMapper orderMapper,
                                       CouponSeckillOrderMapper seckillOrderMapper,
                                       FinanceReceiptMapper financeReceiptMapper) {
        this.orderMapper = orderMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.financeReceiptMapper = financeReceiptMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public FinanceDashboardVO getDashboard(FinanceDashboardQueryDTO query) {
        FinanceDashboardQueryDTO q = query == null ? new FinanceDashboardQueryDTO() : query;
        LocalDate endDate = parseDate(q.getEndDate(), "结束日期", LocalDate.now());
        LocalDate startDate = parseDate(q.getStartDate(), "开始日期", endDate.minusDays(29));
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (Duration.between(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()).toDays() > MAX_DAYS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "日期范围最长为 366 天");
        }

        String bizType = normalizeBizType(q.getBizType());
        String channel = normalizeChannel(q.getChannel());
        boolean includeProduct = !SECKILL.equals(bizType);
        boolean includeSeckill = !PRODUCT.equals(bizType);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal productAmount = includeProduct
                ? zero(orderMapper.sumFinancePaid(start, end, channel)) : BigDecimal.ZERO;
        BigDecimal seckillAmount = includeSeckill
                ? zero(seckillOrderMapper.sumFinancePaid(start, end, channel)) : BigDecimal.ZERO;
        BigDecimal productRefund = includeProduct
                ? zero(financeReceiptMapper.sumFinanceRefundAmount(start, end, channel)) : BigDecimal.ZERO;
        BigDecimal seckillRefund = includeSeckill
                ? zero(seckillOrderMapper.sumFinanceRefunded(start, end, channel)) : BigDecimal.ZERO;
        BigDecimal auditedAmount = includeProduct
                ? zero(financeReceiptMapper.sumDashboardAuditedAmount(start, end, channel)) : BigDecimal.ZERO;
        BigDecimal grossAmount = productAmount.add(seckillAmount);
        BigDecimal refundAmount = productRefund.add(seckillRefund);

        FinanceDashboardVO.Cards cards = FinanceDashboardVO.Cards.builder()
                .grossAmount(grossAmount)
                .productAmount(productAmount)
                .seckillAmount(seckillAmount)
                .refundAmount(refundAmount)
                .productRefundAmount(productRefund)
                .seckillRefundAmount(seckillRefund)
                .netAmount(grossAmount.subtract(refundAmount))
                .auditedAmount(auditedAmount)
                .pendingAuditCount(financeReceiptMapper.countPending())
                .pendingAuditAmount(zero(financeReceiptMapper.sumPendingAmount()))
                .build();

        boolean canViewTodoItems = canViewTodoItems();
        List<FinanceDashboardVO.TodoItem> todoItems = canViewTodoItems
                ? buildTodos(financeReceiptMapper.listFinanceTodos(TODO_LIMIT), now) : List.of();

        return FinanceDashboardVO.builder()
                .start(start)
                .end(end)
                .serverTime(now)
                .cards(cards)
                .trend(buildTrend(startDate, endDate, start, end, channel, includeProduct, includeSeckill))
                .channels(buildChannels(start, end, channel, includeProduct, includeSeckill))
                .alerts(FinanceDashboardVO.Alerts.builder()
                        .unassignedCount(financeReceiptMapper.countUnassigned())
                        .unassignedAmount(zero(financeReceiptMapper.sumUnassignedAmount()))
                        .missingReceiptCount(financeReceiptMapper.countMissingReceipts())
                        .build())
                .todoItems(todoItems)
                .canViewTodoItems(canViewTodoItems)
                .build();
    }

    private List<FinanceDashboardVO.TrendRow> buildTrend(LocalDate startDate, LocalDate endDate,
                                                          LocalDateTime start, LocalDateTime end,
                                                          String channel, boolean includeProduct,
                                                          boolean includeSeckill) {
        Map<String, BigDecimal> received = new HashMap<>();
        Map<String, BigDecimal> refunded = new HashMap<>();
        if (includeProduct) {
            mergeDailyAmounts(received, orderMapper.sumFinancePaidByDay(start, end, channel));
            mergeDailyAmounts(refunded, financeReceiptMapper.sumFinanceRefundByDay(start, end, channel));
        }
        if (includeSeckill) {
            mergeDailyAmounts(received, seckillOrderMapper.sumFinancePaidByDay(start, end, channel));
            mergeDailyAmounts(refunded, seckillOrderMapper.sumFinanceRefundedByDay(start, end, channel));
        }

        List<FinanceDashboardVO.TrendRow> rows = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            String date = day.toString();
            BigDecimal receivedAmount = received.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal refundAmount = refunded.getOrDefault(date, BigDecimal.ZERO);
            rows.add(FinanceDashboardVO.TrendRow.builder()
                    .date(date)
                    .receivedAmount(receivedAmount)
                    .refundAmount(refundAmount)
                    .netAmount(receivedAmount.subtract(refundAmount))
                    .build());
        }
        return rows;
    }

    private List<FinanceDashboardVO.ChannelRow> buildChannels(LocalDateTime start, LocalDateTime end,
                                                                String channel, boolean includeProduct,
                                                                boolean includeSeckill) {
        Map<String, FinanceDashboardVO.ChannelRow> channelMap = new HashMap<>();
        if (includeProduct) {
            mergeChannels(channelMap, orderMapper.sumFinancePaidByChannel(start, end, channel));
        }
        if (includeSeckill) {
            mergeChannels(channelMap, seckillOrderMapper.sumFinancePaidByChannel(start, end, channel));
        }
        return channelMap.values().stream()
                .sorted(Comparator.comparing(FinanceDashboardVO.ChannelRow::getAmount,
                        Comparator.reverseOrder()))
                .toList();
    }

    private void mergeDailyAmounts(Map<String, BigDecimal> target, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object date = row.get("date_key");
            if (date != null) {
                target.merge(date.toString(), decimal(row.get("amount")), BigDecimal::add);
            }
        }
    }

    private void mergeChannels(Map<String, FinanceDashboardVO.ChannelRow> target,
                               List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String channel = String.valueOf(row.get("channel"));
            FinanceDashboardVO.ChannelRow item = target.computeIfAbsent(channel, key ->
                    FinanceDashboardVO.ChannelRow.builder()
                            .channel(key)
                            .channelDesc(channelDesc(key))
                            .amount(BigDecimal.ZERO)
                            .orderCount(0L)
                            .build());
            item.setAmount(item.getAmount().add(decimal(row.get("amount"))));
            item.setOrderCount(item.getOrderCount() + longValue(row.get("order_count"), 0L));
        }
    }

    private List<FinanceDashboardVO.TodoItem> buildTodos(List<Map<String, Object>> rows, LocalDateTime now) {
        List<FinanceDashboardVO.TodoItem> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String channel = String.valueOf(row.get("channel"));
            String todoType = String.valueOf(row.get("todo_type"));
            LocalDateTime paidAt = dateTime(row.get("paid_at"));
            items.add(FinanceDashboardVO.TodoItem.builder()
                    .receiptId(longValue(row.get("receipt_id"), null))
                    .orderId(longValue(row.get("order_id"), null))
                    .orderNo((String) row.get("order_no"))
                    .bizType((String) row.get("biz_type"))
                    .channel(channel)
                    .channelDesc(channelDesc(channel))
                    .amount(decimal(row.get("amount")))
                    .paidAt(paidAt)
                    .todoType(todoType)
                    .todoTypeDesc(todoTypeDesc(todoType))
                    .stayHours(paidAt == null ? null
                            : Math.max(0, Duration.between(paidAt, now).toHours()))
                    .build());
        }
        return items;
    }

    private boolean canViewTodoItems() {
        List<String> permissions = UserContext.getPermissions();
        return permissions.contains(ALL_PERMISSION) || permissions.contains(TODO_PERMISSION);
    }

    private String normalizeBizType(String value) {
        String text = blankToNull(value);
        String bizType = text == null ? ALL : text.toUpperCase(Locale.ROOT);
        if (!BIZ_TYPES.contains(bizType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "业务类型只能是 ALL / PRODUCT / SECKILL");
        }
        return bizType;
    }

    private String normalizeChannel(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        PayTypeEnum type = PayTypeEnum.fromCode(text);
        if (type == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "渠道只能是 WeChat / Alipay / BankCard");
        }
        return type.getCode();
    }

    private LocalDate parseDate(String value, String field, LocalDate defaultValue) {
        String text = blankToNull(value);
        if (text == null) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, field + "格式应为 yyyy-MM-dd");
        }
    }

    private String channelDesc(String channel) {
        PayTypeEnum type = PayTypeEnum.fromCode(channel);
        if (type != null) {
            return type.getDesc();
        }
        return "Unknown".equals(channel) ? "未知渠道" : channel;
    }

    private String todoTypeDesc(String todoType) {
        return switch (todoType) {
            case "PENDING_AUDIT" -> "待审核";
            case "UNASSIGNED" -> "未归集账户";
            case "MISSING_RECEIPT" -> "缺收款单";
            default -> todoType;
        };
    }

    private LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString());
    }

    private Long longValue(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
