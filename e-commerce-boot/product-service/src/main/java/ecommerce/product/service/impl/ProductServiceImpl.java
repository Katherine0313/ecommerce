package ecommerce.product.service.impl;

import ecommerce.cache.CacheKeys;
import ecommerce.cache.CacheTtl;
import ecommerce.common.PageVO;
import ecommerce.common.insurance.InsurancePolicy;
import ecommerce.exception.BusinessException;
import ecommerce.product.entity.Category;
import ecommerce.product.entity.Product;
import ecommerce.product.entity.ProductSku;
import ecommerce.product.entity.Tag;
import ecommerce.product.mapper.CategoryMapper;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.mapper.ProductSkuMapper;
import ecommerce.product.mapper.TagMapper;
import ecommerce.product.service.ProductService;
import ecommerce.product.service.PromotionService;
import ecommerce.product.support.ProductSpecsCodec;
import ecommerce.product.vo.CategoryTreeVO;
import ecommerce.product.vo.ProductVO;
import ecommerce.product.vo.SkuSpecVO;
import ecommerce.product.vo.SkuVO;
import ecommerce.product.vo.TagVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品浏览 Service（单店模式，不再区分店铺）。
 */
@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    /** 缓存里表示「商品不存在」的哨兵值，用于防缓存穿透（TTL 必须短） */
    private static final String NULL_SENTINEL = "NULL";

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductSpecsCodec specsCodec;
    private final PromotionService promotionService;

    public ProductServiceImpl(ProductMapper productMapper, ProductSkuMapper skuMapper, CategoryMapper categoryMapper,
                              TagMapper tagMapper, StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                              ProductSpecsCodec specsCodec, PromotionService promotionService) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.specsCodec = specsCodec;
        this.promotionService = promotionService;
    }

    // 上架商品全量列表：PC 首页一次拉全量用。缓存 60 秒：
    // 后台增删改会主动删，但订单扣库存不删这个 key（一单删一次会导致缓存永远重建），库存靠 TTL 兜底
    @Override
    public List<ProductVO> listProducts() {
        try {
            String json = stringRedisTemplate.opsForValue().get(CacheKeys.PRODUCT_ONSALE_ALL);
            if (json != null) {
                return readProductList(json);
            }
        } catch (RuntimeException e) {
            log.warn("商品列表缓存不可用，降级直接查库", e);
            return queryOnSaleProducts();
        }
        List<ProductVO> list = queryOnSaleProducts();
        try {
            stringRedisTemplate.opsForValue().set(CacheKeys.PRODUCT_ONSALE_ALL, toJson(list),
                    CacheTtl.PRODUCT_ONSALE_ALL);
        } catch (RuntimeException e) {
            log.warn("商品列表缓存写入失败，忽略: {}", e.getMessage());
        }
        return list;
    }

    private List<ProductVO> queryOnSaleProducts() {
        return withPromotions(productMapper.listOnSale().stream()
                .map(this::toVO)
                .toList());
    }

    /**
     * 首页商品流（服务端真分页）：活动商品优先，推荐商品在对应分组内置顶，其余按 id 顺序补齐并去重。
     * <p>
     * 分页顺序来自 {@link CacheKeys#HOME_FEED_IDS}（缓存 60 秒）：既不限制商品总数，
     * 又能保证同一批翻页过程中 id 顺序稳定（否则每页重新随机推荐会出现重复/漏项）。
     */
    @Override
    public PageVO<ProductVO> feedProducts(int page, int pageSize, int recommendLimit) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 60);
        List<Long> orderedIds = feedOrderedIds(Math.max(recommendLimit, 0));
        long total = orderedIds.size();
        int from = (safePage - 1) * safeSize;
        if (from >= orderedIds.size()) {
            return new PageVO<>(List.of(), total, safePage, safeSize);
        }
        int to = Math.min(from + safeSize, orderedIds.size());
        List<Long> pageIds = orderedIds.subList(from, to);
        // 按 id 批量查库后按 feed 顺序重排（SQL 的 IN 不保证顺序）
        Map<Long, Product> byId = productMapper.listOnSaleByIds(pageIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<ProductVO> list = pageIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toVO)
                .toList();
        withPromotions(list);
        return new PageVO<>(list, total, safePage, safeSize);
    }

    /** feed 的 id 顺序：活动优先，推荐在对应分组内置顶 + 其余上架商品（去重）；缓存不可用时退化为每次现算 */
    private List<Long> feedOrderedIds(int recommendLimit) {
        try {
            String json = stringRedisTemplate.opsForValue().get(CacheKeys.HOME_FEED_IDS);
            if (json != null) {
                return readIds(json);
            }
        } catch (RuntimeException e) {
            log.warn("首页 feed 缓存不可用，降级现算", e);
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> recommendIds = recommendLimit <= 0
                ? List.of()
                : queryRandomOnSale(recommendLimit, null, now).stream()
                        .map(Product::getId)
                        .toList();
        Set<Long> recommendSet = new HashSet<>(recommendIds);
        List<Long> ordered = new ArrayList<>(recommendIds);
        for (Long id : queryOnSaleIds(now)) {
            if (!recommendSet.contains(id)) {
                ordered.add(id);
            }
        }
        try {
            stringRedisTemplate.opsForValue().set(CacheKeys.HOME_FEED_IDS, toJson(ordered),
                    CacheTtl.HOME_FEED_IDS);
        } catch (RuntimeException e) {
            log.warn("首页 feed 缓存写入失败，忽略: {}", e.getMessage());
        }
        return ordered;
    }

    private List<Long> queryOnSaleIds(LocalDateTime now) {
        try {
            return productMapper.listOnSaleIds(now, true);
        } catch (RuntimeException e) {
            // 与促销展示保持一致：促销表未迁移或临时故障时，不影响商品浏览
            log.warn("活动商品优先排序不可用，feed 降级为普通排序: {}", e.getMessage());
            return productMapper.listOnSaleIds(now, false);
        }
    }

    private List<Product> queryRandomOnSale(int limit, List<Long> excludeIds, LocalDateTime now) {
        try {
            return productMapper.listRandomOnSale(limit, excludeIds, now, true);
        } catch (RuntimeException e) {
            log.warn("活动商品优先排序不可用，随机列表降级为普通排序: {}", e.getMessage());
            return productMapper.listRandomOnSale(limit, excludeIds, now, false);
        }
    }

    // 按一级分类（父类）查上架商品（不缓存）
    @Override
    public List<ProductVO> listProductsByCategory(Long categoryId) {
        return withPromotions(productMapper.listOnSaleByCategory(categoryId).stream()
                .map(this::toVO)
                .toList());
    }

    // 按关键词搜索上架商品（商品名/副标题/品牌模糊匹配）
    @Override
    public List<ProductVO> searchProducts(String keyword) {
        return withPromotions(productMapper.searchOnSale(keyword).stream()
                .map(this::toVO)
                .toList());
    }

    // C 端分页列表：参数归一化后交给 Mapper；旧 list/search 接口保持数组结构不变
    @Override
    public PageVO<ProductVO> pageProducts(int page, int pageSize, Long categoryId, String keyword,
                                          String brand, BigDecimal minPrice, BigDecimal maxPrice, String sort) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(60, Math.max(1, pageSize));
        String safeKeyword = normalize(keyword);
        String safeBrand = normalize(brand);
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "价格不能为负数");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "最低价不能大于最高价");
        }
        String sortInput = sort == null ? "default" : sort;
        String safeSort = switch (sortInput) {
            case "price_asc", "price_desc", "newest", "default" -> sortInput;
            default -> "default";
        };
        long offsetLong = (long) (safePage - 1) * safePageSize;
        int offset = offsetLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offsetLong;

        List<ProductVO> list = withPromotions(productMapper
                .pageOnSale(categoryId, safeKeyword, safeBrand, minPrice, maxPrice, safeSort, offset, safePageSize)
                .stream().map(this::toVO).toList());
        long total = productMapper.countOnSale(categoryId, safeKeyword, safeBrand, minPrice, maxPrice);
        return new PageVO<>(list, total, safePage, safePageSize);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // 活动商品优先、同组随机：首页下拉刷新换一批（排除当前已展示的 id）
    @Override
    public List<ProductVO> listRandomProducts(int limit, List<Long> excludeIds) {
        // 兜住异常入参，避免 LIMIT 负数/过大
        int size = Math.max(1, Math.min(limit, 50));
        return withPromotions(queryRandomOnSale(size, excludeIds, LocalDateTime.now()).stream()
                .map(this::toVO)
                .toList());
    }

    // 首页热门推荐：推荐分类优先，不够再随机补足
    @Override
    public List<ProductVO> listHotProducts(int limit, List<Long> excludeIds) {
        int size = Math.max(1, Math.min(limit, 50));
        List<Long> exclude = excludeIds == null ? new ArrayList<>() : new ArrayList<>(excludeIds);

        List<ProductVO> hot = productMapper
                .listRandomOnSaleByRecommendedCategory(size, exclude).stream()
                .map(this::toVO)
                .toList();
        if (hot.size() >= size) {
            return withPromotions(hot);
        }

        // 推荐分类商品不够：排除已选中的 id 后再随机补足
        hot.forEach(p -> exclude.add(p.getId()));
        List<ProductVO> fill = queryRandomOnSale(size - hot.size(), exclude, LocalDateTime.now()).stream()
                .map(this::toVO)
                .toList();
        List<ProductVO> result = new ArrayList<>(hot);
        result.addAll(fill);
        return withPromotions(result);
    }

    // 商品详情：缓存 2 分钟；不存在的 id 也缓存短 TTL 的 "NULL" 哨兵，防止无效 id 反复打库
    // 库存变动由 StockWriteService 精确删缓存，TTL 主要兜住「促销到期」这类时间事件
    @Override
    public ProductVO getProduct(Long id) {
        String key = CacheKeys.PRODUCT_DETAIL + id;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                if (NULL_SENTINEL.equals(json)) {
                    throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
                }
                return readProduct(json);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("商品详情缓存不可用，降级直接查库: id={}", id, e);
            return loadProductDetail(id);
        }
        ProductVO vo;
        try {
            vo = loadProductDetail(id);
        } catch (BusinessException e) {
            // 商品不存在：写空值哨兵，短 TTL，新商品上架后很快恢复
            cacheNull(key, CacheTtl.PRODUCT_NULL);
            throw e;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, toJson(vo), CacheTtl.PRODUCT_DETAIL);
        } catch (RuntimeException e) {
            log.warn("商品详情缓存写入失败，忽略: id={}", id, e);
        }
        return vo;
    }

    private ProductVO loadProductDetail(Long id) {
        Product product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        ProductVO vo = toVO(product);
        if (product.getSkuType() != null && product.getSkuType() == 1) {
            List<ProductSku> skus = skuMapper.listEnabledByProductId(id);
            vo.setSkus(skus.stream().map(this::toSkuVO).toList());
            BigDecimal min = null;
            BigDecimal max = null;
            for (ProductSku s : skus) {
                if (s.getPrice() == null) {
                    continue;
                }
                min = min == null || s.getPrice().compareTo(min) < 0 ? s.getPrice() : min;
                max = max == null || s.getPrice().compareTo(max) > 0 ? s.getPrice() : max;
            }
            vo.setPriceMin(min != null ? min : product.getSalesPrice());
            vo.setPriceMax(max != null ? max : product.getSalesPrice());
        } else {
            vo.setSkus(List.of());
            vo.setPriceMin(product.getSalesPrice());
            vo.setPriceMax(product.getSalesPrice());
        }
        promotionService.applyDisplay(List.of(vo));
        // 增值保障按最终展示价（含活动价）计价，规则由后端统一判断，前端只渲染返回的选项
        vo.setInsuranceOptions(InsurancePolicy.options(vo.getCategoryId(), vo.getPrice()));
        return vo;
    }

    // 分类树：读 ids 索引 → MGET 各父分类树 → 合并返回；缺失则整体重建
    // Redis 不可用时降级直接查库：分类是首页/分类页的必需数据，不能因为缓存故障导致整页加载失败
    @Override
    public List<CategoryTreeVO> listCategories() {
        try {
            String idsJson = stringRedisTemplate.opsForValue().get(CacheKeys.CATEGORY_PARENT_IDS);
            if (idsJson == null) {
                return rebuildCategoryCache();
            }
            List<Long> parentIds = readIds(idsJson);
            List<String> keys = parentIds.stream()
                    .map(id -> (CacheKeys.CATEGORY_PARENT + "%d").formatted(id))
                    .toList();
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            // 有任一父分类树缺失就整体重建（分类少、变更不频繁，重建最省事）
            if (values.stream().anyMatch(Objects::isNull)) {
                return rebuildCategoryCache();
            }
            List<CategoryTreeVO> result = new ArrayList<>(values.size());
            for (String v : values) {
                result.add(readTree(v));
            }
            return result;
        } catch (RuntimeException e) {
            // 连接超时/拒绝、序列化失败等都回源查库，保证首页和分类页仍可用
            log.warn("分类缓存不可用，降级直接查库", e);
            return buildTree(categoryMapper.listCategories());
        }
    }

    /** 索引/父分类树缺失时全量重建：写 ids + 每棵父分类树 */
    private List<CategoryTreeVO> rebuildCategoryCache() {
        log.info("部分缓存缺失，现在全部重建：");
        List<Category> categories = categoryMapper.listCategories();
        List<CategoryTreeVO> tree = buildTree(categories);
        List<Long> parentIds = tree.stream().map(CategoryTreeVO::getId).collect(Collectors.toList());
        stringRedisTemplate.opsForValue().set(CacheKeys.CATEGORY_PARENT_IDS, toJson(parentIds), CacheTtl.CATEGORY);
        for (CategoryTreeVO parent : tree) {
            stringRedisTemplate.opsForValue().set(
                    (CacheKeys.CATEGORY_PARENT + "%d").formatted(parent.getId()), toJson(parent), CacheTtl.CATEGORY);
        }
        return tree;
    }

    /** 把扁平分类列表组装成树：parent_id 为 null 的是一级父分类，其余挂到对应 children */
    private List<CategoryTreeVO> buildTree(List<Category> categories) {
        Map<Long, List<Category>> childrenMap = categories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        List<CategoryTreeVO> parents = categories.stream()
                .filter(c -> c.getParentId() == null)
                .map(this::toTreeVO)
                .collect(Collectors.toList());
        for (CategoryTreeVO parent : parents) {
            List<Category> children = childrenMap.get(parent.getId());
            parent.setChildren(children == null ? List.of()
                    : children.stream().map(this::toTreeVO).collect(Collectors.toList()));
        }
        return parents;
    }

    private CategoryTreeVO toTreeVO(Category c) {
        return CategoryTreeVO.builder()
                .id(c.getId())
                .name(c.getName())
                .parentId(c.getParentId())
                .sort(c.getSortOrder())
                .status(c.getStatus())
                .recommend(c.getRecommend())
                .image(c.getImage())
                .productCount(c.getProductCount())
                .build();
    }

    /** 对象序列化为纯 JSON（无 @class） */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存序列化失败", e);
        }
    }

    private List<Long> readIds(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, Long[].class));
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存反序列化失败", e);
        }
    }

    private CategoryTreeVO readTree(String json) {
        try {
            return objectMapper.readValue(json, CategoryTreeVO.class);
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存反序列化失败", e);
        }
    }

    private ProductVO readProduct(String json) {
        try {
            return objectMapper.readValue(json, ProductVO.class);
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存反序列化失败", e);
        }
    }

    private List<ProductVO> readProductList(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, ProductVO[].class));
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存反序列化失败", e);
        }
    }

    private List<TagVO> readTagList(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, TagVO[].class));
        } catch (Exception e) {
            throw new RuntimeException("Redis 缓存反序列化失败", e);
        }
    }

    /** 写空值哨兵：商品不存在时挡住后续无效请求；写失败不影响主流程 */
    private void cacheNull(String key, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, NULL_SENTINEL, ttl);
        } catch (RuntimeException e) {
            log.warn("空值缓存写入失败，忽略: key={}", key, e);
        }
    }

    @Override
    public List<TagVO> listTags() {
        return tagMapper.listTags().stream()
                .map(this::toVO)
                .toList();
    }

    // 商品标签：与详情同生命周期缓存 2 分钟，避免详情页两个请求各自重复查一次商品
    @Override
    public List<TagVO> listTagsByProduct(Long productId) {
        String key = CacheKeys.PRODUCT_TAGS + productId;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                if (NULL_SENTINEL.equals(json)) {
                    throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
                }
                return readTagList(json);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("商品标签缓存不可用，降级直接查库: id={}", productId, e);
            return loadTagsByProduct(productId);
        }
        List<TagVO> tags;
        try {
            tags = loadTagsByProduct(productId);
        } catch (BusinessException e) {
            cacheNull(key, CacheTtl.PRODUCT_NULL);
            throw e;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, toJson(tags), CacheTtl.PRODUCT_TAGS);
        } catch (RuntimeException e) {
            log.warn("商品标签缓存写入失败，忽略: id={}", productId, e);
        }
        return tags;
    }

    private List<TagVO> loadTagsByProduct(Long productId) {
        // 商品不存在按详情接口口径返回 404，避免前端拿到空数组误判为「无标签」
        if (productMapper.findById(productId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return tagMapper.listTagsByProductId(productId).stream()
                .map(this::toVO)
                .toList();
    }

    /** 商品列表统一补促销价与标签（批量查活动，避免逐条查询） */
    private List<ProductVO> withPromotions(List<ProductVO> list) {
        promotionService.applyDisplay(list);
        return list;
    }

    private ProductVO toVO(Product p) {
        return ProductVO.builder()
                .id(p.getId())
                .name(p.getName())
                .subTitle(p.getSubTitle())
                .brand(p.getBrand())
                .image(p.getImage())
                .title(p.getTitle())
                .body(p.getBody())
                .price(p.getSalesPrice())
                .marketPrice(p.getMarketPrice())
                .stock(p.getStock())
                .categoryId(p.getCategoryId())
                .tagId(p.getTagId())
                .salesMode(p.getSalesMode())
                .status(p.getListingStatus())
                .productCode(p.getProductCode())
                .specs(specsCodec.decode(p.getSpecs()))
                .skuType(p.getSkuType() == null ? 0 : p.getSkuType())
                .skuSpecs(decodeSkuSpecs(p.getSkuSpecs()))
                .skus(List.of())
                .priceMin(p.getSalesPrice())
                .build();
    }

    private SkuVO toSkuVO(ProductSku s) {
        SkuVO vo = new SkuVO();
        vo.setId(s.getId());
        vo.setSpecKey(s.getSpecKey());
        vo.setSpecJson(parseSpecJson(s.getSpecJson()));
        vo.setPrice(s.getPrice());
        vo.setMarketPrice(s.getMarketPrice());
        vo.setStock(s.getStock());
        vo.setImage(s.getImage());
        vo.setSkuCode(s.getSkuCode());
        vo.setStatus(s.getStatus());
        return vo;
    }

    private List<SkuSpecVO> decodeSkuSpecs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            SkuSpecVO[] array = objectMapper.readValue(json, SkuSpecVO[].class);
            return array == null ? List.of() : Arrays.asList(array);
        } catch (Exception e) {
            log.warn("商品 sku_specs 反序列化失败，按空规格处理: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseSpecJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return (Map<String, String>) objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("SKU spec_json 反序列化失败，按空规格处理: {}", e.getMessage());
            return Map.of();
        }
    }

    private TagVO toVO(Tag t) {
        return TagVO.builder()
                .id(t.getId())
                .name(t.getName())
                .code(t.getCode())
                .type(t.getType())
                .color(t.getColor())
                .sort(t.getSortOrder())
                .status(t.getStatus())
                .productCount(t.getProductCount())
                .build();
    }
}
