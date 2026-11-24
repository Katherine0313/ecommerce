package ecommerce.cache;

import java.time.Duration;

/**
 * Redis key 前缀与 TTL 统一管理。
 * <p>
 * 所有缓存 key 和过期时间集中在这里，避免散落在各个 Service 中硬编码。
 * 修改 TTL 只需改这一个文件。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    // ==================== C 端用户 ====================

    /** 短信验证码：pc:auth:code:{phone} */
    public static final String SMS_CODE = "pc:auth:code:";
    /** 短信发送限频：pc:auth:code:limit:{phone} */
    public static final String SMS_LIMIT = "pc:auth:code:limit:";
    /** 下单幂等（也作为 @Idempotent 的 prefix）：order:idempotency:{userId}:{idempotencyKey} */
    public static final String ORDER_IDEMPOTENCY = "order:idempotency:";

    // ==================== 后台管理端 ====================

    /** 图形验证码：auth:captcha:{captchaId} */
    public static final String CAPTCHA = "auth:captcha:";
    /** 登录失败计数：auth:login:fail:{username}:{ip} */
    public static final String LOGIN_FAIL = "auth:login:fail:";
    /** 管理员在线会话：auth:online:{adminId}:{sid} */
    public static final String ONLINE_SESSION = "auth:online:";
    /** 活跃时间节流：auth:online:touch:{sid} */
    public static final String ONLINE_TOUCH = "auth:online:touch:";
    /** 令牌失效标记：auth:admin:invalid-before:{adminId} */
    public static final String TOKEN_INVALID = "auth:admin:invalid-before:";

    // ==================== 公共服务 ====================

    /** 一级分类 id 列表：category:parent:ids */
    public static final String CATEGORY_PARENT_IDS = "category:parent:ids";
    /** 分类树缓存：category:parent:{parentId} */
    public static final String CATEGORY_PARENT = "category:parent:";
    /** 商品详情缓存：product:detail:{productId}（值为 "NULL" 表示商品不存在，防缓存穿透） */
    public static final String PRODUCT_DETAIL = "product:detail:";
    /** 商品标签缓存：product:tags:{productId} */
    public static final String PRODUCT_TAGS = "product:tags:";
    /** 上架商品全量列表（PC 首页一次拉全量用）：product:onsale:all */
    public static final String PRODUCT_ONSALE_ALL = "product:onsale:all";
    /** 首页 feed 的商品 id 顺序（推荐置顶 + 其余去重），保证分页稳定：home:feed:ids */
    public static final String HOME_FEED_IDS = "home:feed:ids";
    /** 微信 access_token：wechat:access_token:{appid} */
    public static final String WECHAT_TOKEN = "wechat:access_token:";
    /** 微信 token 刷新锁：wechat:access_token:lock:{appid} */
    public static final String WECHAT_LOCK = "wechat:access_token:lock:";

    // ==================== 监控/系统 ====================

    /** 定时任务执行记录：monitor:task:last:{name} */
    public static final String MONITOR_TASK = "monitor:task:last:";
    /** 缓存监控临时引用：monitor:cache:keyref:{token} */
    public static final String MONITOR_CACHE_REF = "monitor:cache:keyref:";
    /** 服务器告警开始时间：monitor:server:alert_since:{type} */
    public static final String MONITOR_SERVER_ALERT = "monitor:server:alert_since:";
    /** QPS 采样：monitor:db:qps */
    public static final String MONITOR_DB_QPS = "monitor:db:qps";
}
