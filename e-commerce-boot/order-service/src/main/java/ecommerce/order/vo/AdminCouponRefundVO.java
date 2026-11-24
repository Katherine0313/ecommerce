package ecommerce.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台秒杀券退款单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCouponRefundVO {
    private Long id;
    private String refundNo;
    private Long seckillOrderId;
    private String orderNo;
    private String couponName;
    private Long userId;
    private String userName;
    private String phone;
    private Long couponUserId;
    private BigDecimal refundAmount;
    @Schema(description = "REVIEWING / APPROVING / REFUNDED / REJECTED")
    private String status;
    private String statusDesc;
    private String reason;
    private String images;
    private String merchantRemark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refundTime;
}
