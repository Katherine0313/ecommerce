package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 秒杀券订单支付请求。
 */
@Data
public class SeckillPayDTO {
    @Schema(description = "支付方式，如 WECHAT / ALIPAY / MOCK")
    private String payType;
}
