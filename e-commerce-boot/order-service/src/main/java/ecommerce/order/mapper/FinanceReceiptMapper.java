package ecommerce.order.mapper;

import ecommerce.order.entity.FinanceReceipt;
import ecommerce.order.vo.FinanceReceiptVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 收款单 Mapper（finance_receipt 表）。
 */
public interface FinanceReceiptMapper {

    int insert(FinanceReceipt receipt);

    /** 该订单是否已有收款单（含已删除，配合 uk_order_id 防重复生成） */
    int countByOrderId(@Param("orderId") Long orderId);

    /** 查未删除的单据实体（审核前校验用） */
    FinanceReceipt findById(@Param("id") Long id);

    /** 详情：带买家姓名、账户名称、订单状态与退款标记 */
    FinanceReceiptVO findVOById(@Param("id") Long id);

    List<FinanceReceiptVO> listAdmin(@Param("offset") int offset, @Param("pageSize") int pageSize,
                                     @Param("receiptNo") String receiptNo, @Param("orderNo") String orderNo,
                                     @Param("userId") Long userId, @Param("channel") String channel,
                                     @Param("status") Integer status,
                                     @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                     @Param("accountUnassigned") Boolean accountUnassigned);

    long countAdmin(@Param("receiptNo") String receiptNo, @Param("orderNo") String orderNo,
                    @Param("userId") Long userId, @Param("channel") String channel,
                    @Param("status") Integer status,
                    @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                    @Param("accountUnassigned") Boolean accountUnassigned);

    /** 审核：条件更新（WHERE status = 0），返回 0 表示已被别人审核 */
    int audit(@Param("id") Long id, @Param("auditBy") Long auditBy, @Param("auditName") String auditName,
              @Param("now") LocalDateTime now, @Param("remark") String remark);

    int updateRemark(@Param("id") Long id, @Param("remark") String remark, @Param("now") LocalDateTime now);

    /** 区间内已审核实收合计（按 paid_at） */
    BigDecimal sumAuditedAmount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 区间内退款金额（只读 after_sales，按售后退款完成时间） */
    BigDecimal sumRefundAmount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 待审核笔数（全时段） */
    long countPending();

    // ==================== 财务看板 ====================

    BigDecimal sumDashboardAuditedAmount(@Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("channel") String channel);

    /** 按实际退款完成时间统计，可选按原订单支付渠道过滤；不因订单软删除而漏算 */
    BigDecimal sumFinanceRefundAmount(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("channel") String channel);

    List<Map<String, Object>> sumFinanceRefundByDay(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end,
                                                    @Param("channel") String channel);

    long countUnassigned();

    BigDecimal sumUnassignedAmount();

    BigDecimal sumPendingAmount();

    /** 已支付但没有有效收款单的订单，包括被用户软删除的订单 */
    long countMissingReceipts();

    /** 当前待办：待审核、未归集账户、支付后缺收款单 */
    List<Map<String, Object>> listFinanceTodos(@Param("limit") int limit);
}
