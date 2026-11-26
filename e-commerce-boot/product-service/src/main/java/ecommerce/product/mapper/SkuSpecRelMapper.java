package ecommerce.product.mapper;

import ecommerce.product.entity.SkuSpecRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SKU ↔ 规格值 多对多关联数据访问。
 */
@Mapper
public interface SkuSpecRelMapper {

    /** 按 SKU 批量查询其规格关联（按 SKU 分组返回） */
    List<SkuSpecRel> listBySkuIds(@Param("skuIds") List<Long> skuIds);

    /** 清空一个 SKU 的所有关联 */
    int deleteBySkuId(@Param("skuId") Long skuId);

    /** 删除某商品下所有 SKU 的规格关联（删商品时级联调用） */
    int deleteByProductId(@Param("productId") Long productId);

    /** 批量写入（先删后插） */
    int batchInsert(@Param("items") List<SkuSpecRel> items);

    /** 按 product_id 查出所有 SKU 的 rel，用于聚合时避免 N+1 */
    List<SkuSpecRel> listByProductId(@Param("productId") Long productId);

    /** 工具：根据 group name + value 反查 spec_value_id（写入时校验规格值是否在字典中） */
    Long findSpecValueId(@Param("groupName") String groupName,
                         @Param("value") String value);

    /** 工具：根据 group name 查 spec_group.id */
    Long findGroupIdByName(@Param("groupName") String groupName);

    /** 工具：查 product_spec_group.sort */
    Integer findGroupSort(@Param("groupId") Long groupId);

    /** 工具：查 product_spec_value 是否已存在 */
    Long findValueId(@Param("groupId") Long groupId, @Param("value") String value);

    /** 工具：自动补建 spec_value（字典中没有但 SKU 里有记录时） */
    int insertSpecValue(@Param("groupId") Long groupId, @Param("value") String value,
                        @Param("sort") Integer sort, @Param("now") LocalDateTime now);

    /** 工具：查同一 product_id 下，除了 excludedIds 以外的 SKU 里，
     *  是否存在一组与 given 完全相同的 (group_id, value) 组合 —— 服务层用来替换原 spec_key 唯一约束 */
    int countSkuWithSameSpecs(@Param("productId") Long productId,
                              @Param("excludedIds") List<Long> excludedIds,
                              @Param("relPairs") List<RelPair> relPairs);

    /** 内部用参数对象 */
    class RelPair {
        public Long groupId;
        public Long valueId;
    }
}
