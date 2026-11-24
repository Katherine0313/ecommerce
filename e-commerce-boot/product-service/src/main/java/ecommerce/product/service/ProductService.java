package ecommerce.product.service;

import ecommerce.common.PageVO;
import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.TagVO;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    /**
     * 商品列表（占位：返回固定示例数据，接库后改为查 products 表）
     */
    List<ProductVO> listProducts();

    /**
     * 首页商品流（服务端真分页）：活动商品优先，推荐商品在对应分组内置顶，其余按 id 顺序补齐并去重。
     * 不限制商品总数（解决老接口 LIMIT 200 的截断问题）。
     */
    PageVO<ProductVO> feedProducts(int page, int pageSize, int recommendLimit);

    /**
     * 按一级分类（父类）查上架商品：聚合该父类名下所有二级分类的商品
     */
    List<ProductVO> listProductsByCategory(Long categoryId);

    /**
     * 按关键词搜索上架商品：匹配商品名 / 副标题 / 品牌
     */
    List<ProductVO> searchProducts(String keyword);

    /**
     * C 端分页商品列表：categoryId 支持一级/二级，另支持关键词、品牌、价格区间、排序白名单。
     */
    PageVO<ProductVO> pageProducts(int page, int pageSize, Long categoryId, String keyword,
                                   String brand, BigDecimal minPrice, BigDecimal maxPrice, String sort);

    /**
     * 随机取上架商品：首页下拉刷新「换一批」用。excludeIds 排除当前已展示的商品，
     * 避免刷新后和上一批大量重复；排除后不足则返回实际数量。
     */
    List<ProductVO> listRandomProducts(int limit, List<Long> excludeIds);

    /**
     * 首页热门推荐：优先取「推荐分类」（categories.recommend=1，含父分类推荐）下的商品，
     * 不够 limit 时用随机上架商品补足；excludeIds 排除当前已展示的，下拉刷新换一批用。
     */
    List<ProductVO> listHotProducts(int limit, List<Long> excludeIds);

    /**
     * 商品详情；商品不存在时抛 404
     */
    ProductVO getProduct(Long id);

    /**
     * 分类树列表：每个元素是一级父分类（含 children 子分类）
     */
    List<CategoryTreeVO> listCategories();

    /**
     * 标签列表
     */
    List<TagVO> listTags();

    /**
     * 根据商品查标签；商品不存在时抛 404，无标签返回空列表
     */
    List<TagVO> listTagsByProduct(Long productId);
}