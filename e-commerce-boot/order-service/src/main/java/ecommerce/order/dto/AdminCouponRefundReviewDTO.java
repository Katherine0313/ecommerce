package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台秒杀券退款审核请求。
 */
@Data
public class AdminCouponRefundReviewDTO {
    @Schema(description = "商家备注（拒绝时建议填写原因）")
    private String merchantRemark;
}
