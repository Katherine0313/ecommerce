package ecommerce.product.service.impl;

import ecommerce.common.PageVO;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.LogDiff;
import ecommerce.log.OperationLog;
import ecommerce.product.cache.CategoryCacheEvictor;
import ecommerce.product.cache.ProductCacheEvictor;
import ecommerce.common.insurance.InsurancePolicy;
import ecommerce.product.dto.AdminProductDTO;
import ecommerce.product.dto.AdminSkuDTO;
import ecommerce.product.dto.SkuSpecDTO;
import ecommerce.product.entity.Product;
import ecommerce.product.entity.ProductSku;
import ecommerce.product.entity.SkuSpecRel;
import ecommerce.product.mapper.ProductMapper;
import ecommerce.product.mapper.ProductSkuMapper;
import ecommerce.product.mapper.SkuSpecRelMapper;
import ecommerce.product.service.AdminProductService;
import ecommerce.product.support.ProductSpecsCodec;
import ecommerce.product.vo.AdminProductVO;
import ecommerce.product.vo.DashboardVO;
import ecommerce.product.vo.SkuSpecVO;
import ecommerce.product.vo.SkuVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 后台商品管理 Service 实现。
 */
@Slf4j
@Service
public class AdminProductServiceImpl implements AdminProductService {

    private static final int MAX_SPEC_VALUES = 10;

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final SkuSpecRelMapper skuSpecRelMapper;
    private final ProductSpecsCodec specsCodec;
    private final CategoryCacheEvictor categoryCacheEvictor;
    private final ProductCacheEvictor productCacheEvictor;
    private final ObjectMapper objectMapper;

