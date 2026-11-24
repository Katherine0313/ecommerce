package ecommerce.order.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 财务看板聚合结果。交易实收与收款单审核口径分开返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDashboardVO {
    private LocalDateTime start;
    /** 结束时间（不包含） */
    private LocalDateTime end;
    private LocalDateTime serverTime;
    private Cards cards;
    private List<TrendRow> trend;
    private List<ChannelRow> channels;
    private Alerts alerts;
    /** 无 finance:receipt:list 权限时为空数组，同时 canViewTodoItems=false */
    private List<TodoItem> todoItems;
    private boolean canViewTodoItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cards {
        private BigDecimal grossAmount;
        private BigDecimal productAmount;
        private BigDecimal seckillAmount;
        private BigDecimal refundAmount;
        private BigDecimal productRefundAmount;
        private BigDecimal seckillRefundAmount;
        private BigDecimal netAmount;
        private BigDecimal auditedAmount;
        private Long pendingAuditCount;
        private BigDecimal pendingAuditAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendRow {
        private String date;
        private BigDecimal receivedAmount;
        private BigDecimal refundAmount;
        private BigDecimal netAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelRow {
        private String channel;
        private String channelDesc;
        private BigDecimal amount;
        private Long orderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alerts {
        private Long unassignedCount;
        private BigDecimal unassignedAmount;
        private Long missingReceiptCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodoItem {
        private Long receiptId;
        private Long orderId;
        private String orderNo;
        private String bizType;
        private String channel;
        private String channelDesc;
        private BigDecimal amount;
        private LocalDateTime paidAt;
        private String todoType;
        private String todoTypeDesc;
        private Long stayHours;
    }
}
