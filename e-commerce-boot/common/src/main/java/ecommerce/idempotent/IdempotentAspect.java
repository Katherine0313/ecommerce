package ecommerce.idempotent;

import ecommerce.exception.BusinessException;
import ecommerce.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * {@link Idempotent} 的实现切面：Redis 三态 + 结果回放。
 * <p>
 * 三种状态：
 * <ul>
 *   <li>key 不存在 —— 首次请求，SETNX 占位后执行；</li>
 *   <li>key = PENDING —— 已有请求在处理，结果未知，抛 409；</li>
 *   <li>key = 结果 JSON —— 首次已成功，直接回放（仅 cacheResult=true）。</li>
 * </ul>
 * <p>
 * 切面排在事务外层（{@link Ordered#HIGHEST_PRECEDENCE}）：只有事务提交成功后才会缓存结果，
 * 提交阶段失败也会走到 catch 里释放 key，不会把用户卡在「处理中」。
 * <p>
 * Redis 异常一律降级（failOpen=true 时放行），保证缓存不可用不影响主流程。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdempotentAspect {

    /** 占位值：结果尚未产生。与改造前 OrderServiceImpl 里的值保持一致，方便灰度期间新旧实例共存 */
    private static final String PENDING = "PENDING";

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotentAspect(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        String key = buildKey(point, idempotent);

        // 1. 已有记录：处理中抛冲突，已成功则回放结果
        String cached = safeGet(key);
        if (cached != null) {
            if (PENDING.equals(cached)) {
                throw new BusinessException(HttpStatus.CONFLICT, idempotent.message());
            }
            if (idempotent.cacheResult()) {
                return readResult(point, cached);
            }
            throw new BusinessException(HttpStatus.CONFLICT, idempotent.message());
        }

        // 2. 原子占位：并发下同一 key 只有第一个请求能进来
        if (!safeSetIfAbsent(key, idempotent)) {
            throw new BusinessException(HttpStatus.CONFLICT, idempotent.message());
        }

        Object result;
        try {
            result = point.proceed();
        } catch (Throwable e) {
            if (idempotent.deleteKeyWhenException()) {
                safeDelete(key);
            }
            throw e;
        }

        // 3. 成功后写回结果，重复请求直接回放
        if (idempotent.cacheResult()) {
            try {
                safeSet(key, objectMapper.writeValueAsString(result), idempotent);
            } catch (Exception e) {
                // 业务已提交成功，缓存失败只能记日志，不能让请求失败
                log.warn("幂等结果缓存失败，忽略: key={}", key, e);
            }
        }
        return result;
    }

    private Object readResult(ProceedingJoinPoint point, String cached) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        return objectMapper.readValue(cached, method.getReturnType());
    }

    private String buildKey(ProceedingJoinPoint point, Idempotent idempotent) {
        String biz = resolveBiz(point, idempotent);
        String prefix = idempotent.prefix();
        StringBuilder key = new StringBuilder(prefix);
        if (!prefix.endsWith(":")) {
            key.append(':');
        }
        if (idempotent.includeUser()) {
            Long userId = UserContext.getUserId();
            key.append(userId == null ? "anon" : userId).append(':');
        }
        return key.append(biz).toString();
    }

    private String resolveBiz(ProceedingJoinPoint point, Idempotent idempotent) {
        if (idempotent.key().isBlank()) {
            return point.getSignature().toShortString() + ':' + argsDigest(point.getArgs());
        }
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                point.getTarget(), method, point.getArgs(), NAME_DISCOVERER);
        Object value = PARSER.parseExpression(idempotent.key()).getValue(context);
        return value == null ? "null" : String.valueOf(value);
    }

    /** 参数摘要：跳过 Servlet / 上传对象，其余按 JSON 序列化后取 MD5 */
    private String argsDigest(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                continue;
            }
            if (arg != null && arg.getClass().getName().startsWith("org.springframework.web.multipart.")) {
                continue;
            }
            try {
                sb.append(arg == null ? "null" : objectMapper.writeValueAsString(arg));
            } catch (Exception e) {
                // 序列化不了就退化为类名，至少保证 key 稳定
                sb.append(arg.getClass().getName());
            }
            sb.append('|');
        }
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("幂等键读取失败，降级跳过幂等校验: key={}", key, e);
            return null;
        }
    }

    private boolean safeSetIfAbsent(String key, Idempotent idempotent) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(key, PENDING, idempotent.timeout(), idempotent.timeUnit()));
        } catch (Exception e) {
            if (idempotent.failOpen()) {
                log.warn("幂等键占位失败，降级放行: key={}", key, e);
                return true;
            }
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "幂等校验暂不可用，请稍后重试");
        }
    }

    private void safeSet(String key, String value, Idempotent idempotent) {
        try {
            redisTemplate.opsForValue().set(key, value, idempotent.timeout(), idempotent.timeUnit());
        } catch (Exception e) {
            log.warn("幂等结果写入失败，忽略: key={}", key, e);
        }
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("幂等键释放失败，忽略: key={}", key, e);
        }
    }
}
