package ecommerce.cache;

import java.time.Duration;

/**
 * Redis 缓存 TTL 统一管理。
 * <p>
 * 所有过期时间集中在这里，修改 TTL 只需改这一个文件。
 */
public final class CacheTtl {

    private CacheTtl() {
    }

    // ==================== C 端用户 ====================

    /** 短信验证码有效期 */
    public static final Duration SMS_CODE = Duration.ofMinutes(5);
    /** 短信发送限频间隔 */
    public static final Duration SMS_LIMIT = Duration.ofSeconds(60);
    /** 下单幂等键存活时长（覆盖待付款超时 30 分钟及用户中途退出重进） */
    public static final Duration ORDER_IDEMPOTENCY = Duration.ofHours(24);

    // ==================== 后台管理端 ====================

    /** 图形验证码有效期 */
    public static final Duration CAPTCHA = Duration.ofMinutes(5);
    /** 登录失败锁定时间（也可通过 auth.login.lock-minutes 配置覆盖） */
    public static final Duration LOGIN_LOCK = Duration.ofMinutes(10);
    /** 在线会话活跃时间节流间隔 */
    public static final Duration ONLINE_TOUCH = Duration.ofMinutes(10);

    // ==================== 公共服务 ====================

    /** 分类缓存过期时长（分类靠 SQL 导入、变更不频繁，TTL 到期自动回源刷新） */
    public static final Duration CATEGORY = Duration.ofMinutes(30);
    /**
     * 商品详情过期时长。库存变动由 StockWriteService 精确失效，这里主要兜住
     * 「促销活动到期」这类时间事件（无法主动删缓存），所以给短一点。
     */
    public static final Duration PRODUCT_DETAIL = Duration.ofMinutes(2);
    /** 商品标签：与详情同生命周期 */
    public static final Duration PRODUCT_TAGS = Duration.ofMinutes(2);
    /** 商品不存在时的空值缓存（防穿透，必须短，避免新商品上架后仍被判不存在） */
    public static final Duration PRODUCT_NULL = Duration.ofMinutes(2);
    /**
     * 上架商品全量列表。一个 key 代表全部商品，不能随库存变动失效（否则每来一单就重建），
     * 只能靠短 TTL 控制库存/价格偏差。
     */
    public static final Duration PRODUCT_ONSALE_ALL = Duration.ofSeconds(60);
    /** 首页 feed 的 id 顺序：60 秒换一批推荐，同时保证同一批分页过程中顺序稳定 */
    public static final Duration HOME_FEED_IDS = Duration.ofSeconds(60);
    /** 微信 token 刷新锁存活时长 */
    public static final Duration WECHAT_LOCK = Duration.ofSeconds(10);

    // ==================== 监控/系统 ====================

    /** 定时任务执行记录保留时长 */
    public static final Duration MONITOR_TASK = Duration.ofDays(7);
    /** 缓存监控临时引用有效期 */
    public static final Duration MONITOR_CACHE_REF = Duration.ofMinutes(10);
    /** 服务器告警开始时间记录保留时长 */
    public static final Duration MONITOR_SERVER_ALERT = Duration.ofDays(1);
    /** QPS 采样数据保留时长 */
    public static final Duration MONITOR_DB_QPS = Duration.ofDays(1);
}
