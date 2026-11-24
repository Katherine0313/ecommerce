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
 * C 端秒杀券列表 / 详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillCouponVO {
    private Long couponId;
    private String name;
    private String type;
    private String typeDesc;
    private BigDecimal faceValue;
    private BigDecimal discountRate;
    private BigDecimal maxDiscount;
    private BigDecimal thresholdAmount;
    private BigDecimal seckillPrice;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime seckillBeginTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime seckillEndTime;
    private Integer totalQuantity;
    private Integer remainQuantity;
    @Schema(description = "NOT_STARTED / ACTIVE / ENDED / STOPPED")
    private String activityStatus;
    private String activityStatusDesc;
    private String description;
}
