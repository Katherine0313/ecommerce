package ecommerce.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交 / 幂等注解。
 * <p>
 * 语义分两种，由 {@link #cacheResult()} 决定：
 * <ul>
 *   <li><b>防重复提交</b>（默认）：窗口内重复请求直接抛 409，适合后台审核、发券这类「重复执行没有意义」的写接口。</li>
 *   <li><b>结果回放</b>（cacheResult=true）：第一次成功的结果会被缓存，同一 key 的重复请求直接返回这份结果，
 *       适合下单这类「客户端超时重试时，必须知道上一单到底成没成」的接口。</li>
 * </ul>
 *
 * <p>key 规则：{@link #prefix()} + 当前用户 ID + {@link #key()}（SpEL）或「方法签名 + 参数摘要」。
 * 若要兼容已有 key 格式，{@link #prefix()} 可以直接传带结尾冒号的常量，切面不会重复拼接。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Redis key 前缀，建议按业务命名（如 order:idempotency）。
     * 以冒号结尾时切面不再补冒号，方便复用 {@code CacheKeys} 里的常量。
     */
    String prefix() default "idempotent";

    /**
     * SpEL 表达式，用来从方法参数里取业务唯一键，如 {@code "#dto.idempotencyKey"}。
     * 为空时退化为「方法签名 + 参数 JSON 摘要」。
     */
    String key() default "";

    /** 占位有效期，与 {@link #timeUnit()} 配合使用 */
    int timeout() default 5;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 重复请求时的提示文案 */
    String message() default "请勿重复提交";

    /** key 是否拼接当前登录用户 ID，默认拼接，避免不同用户同参数互相误伤 */
    boolean includeUser() default true;

    /** 业务抛异常时是否释放 key，默认释放，允许用户修正后重试 */
    boolean deleteKeyWhenException() default true;

    /** Redis 故障时是否放行，默认放行，避免缓存不可用拖垮主流程 */
    boolean failOpen() default true;

    /** 是否缓存并回放第一次的返回值（真正的幂等）；要求方法有具体返回值类型 */
    boolean cacheResult() default false;
}
