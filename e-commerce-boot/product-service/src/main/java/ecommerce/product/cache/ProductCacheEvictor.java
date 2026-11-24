package ecommerce.product.cache;

import ecommerce.cache.CacheKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品缓存清理：后台改商品、库存变动后调用。
 * <p>
 * 分两类：
 * <ul>
 *   <li>{@link #evictProduct(Long)}：只删单个商品的详情与标签，库存变动走这里（精确、便宜）</li>
 *   <li>{@link #evictList()}：删全量列表与首页 feed 顺序，商品增删改时调用</li>
 * </ul>
 * 列表缓存不随库存变动删除：一个 key 代表全部商品，每来一单就删会导致缓存永远重建。
 * Redis 故障只记日志，不影响业务写操作。
 */
@Slf4j
@Component
public class ProductCacheEvictor {

    private final StringRedisTemplate stringRedisTemplate;

    public ProductCacheEvictor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 删单个商品的详情与标签缓存（库存变动用这个，不碰列表） */
    public void evictProduct(Long productId) {
        if (productId == null) {
            return;
        }
        List<String> keys = new ArrayList<>(2);
        keys.add(CacheKeys.PRODUCT_DETAIL + productId);
        keys.add(CacheKeys.PRODUCT_TAGS + productId);
        delete(keys);
    }

    /** 删全量列表 + 首页 feed 顺序（商品增删改、上下架时调用） */
    public void evictList() {
        delete(List.of(CacheKeys.PRODUCT_ONSALE_ALL, CacheKeys.HOME_FEED_IDS));
    }

    private void delete(List<String> keys) {
        try {
            stringRedisTemplate.delete(keys);
        } catch (RuntimeException e) {
            log.warn("商品缓存清理失败，忽略: {}", e.getMessage());
        }
    }
}
