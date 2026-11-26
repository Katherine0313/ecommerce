package ecommerce.order.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 精简实体（order-service 内查 product_sku 表）。
 * <p>
 * 规格选择由 product_sku_spec_rel 中间表承载；订单快照规格信息在 order_items.spec_json 内独立存储，
 * 与 product_sku 表字段解耦。
 */
@Data
public class ProductSku {
    private Long id;
    private Long productId;
    private String skuCode;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
    /** 规格组合 JSON 字符串（由 product_sku_spec_rel 聚合，如 {"颜色":"曜石黑"}） */
    private String specJson;
}
