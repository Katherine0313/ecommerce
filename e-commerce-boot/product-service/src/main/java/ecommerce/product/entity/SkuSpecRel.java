package ecommerce.product.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SKU 与规格值的多对多关联（中间表 product_sku_spec_rel）。
 * <p>
 * 一个 SKU 可以选多个规格组的值（如 颜色=曜石黑，存储容量=256G），
 * 每个规格组在一个 SKU 里只选一个值（由 PRIMARY KEY (sku_id, spec_group_id) 保证）。
 * spec_value 字符串冗余存储，即使字典被删也能保持语义不丢失。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuSpecRel {
    private Long skuId;
    private Long specGroupId;
    private Long specValueId;
    private String specValue;
    private Integer sort;
    private LocalDateTime createTime;
    /** 仅查询时填充（JOIN product_spec_group 得到），用于拼装 Map<String, String> specJson */
    private String groupName;
}
