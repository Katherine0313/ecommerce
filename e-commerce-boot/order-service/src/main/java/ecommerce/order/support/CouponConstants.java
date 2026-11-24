package ecommerce.order.support;

/**
 * 优惠券常量：类型 / 范围 / 人群 / 有效期 / 模板状态 / 用户券状态。
 */
public final class CouponConstants {

    /** 满减券 */
    public static final String TYPE_FIXED = "FIXED";
    /** 折扣券 */
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_CATEGORY = "CATEGORY";
    public static final String SCOPE_PRODUCT = "PRODUCT";

    public static final String TARGET_ALL = "ALL";
    public static final String TARGET_NEW_USER = "NEW_USER";

    public static final String VALID_FIXED = "FIXED";
    public static final String VALID_RECEIVE_DAYS = "RECEIVE_DAYS";

    /** 模板状态（读时推导） */
    public static final String TEMPLATE_PENDING = "PENDING";
    public static final String TEMPLATE_ACTIVE = "ACTIVE";
    public static final String TEMPLATE_ENDED = "ENDED";
    public static final String TEMPLATE_STOPPED = "STOPPED";

    /** 用户券状态 */
    public static final String USER_UNUSED = "UNUSED";
    public static final String USER_LOCKED = "LOCKED";
    public static final String USER_USED = "USED";

    public static final String SOURCE_RECEIVE = "RECEIVE";
    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_SECKILL = "SECKILL";

    /** 秒杀券已退款（用户券状态） */
    public static final String USER_REFUNDED = "REFUNDED";

    /** 发放方式：普通领取 / 秒杀购买 */
    public static final String ISSUE_RECEIVE = "RECEIVE";
    public static final String ISSUE_SECKILL = "SECKILL";

    /** 秒杀订单状态 */
    public static final int SECKILL_STATUS_PENDING = 1;
    public static final int SECKILL_STATUS_PAID = 2;
    public static final int SECKILL_STATUS_CANCELED = 3;
    public static final int SECKILL_STATUS_REFUNDED = 4;

    /** 秒杀券活动阶段（读时推导） */
    public static final String ACTIVITY_NOT_STARTED = "NOT_STARTED";
    public static final String ACTIVITY_ACTIVE = "ACTIVE";
    public static final String ACTIVITY_ENDED = "ENDED";
    public static final String ACTIVITY_STOPPED = "STOPPED";

    /** 退款单状态 */
    public static final String REFUND_REVIEWING = "REVIEWING";
    public static final String REFUND_APPROVING = "APPROVING";
    public static final String REFUND_REFUNDED = "REFUNDED";
    public static final String REFUND_REJECTED = "REJECTED";

    private CouponConstants() {
    }
}
