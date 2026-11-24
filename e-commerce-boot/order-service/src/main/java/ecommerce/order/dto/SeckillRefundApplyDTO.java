package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 秒杀券退款申请。
 */
@Data
public class SeckillRefundApplyDTO {
    @Schema(description = "退款原因")
    private String reason;
    @Schema(description = "凭证图片，多张用逗号分隔")
    private String images;
}
