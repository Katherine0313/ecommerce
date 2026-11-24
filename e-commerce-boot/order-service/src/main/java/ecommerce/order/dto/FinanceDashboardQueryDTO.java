package ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 财务看板筛选条件。日期按自然日包含起止两天，服务层转换为左闭右开区间。
 */
@Data
public class FinanceDashboardQueryDTO {

    @Schema(description = "开始日期，yyyy-MM-dd；默认近 30 日的第一天")
    private String startDate;

    @Schema(description = "结束日期，yyyy-MM-dd；默认今天")
    private String endDate;

    @Schema(description = "业务类型：ALL / PRODUCT / SECKILL")
    private String bizType;

    @Schema(description = "支付渠道：WeChat / Alipay / BankCard")
    private String channel;
}
