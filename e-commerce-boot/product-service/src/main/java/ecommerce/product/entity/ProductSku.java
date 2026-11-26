package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SKU（product_sku 表）。
 * <p>
 * SKU 的规格选择（"这个 SKU 选了哪些规格组的哪些值"）统一由中间表
 * product_sku_spec_rel 维护；service 层读写全部走 rel，不再使用冗余 JSON 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSku {
    private Long id;
    private Long productId;
    private String skuCode;
    private BigDecimal price;
    private BigDecimal marketPrice;
    private BigDecimal costPrice;
    private Integer stock;
    private String image;
    /** 1 启用 / 0 停用 */
    private Integer status;
    /** 1=单规格商品的默认 SKU；0=普通 SKU */
    private Integer isDefault;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
