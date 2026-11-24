package ecommerce.order.mapper;

import ecommerce.order.entity.CouponScope;
import ecommerce.order.entity.Coupon;
import ecommerce.order.entity.UserCoupon;
import ecommerce.order.vo.CouponRecordVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 优惠券 Mapper（coupon / coupon_scope / coupon_user）。
 * <p>
 * 时间条件统一由 Java 传入 now，不使用 MySQL NOW()（服务器时区 UTC）。
 */
public interface CouponMapper {

    // ==================== C 端 ====================

    // 领券中心：未删除、未停止、now 在领取窗口内、还有剩余
    List<Coupon> listCenter(@Param("now") LocalDateTime now);

    // 商品详情页「国家补贴」横幅：取名称含「国家补贴」、正在发放且适用于该商品的模板
    Coupon findProductSubsidy(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    // 某用户已领取的模板 id（领券中心标记已领取）
    List<Long> listClaimedTemplateIds(@Param("userId") Long userId);

    Coupon findTemplateById(@Param("id") Long id);

    List<CouponScope> listScopesByTemplateIds(@Param("templateIds") List<Long> templateIds);

    UserCoupon findUserCouponById(@Param("id") Long id);

    UserCoupon findUserCouponByUserAndTemplate(@Param("userId") Long userId,
                                               @Param("templateId") Long templateId);

    int insertUserCoupon(UserCoupon userCoupon);

    // 用户全部券（状态/过期在 Java 里推导）
    List<UserCoupon> listUserCoupons(@Param("userId") Long userId);

    // 可用券：未使用且未过期
    List<UserCoupon> listAvailableUserCoupons(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<UserCoupon> listUserCouponsByOrder(@Param("orderId") Long orderId);

    // 下单锁定：UNUSED -> LOCKED（带状态条件，防并发重复使用）
    int lockCoupon(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 锁定后回填订单 id
    int bindOrder(@Param("id") Long id, @Param("orderId") Long orderId);

    // 支付成功：LOCKED -> USED
    int markUsedByOrder(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    // 取消/超时：LOCKED -> UNUSED
    int releaseLockedByOrder(@Param("orderId") Long orderId);

    // 整单退款：USED -> UNUSED（仅未过期）
    int releaseUsedByOrder(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    // 商品所属分类及父分类（判断分类券适用范围）
    List<Map<String, Object>> listProductCategories(@Param("productIds") List<Long> productIds);

    // ==================== 后台管理 ====================

    List<Coupon> listAdmin(@Param("keyword") String keyword, @Param("status") String status,
                                   @Param("issueType") String issueType,
                                   @Param("now") LocalDateTime now,
                                   @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countAdmin(@Param("keyword") String keyword, @Param("status") String status,
                    @Param("issueType") String issueType,
                    @Param("now") LocalDateTime now);

    List<CouponScope> listScopes(@Param("templateId") Long templateId);

    int insertTemplate(Coupon template);

    int updateTemplate(Coupon template);

    int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);

    int stopNow(@Param("id") Long id, @Param("now") LocalDateTime now);

    int deleteScopes(@Param("templateId") Long templateId);

    int insertScopes(@Param("scopes") List<CouponScope> scopes);

    long countIssued(@Param("templateId") Long templateId);

    long countUsed(@Param("templateId") Long templateId);

    BigDecimal sumUsedDiscount(@Param("templateId") Long templateId);

    // 券领取/使用记录（后台查看）
    List<CouponRecordVO> listRecords(@Param("templateId") Long templateId,
                                     @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countRecords(@Param("templateId") Long templateId);

    // 全部有效用户 id（后台全量发券）
    List<Long> listActiveUserIds();

    // 校验定向发券的目标用户是否存在（防止把券发给不存在的用户 id，用户端永远看不到）
    List<Long> listExistingUserIds(@Param("ids") List<Long> ids);

    // ==================== 秒杀 ====================

    // 未删除的秒杀券列表
    List<Coupon> listSeckill(@Param("offset") int offset, @Param("pageSize") int pageSize);

    long countSeckill();

    Coupon findSeckillById(@Param("id") Long id);

    // 库存与版本：只取 remain_quantity / version
    Coupon findStock(@Param("couponId") Long couponId);

    // 乐观锁扣减：影响 0 行表示库存不足或版本被并发修改
    int decreaseSeckillStock(@Param("couponId") Long couponId, @Param("version") Integer version);

    // 取消/超时回补：同样递增版本号
    int increaseSeckillStock(@Param("couponId") Long couponId);

    // 退款：用户券 UNUSED -> REFUNDED
    int refundUserCoupon(@Param("id") Long id);

    // 该秒杀券是否已有支付/已退款订单（有则只能停止，不能删除）
    long countPaidSeckillOrders(@Param("couponId") Long couponId);
}
