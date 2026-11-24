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
 * 秒杀券订单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderVO {
    private String orderNo;
    private Long couponId;
    private String couponName;
    private BigDecimal payAmount;
    @Schema(description = "1 待支付 / 2 已支付 / 3 已取消 / 4 已退款")
    private Integer status;
    private String statusDesc;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;
    private String payType;
}