    public AdminProductServiceImpl(ProductMapper productMapper, ProductSkuMapper skuMapper,
                                   SkuSpecRelMapper skuSpecRelMapper,
                                   ProductSpecsCodec specsCodec, CategoryCacheEvictor categoryCacheEvictor,
                                   ProductCacheEvictor productCacheEvictor, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.skuSpecRelMapper = skuSpecRelMapper;
        this.specsCodec = specsCodec;
        this.categoryCacheEvictor = categoryCacheEvictor;
        this.productCacheEvictor = productCacheEvictor;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageVO<AdminProductVO> list(int page, int pageSize, String search, Long categoryId) {
        int offset = (page - 1) * pageSize;
        List<AdminProductVO> list = productMapper
                .listAdmin(search, categoryId, offset, pageSize).stream()
                .map(p -> toVO(p, false))
                .toList();
        long total = productMapper.countAdmin(search, categoryId);
        return new PageVO<>(list, total, page, pageSize);
    }

    @Override
    public AdminProductVO get(Long id) {
        Product p = productMapper.findByIdAdmin(id);
        if (p == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return toVO(p, true);
    }

    @Override
    @Transactional
    @OperationLog(module = "product", action = "PRODUCT_ADD")
    public AdminProductVO create(AdminProductDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品名称不能为空");
        }
        validateSkuPayload(dto, null);
        Product p = new Product();
        applyDto(p, dto);
        // 后台新增表单没有销售模式字段，且部分 NOT NULL 列插 NULL 会覆盖库默认值，这里统一兜底
        if (p.getSalesMode() == null) p.setSalesMode("spot");
        if (p.getListingStatus() == null) p.setListingStatus(1);
        if (p.getSubTitle() == null) p.setSubTitle("");
        if (p.getBrand() == null) p.setBrand("");
        if (p.getSalesPrice() == null) p.setSalesPrice(BigDecimal.ZERO);
        if (p.getStock() == null) p.setStock(0);
        LocalDateTime now = LocalDateTime.now();
        p.setCreateTime(now);
        p.setUpdateTime(now);
        productMapper.insert(p);
        saveSkus(p.getId(), dto, p, null, now, true);
        categoryCacheEvictor.evictAll();
        // 新增商品会改变全量列表与首页 feed 顺序
        productCacheEvictor.evictList();
        // 回查一次拿到 DB 默认值（如 tag_id）与 SKU 汇总后的价格库存，保证返回体与库一致
        AdminProductVO vo = toVO(productMapper.findByIdAdmin(p.getId()), true);
        // 新增没有「改前值」，只记关键识别信息，方便在日志里定位是哪件商品
        LogDetail.add("新增商品：名称【" + vo.getName() + "】、销售价【" + vo.getSalesPrice()
                + "】、初始库存【" + vo.getStock() + "】");
        return vo;
    }

    @Override
    @Transactional
    @OperationLog(module = "product", action = "PRODUCT_EDIT", bizId = "#id")
    public AdminProductVO update(Long id, AdminProductDTO dto) {
        // 复用这次查询的结果：既判断存在性，又当「改前值」算变更，不重复查库
        Product old = productMapper.findByIdAdmin(id);
        if (old == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        validateSkuPayload(dto, old);
        // categoryId=0 是「清空分类」哨兵：局部更新语义下 null 表示不修改，解除分类关联只能靠显式哨兵
        if (dto.getCategoryId() != null && dto.getCategoryId() == 0) {
            productMapper.clearCategory(id, LocalDateTime.now());
            dto.setCategoryId(null); // 置空，避免走常规更新分支和操作日志误报
        }
        // 库存不再允许在商品编辑里手改，必须走库存模块（入库单/出库单/调整单）留流水
        if (dto.getStock() != null && !Objects.equals(old.getStock(), dto.getStock())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                "商品库存不能在此处修改，请到【库存管理 → 入库单/出库单/调整单】操作");
        }
        Product p = new Product();
        p.setId(id);
        applyDto(p, dto);
        // 上下架、改价、改库存都走这一个接口，一次提交只打一条日志，变更字段在这里逐项列出
        recordDiff(old, dto);
        LocalDateTime now = LocalDateTime.now();
        p.setUpdateTime(now);
        productMapper.update(p);
        saveSkus(id, dto, p, old.getSkuSpecs(), now, false);
        categoryCacheEvictor.evictAll();
        // 改商品（含上下架/改价）后：详情与标签立即失效，列表与 feed 顺序也可能变
        productCacheEvictor.evictProduct(id);
        productCacheEvictor.evictList();
        return toVO(productMapper.findByIdAdmin(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "product", action = "PRODUCT_REMOVE", bizId = "#id")
    public void delete(Long id) {
        // 先查一次拿商品名称，日志里要能看出删的是哪件商品
        Product old = productMapper.findByIdAdmin(id);
        // 级联清理：SKU 与规格关联表无外键约束，必须显式删除，否则残留孤儿 SKU
        skuSpecRelMapper.deleteByProductId(id);
        skuMapper.deleteByProductId(id);
        int rows = productMapper.delete(id);
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        if (old != null) {
            LogDetail.add("删除商品：名称【" + old.getName() + "】");
        }
        categoryCacheEvictor.evictAll();
        // 删除后详情/标签与列表/feed 都要失效
        productCacheEvictor.evictProduct(id);
        productCacheEvictor.evictList();
    }

    @Override
    public List<String> brands() {
        return productMapper.listBrands();
    }

    @Override
    public DashboardVO dashboard() {
        return productMapper.dashboardStats();
    }

    /** 把 DTO 的非 null 字段写入 Product（局部更新语义） */
    private void applyDto(Product p, AdminProductDTO dto) {
        if (dto.getName() != null) p.setName(dto.getName());
        if (dto.getSubTitle() != null) p.setSubTitle(dto.getSubTitle());
        if (dto.getBrand() != null) p.setBrand(dto.getBrand());
        if (dto.getTitle() != null) p.setTitle(dto.getTitle());
        if (dto.getImage() != null) p.setImage(dto.getImage());
        if (dto.getBody() != null) p.setBody(dto.getBody());
        if (dto.getCategoryId() != null) p.setCategoryId(dto.getCategoryId());
        if (dto.getSalesMode() != null) p.setSalesMode(dto.getSalesMode());
        if (dto.getListingStatus() != null) p.setListingStatus(dto.getListingStatus());
        if (dto.getSalesPrice() != null) p.setSalesPrice(dto.getSalesPrice());
        if (dto.getMarketPrice() != null) p.setMarketPrice(dto.getMarketPrice());
        if (dto.getCostPrice() != null) p.setCostPrice(dto.getCostPrice());
        if (dto.getProductCode() != null) p.setProductCode(dto.getProductCode());
        if (dto.getStock() != null) p.setStock(dto.getStock());
        // specs 传 null 不修改；传 [] 清空
        if (dto.getSpecs() != null) p.setSpecs(specsCodec.encode(dto.getSpecs()));
        if (dto.getSkuType() != null) p.setSkuType(dto.getSkuType());
        if (dto.getSkuSpecs() != null) p.setSkuSpecs(encodeSpecs(dto.getSkuSpecs()));
    }

    // ==================== SKU ====================

    /**
     * 保存规格维度与 SKU 明细。dto 未涉及规格时直接返回（上架开关等局部更新不动 SKU）。
     * 多规格按 id 差量：无 id 新增、有 id 更新、未提交的旧 SKU 置停用。
     * 写入时同步维护 product_sku_spec_rel 多对多中间表，spec_json/spec_key 仍保留用于 DB 层兜底唯一约束，
     * 但业务逻辑以中间表为准。
     */
    private void saveSkus(Long productId, AdminProductDTO dto, Product product,
                          String existingSpecsJson, LocalDateTime now, boolean createMode) {
        // 编辑时未提交规格字段（如只改上架状态）不动 SKU；新增时即使没传也要补一条默认 SKU
        if (!createMode && dto.getSkuType() == null && dto.getSkuSpecs() == null && dto.getSkus() == null) {
            return;
        }
        int skuType = dto.getSkuType() != null
                ? dto.getSkuType()
                : (product.getSkuType() != null ? product.getSkuType() : 0);
        List<ProductSku> existing = skuMapper.listByProductId(productId);

        if (skuType == 0) {
            saveDefaultSku(productId, dto, product, existing, now);
            return;
        }

        List<SkuSpecDTO> dims = dto.getSkuSpecs() != null ? dto.getSkuSpecs() : decodeSpecDtos(existingSpecsJson);
        if (dims.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "多规格商品必须定义规格维度");
        }
        List<AdminSkuDTO> rows = dto.getSkus();
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "多规格商品至少需要一个 SKU");
        }
        Map<Long, ProductSku> existingById = new LinkedHashMap<>();
        for (ProductSku s : existing) {
            existingById.put(s.getId(), s);
        }

        // === 预查字典：groupName -> {value -> (groupId, valueId, groupSort)} ===
        Map<String, Map<String, SpecDictRef>> dictCache = buildSpecDictCache(dims);

        // === 解析所有行 → 规格组合，校验重复 ===
        // existing 里已有的 rel 集合（Java 内存集合做比对，避免 MySQL 5.7 不支持 VALUES 子查询）
        Map<Long, Set<GroupValuePair>> existingPairsBySku = loadExistingPairsBySku(productId);

        Set<Long> kept = new HashSet<>();
        List<SkuSpecRel> relBatch = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            AdminSkuDTO row = rows.get(i);
            Map<String, String> specJson = row.getSpecJson() == null ? Map.of() : row.getSpecJson();

            // 解析为 (groupId, valueId, value, sort) 列表
            List<SpecTuple> specs = resolveSpecs(specJson, dims, dictCache);
            Set<GroupValuePair> pairSet = specs.stream()
                    .map(t -> new GroupValuePair(t.groupId, t.valueId))
                    .collect(java.util.stream.Collectors.toSet());

            // 先查当前 product_id 下所有 SKU（排除正在编辑的 row.getId()），是否有完全相同组合
            for (Map.Entry<Long, Set<GroupValuePair>> e : existingPairsBySku.entrySet()) {
                Long otherSkuId = e.getKey();
                if (Objects.equals(otherSkuId, row.getId())) continue;
                Set<GroupValuePair> otherPairs = e.getValue();
                if (otherPairs != null && otherPairs.size() == pairSet.size() && otherPairs.containsAll(pairSet)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "存在重复的规格组合（与 SKU ID=" + otherSkuId + " 相同）");
                }
            }
            // 行内去重（dto 自身两行相同）
            for (int j = 0; j < i; j++) {
                Set<GroupValuePair> prev = rows.get(j) != null
                        ? parseRowPairs(rows.get(j), dims, dictCache)
                        : null;
                if (prev != null && prev.size() == pairSet.size() && prev.containsAll(pairSet)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "存在重复的规格组合：行 " + (j + 1) + " 与行 " + (i + 1));
                }
            }

            if (row.getId() != null && !existingById.containsKey(row.getId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "SKU 不属于该商品，无法更新");
            }
            ProductSku target = row.getId() != null ? existingById.get(row.getId()) : new ProductSku();
            target.setProductId(productId);
            // 商家编码：未填则按「商品编码-规格值」自动生成，保证非空
            target.setSkuCode(resolveSkuCode(row.getSkuCode(), product, specs, i));
            // specJson / specKey 已下线，规格选择统一由 product_sku_spec_rel 中间表承载
            target.setPrice(row.getPrice());
            target.setMarketPrice(row.getMarketPrice());
            target.setCostPrice(row.getCostPrice());
            if (row.getId() != null) {
                ProductSku prev = existingById.get(row.getId());
                if (row.getStock() != null && !Objects.equals(prev.getStock(), row.getStock())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "SKU「" + row.getSkuCode() + "」库存不能在此处修改，请到【库存管理 → 入库单/出库单/调整单】操作");
                }
                target.setStock(prev.getStock());
            } else {
                target.setStock(row.getStock() == null ? 0 : row.getStock());
            }
            target.setStatus(row.getStatus() == null ? 1 : row.getStatus());
            // 多规格商品没有默认 SKU；商品类型从单规格切过来时需清掉历史标记
            target.setIsDefault(0);
            target.setSort(i);
            target.setUpdateTime(now);
            if (row.getId() != null) {
                skuMapper.update(target);
                // 先删旧 rel
                skuSpecRelMapper.deleteBySkuId(target.getId());
            } else {
                target.setCreateTime(now);
                skuMapper.insert(target);
            }

            // 准备 rel 记录，INSERT 时先删再插
            for (SpecTuple t : specs) {
                relBatch.add(SkuSpecRel.builder()
                        .skuId(target.getId())
                        .specGroupId(t.groupId)
                        .specValueId(t.valueId)
                        .specValue(t.value)
                        .sort(t.sort)
                        .createTime(now)
                        .build());
            }
            // 行内集合更新（供后续行校验）
            existingPairsBySku.put(target.getId(), pairSet);

            kept.add(target.getId());
        }
        // 批量写 rel（ON DUPLICATE KEY UPDATE 已写好，幂等）
        if (!relBatch.isEmpty()) {
            skuSpecRelMapper.batchInsert(relBatch);
        }

        for (ProductSku s : existing) {
            if (!kept.contains(s.getId()) && Objects.equals(s.getStatus(), 1)) {
                skuMapper.updateStatus(s.getId(), 0);
            }
        }
        skuMapper.refreshProductAggregate(productId, now);
    }

    // ==================== 规格辅助方法 ====================

    /** 规格值字典缓存：groupName → {value → SpecDictRef} */
    private Map<String, Map<String, SpecDictRef>> buildSpecDictCache(List<SkuSpecDTO> dims) {
        Map<String, Map<String, SpecDictRef>> result = new LinkedHashMap<>();
        for (SkuSpecDTO dim : dims) {
            String name = dim.getName();
            if (name == null || name.isBlank()) continue;
            Long gid = dim.getGroupId();
            if (gid == null) {
                // 自定义规格：字典没预建，尝试按 name 建或查
                gid = skuSpecRelMapper.findGroupIdByName(name);
                if (gid == null) {
                    log.warn("规格名 '{}' 在字典中不存在，服务层校验跳过", name);
                }
            }
            Map<String, SpecDictRef> inner = new LinkedHashMap<>();
            if (gid != null) {
                Integer groupSort = skuSpecRelMapper.findGroupSort(gid);
                for (String v : dim.getValues()) {
                    Long vid = skuSpecRelMapper.findValueId(gid, v);
                    if (vid == null) {
                        // 自动补建
                        skuSpecRelMapper.insertSpecValue(gid, v, 999, LocalDateTime.now());
                        vid = skuSpecRelMapper.findValueId(gid, v);
                    }
                    inner.put(v, new SpecDictRef(gid, vid, groupSort == null ? 999 : groupSort));
                }
            }
            result.put(name, inner);
        }
        return result;
    }

    private List<SpecTuple> resolveSpecs(Map<String, String> specJson, List<SkuSpecDTO> dims,
                                         Map<String, Map<String, SpecDictRef>> dictCache) {
        List<SpecTuple> out = new ArrayList<>();
        for (SkuSpecDTO dim : dims) {
            String value = specJson.get(dim.getName());
            if (value == null || value.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "SKU 缺少规格【" + dim.getName() + "】的取值");
            }
            if (!dim.getValues().contains(value)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "规格【" + dim.getName() + "】的值【" + value + "】不在可选值中");
            }
            Map<String, SpecDictRef> inner = dictCache.get(dim.getName());
            if (inner != null) {
                SpecDictRef ref = inner.get(value);
                if (ref != null) {
                    out.add(new SpecTuple(ref.groupId, ref.valueId, value, ref.sort));
                }
            }
        }
        return out;
    }

    private Set<GroupValuePair> parseRowPairs(AdminSkuDTO row, List<SkuSpecDTO> dims,
                                               Map<String, Map<String, SpecDictRef>> dictCache) {
        Map<String, String> specJson = row.getSpecJson() == null ? Map.of() : row.getSpecJson();
        List<SpecTuple> specs = resolveSpecs(specJson, dims, dictCache);
        return specs.stream().map(t -> new GroupValuePair(t.groupId, t.valueId)).collect(java.util.stream.Collectors.toSet());
    }

    private Map<Long, Set<GroupValuePair>> loadExistingPairsBySku(Long productId) {
        List<SkuSpecRel> rels = skuSpecRelMapper.listByProductId(productId);
        Map<Long, Set<GroupValuePair>> out = new LinkedHashMap<>();
        for (SkuSpecRel r : rels) {
            out.computeIfAbsent(r.getSkuId(), k -> new HashSet<>())
                    .add(new GroupValuePair(r.getSpecGroupId(), r.getSpecValueId()));
        }
        return out;
    }

    /** 字典引用 */
    private record SpecDictRef(Long groupId, Long valueId, int sort) {}
    /** 解析中间结构 */
    private record SpecTuple(Long groupId, Long valueId, String value, int sort) {}
    /** 用于重复校验的唯一对 */
    private record GroupValuePair(Long groupId, Long valueId) {}

    /**
     * 生成商家编码（sku_code）。
     * <p>
     * 规则：后台明确填写则用填写值；否则用「商品编码-规格值1-规格值2」自动拼装；
     * 商品编码也为空时退化为「SKU-{商品ID}-{行号}」，保证 sku_code 永不为空
     * （数据库列已改为 NOT NULL）。
     */
    private String resolveSkuCode(String input, Product product, List<SpecTuple> specs, int rowIndex) {
        if (input != null && !input.isBlank()) {
            return input.trim();
        }
        String base = product.getProductCode() != null && !product.getProductCode().isBlank()
                ? product.getProductCode().trim()
                : "SKU" + product.getId();
        if (specs != null && !specs.isEmpty()) {
            String suffix = specs.stream()
                    .sorted(java.util.Comparator.comparingInt(SpecTuple::sort))
                    .map(SpecTuple::value)
                    .filter(v -> v != null && !v.isBlank())
                    .collect(java.util.stream.Collectors.joining("-"));
            if (!suffix.isBlank()) {
                return base + "-" + suffix;
            }
        }
        return base + "-" + (rowIndex + 1);
    }


    /** 单规格：停用其余 SKU，保留/重建一条默认 SKU（由 is_default=1 标记） */
    private void saveDefaultSku(Long productId, AdminProductDTO dto, Product product,
                                List<ProductSku> existing, LocalDateTime now) {
        ProductSku def = existing.stream()
                .filter(s -> Objects.equals(s.getIsDefault(), 1))
                .findFirst().orElse(null);
        AdminSkuDTO row = dto.getSkus() != null && dto.getSkus().size() == 1 ? dto.getSkus().get(0) : null;
        for (ProductSku s : existing) {
            if ((def == null || !Objects.equals(s.getId(), def.getId())) && Objects.equals(s.getStatus(), 1)) {
                skuMapper.updateStatus(s.getId(), 0);
            }
        }
        ProductSku target = def != null ? def : new ProductSku();
        target.setProductId(productId);
        target.setPrice(row != null && row.getPrice() != null ? row.getPrice()
                : (product.getSalesPrice() != null ? product.getSalesPrice() : BigDecimal.ZERO));
        target.setMarketPrice(row != null && row.getMarketPrice() != null ? row.getMarketPrice()
                : product.getMarketPrice());
        target.setCostPrice(row != null && row.getCostPrice() != null ? row.getCostPrice()
                : product.getCostPrice());
        if (def != null) {
            // 已有默认 SKU：库存不允许手改，保持库中原值
            if (row != null && row.getStock() != null && !Objects.equals(def.getStock(), row.getStock())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "SKU 库存不能在此处修改，请到【库存管理 → 入库单/出库单/调整单】操作");
            }
            target.setStock(def.getStock());
        } else {
            // 新建默认 SKU：允许填初始库存
            target.setStock(row != null && row.getStock() != null ? row.getStock()
                    : (product.getStock() != null ? product.getStock() : 0));
        }
        // 商家编码：未填则按「商品编码-1」自动生成，保证非空
        target.setSkuCode(resolveSkuCode(row != null ? row.getSkuCode() : null, product, null, 0));
        target.setStatus(1);
        target.setIsDefault(1);
        target.setSort(0);
        target.setUpdateTime(now);
        if (def != null) {
            skuMapper.update(target);
        } else {
            target.setCreateTime(now);
            skuMapper.insert(target);
        }
    }

    /** 保存前的规格结构校验（不依赖数据库） */
    private void validateSkuPayload(AdminProductDTO dto, Product old) {
        if (dto.getSkuType() == null && dto.getSkuSpecs() == null && dto.getSkus() == null) {
            return;
        }
        int skuType = dto.getSkuType() != null
                ? dto.getSkuType()
                : (old == null ? 0 : (old.getSkuType() == null ? 0 : old.getSkuType()));
        if (skuType == 0 && dto.getSkuSpecs() != null && !dto.getSkuSpecs().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "单规格商品不能提交规格维度");
        }
        if (skuType != 1) {
            return;
        }
        List<SkuSpecDTO> dims = dto.getSkuSpecs() != null ? dto.getSkuSpecs() : decodeSpecDtos(old == null ? null : old.getSkuSpecs());
        if (dims.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "多规格商品必须定义规格维度");
        }
        List<AdminSkuDTO> rows = dto.getSkus();
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "多规格商品至少需要一个 SKU");
        }
        long expected = 1;
        for (SkuSpecDTO dim : dims) {
            List<String> values = dim.getValues();
            if (values == null || values.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "规格【" + dim.getName() + "】至少需要一个值");
            }
            expected *= values.size();
        }
        if (rows.size() != expected) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SKU 数量应为 " + expected + "（规格值数量的乘积）");
        }
        for (AdminSkuDTO row : rows) {
            if (row.getPrice() == null || row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "每个 SKU 的售价必须大于 0");
            }
            if (row.getStock() == null || row.getStock() < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "每个 SKU 的库存不能为空且不能为负");
            }
        }
    }

    private String encodeSpecs(List<SkuSpecDTO> dims) {
        if (dims == null) {
            return null;
        }
        Set<String> names = new HashSet<>();
        for (SkuSpecDTO dim : dims) {
            if (dim == null || dim.getName() == null || dim.getName().isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "规格名不能为空");
            }
            String name = dim.getName().trim();
            if (!names.add(name)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "规格名不能重复：" + name);
            }
            List<String> values = dim.getValues() == null ? List.of() : dim.getValues();
            if (values.isEmpty() || values.size() > MAX_SPEC_VALUES) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "规格【" + name + "】的值数量应为 1~" + MAX_SPEC_VALUES);
            }
            // 用 LinkedHashSet 去重并保留商家录入顺序（顺序决定 SKU 表格行序）
            Set<String> valueSet = new java.util.LinkedHashSet<>();
            for (String v : values) {
                if (v == null || v.isBlank()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "规格【" + name + "】的值不能为空");
                }
                if (!valueSet.add(v.trim())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "规格【" + name + "】的值不能重复");
                }
            }
            dim.setName(name);
            dim.setValues(new ArrayList<>(valueSet));
        }
        try {
            return objectMapper.writeValueAsString(dims);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "规格维度格式不合法");
        }
    }

    private List<SkuSpecVO> decodeSpecs(String json) {
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

    private List<SkuSpecDTO> decodeSpecDtos(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            SkuSpecDTO[] array = objectMapper.readValue(json, SkuSpecDTO[].class);
            return array == null ? List.of() : Arrays.asList(array);
        } catch (Exception e) {
            log.warn("商品 sku_specs 反序列化失败，按空规格处理: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSpecKey(Map<String, String> specJson, List<SkuSpecDTO> dims) {
        List<String> parts = new ArrayList<>();
        for (SkuSpecDTO dim : dims) {
            String value = specJson.get(dim.getName());
            if (value == null || value.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "SKU 缺少规格【" + dim.getName() + "】的取值");
            }
            if (!dim.getValues().contains(value)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "规格【" + dim.getName() + "】的值【" + value + "】不在可选值中");
            }
            parts.add(dim.getName() + ":" + value);
        }
        return String.join("|", parts);
    }

    private String writeJson(Map<String, String> specJson) {
        try {
            return objectMapper.writeValueAsString(specJson);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SKU 规格格式不合法");
        }
    }

    private SkuVO toSkuVO(ProductSku s, List<SkuSpecRel> rels) {
        SkuVO vo = new SkuVO();
        vo.setId(s.getId());
        vo.setSpecJson(buildSpecJsonFromRel(rels));
        vo.setPrice(s.getPrice());
        vo.setMarketPrice(s.getMarketPrice());
        vo.setCostPrice(s.getCostPrice());
        vo.setStock(s.getStock());
        vo.setImage(s.getImage());
        vo.setSkuCode(s.getSkuCode());
        vo.setStatus(s.getStatus());
        return vo;
    }

    /** 从 rel 聚合成 Map<groupName, value>；rels 为空返回空 Map（单规格 SKU） */
    private Map<String, String> buildSpecJsonFromRel(List<SkuSpecRel> rels) {
        if (rels == null || rels.isEmpty()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (SkuSpecRel r : rels) {
            if (r.getGroupName() != null && r.getSpecValue() != null) {
                out.put(r.getGroupName(), r.getSpecValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseSpecJson(String json) {
        // 仅在 AdminSkuDTO 反序列化时用到，保留签名但不再读 DB 字段
        if (json == null || json.isBlank()) return Map.of();
        try {
            return (Map<String, String>) objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("SKU spec_json 反序列化失败，按空规格处理: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 记录商品变更内容（写进 operation_log.detail）。
     * <p>
     * 上下架（listingStatus）、改价（salesPrice）、改库存（stock）都在 update 一个接口里，
     * 一次提交只打一条日志，这里按「实际变了哪些字段」逐项列出。
     * <p>
     * DTO 里为 null 的字段按 applyDto 的语义是「不修改」，所以不参与比较，
     * 避免出现「从【xxx】修改为【空】」这种没提交过的假变更。
     * <p>
     * LogDiff.of 在新旧值相同时返回 null，LogDetail.add 会自动忽略，值没变就不记。
     */
    private void recordDiff(Product old, AdminProductDTO dto) {
        if (dto.getName() != null) {
            LogDetail.add(LogDiff.of("商品名称", old.getName(), dto.getName()));
        }
        if (dto.getSubTitle() != null) {
            LogDetail.add(LogDiff.of("副标题", old.getSubTitle(), dto.getSubTitle()));
        }
        if (dto.getBrand() != null) {
            LogDetail.add(LogDiff.of("品牌", old.getBrand(), dto.getBrand()));
        }
        if (dto.getTitle() != null) {
            LogDetail.add(LogDiff.of("主标题", old.getTitle(), dto.getTitle()));
        }
        if (dto.getImage() != null) {
            LogDetail.add(LogDiff.of("主图", old.getImage(), dto.getImage()));
        }
        if (dto.getBody() != null && !Objects.equals(old.getBody(), dto.getBody())) {
            // 图文详情是富文本，整段塞进日志会超长（detail 只有 1024），只记「改过」
            LogDetail.add("图文详情已修改");
        }
        if (dto.getCategoryId() != null) {
            LogDetail.add(LogDiff.of("所属分类", old.getCategoryId(), dto.getCategoryId()));
        }
        if (dto.getSalesMode() != null) {
            LogDetail.add(LogDiff.of("销售模式", old.getSalesMode(), dto.getSalesMode()));
        }
        if (dto.getListingStatus() != null) {
            LogDetail.add(LogDiff.of("上架状态", old.getListingStatus(), dto.getListingStatus(),
                    AdminProductServiceImpl::listingStatusText));
        }
        // 价格是 BigDecimal，先按数值比较，199 与 199.00 是同一个价，不该记成变更
        if (dto.getSalesPrice() != null && !sameAmount(old.getSalesPrice(), dto.getSalesPrice())) {
            LogDetail.add(LogDiff.of("销售价", old.getSalesPrice(), dto.getSalesPrice()));
        }
        if (dto.getMarketPrice() != null && !sameAmount(old.getMarketPrice(), dto.getMarketPrice())) {
            LogDetail.add(LogDiff.of("市场价", old.getMarketPrice(), dto.getMarketPrice()));
        }
        if (dto.getCostPrice() != null && !sameAmount(old.getCostPrice(), dto.getCostPrice())) {
            LogDetail.add(LogDiff.of("成本价", old.getCostPrice(), dto.getCostPrice()));
        }
        if (dto.getProductCode() != null) {
            LogDetail.add(LogDiff.of("商品编码", old.getProductCode(), dto.getProductCode()));
        }
        if (dto.getStock() != null) {
            LogDetail.add(LogDiff.of("库存", old.getStock(), dto.getStock()));
        }
        if (dto.getSpecs() != null) {
            // 规格是 JSON 数组，不整段进日志，只记规格项数量
            LogDetail.add(LogDiff.of("规格项数量",
                    specsCodec.decode(old.getSpecs()).size(), dto.getSpecs().size()));
        }
    }

    /**
     * 金额比较：BigDecimal 用数值比较而不是 Objects.equals，
     * 否则 199 与 199.00 会被误判成「改过价」。
     */
    private static boolean sameAmount(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || newValue == null) {
            return Objects.equals(oldValue, newValue);
        }
        return oldValue.compareTo(newValue) == 0;
    }

    /** 上架状态翻译：1 已上架 / 0 已下架 */
    private static String listingStatusText(Object value) {
        if (value instanceof Integer status) {
            return status == 1 ? "已上架" : "已下架";
        }
        return String.valueOf(value);
    }

    private AdminProductVO toVO(Product p, boolean withSkus) {
        List<SkuVO> skus = List.of();
        if (withSkus) {
            List<ProductSku> raw = skuMapper.listByProductId(p.getId());
            List<SkuSpecRel> allRels = skuSpecRelMapper.listByProductId(p.getId());
            Map<Long, List<SkuSpecRel>> relsBySku = allRels.stream()
                    .collect(java.util.stream.Collectors.groupingBy(SkuSpecRel::getSkuId));
            skus = raw.stream()
                    .map(s -> toSkuVO(s, relsBySku.getOrDefault(s.getId(), List.of())))
                    .toList();
        }
        return AdminProductVO.builder()
                .id(p.getId())
                .name(p.getName())
                .subTitle(p.getSubTitle())
                .brand(p.getBrand())
                .title(p.getTitle())
                .image(p.getImage())
                .body(p.getBody())
                .categoryId(p.getCategoryId())
                .tagId(p.getTagId())
                .salesMode(p.getSalesMode())
                .listingStatus(p.getListingStatus())
                .salesPrice(p.getSalesPrice())
                .marketPrice(p.getMarketPrice())
                .costPrice(p.getCostPrice())
                .productCode(p.getProductCode())
                .stock(p.getStock())
                .specs(specsCodec.decode(p.getSpecs()))
                .skuType(p.getSkuType() == null ? 0 : p.getSkuType())
                .skuSpecs(decodeSpecs(p.getSkuSpecs()))
                .skus(skus)
                // 后台商品预览弹窗也展示增值保障，与 C 端同一套规则
                .insuranceOptions(InsurancePolicy.options(p.getCategoryId(), p.getSalesPrice()))
                .createdAt(p.getCreateTime())
                .updatedAt(p.getUpdateTime())
                .build();
    }
}
