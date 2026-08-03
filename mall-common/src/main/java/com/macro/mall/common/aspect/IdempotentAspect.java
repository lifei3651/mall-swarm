package com.macro.mall.common.aspect;

import com.macro.mall.common.annotation.Idempotent;
import com.macro.mall.common.exception.Asserts;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class IdempotentAspect {
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final StringRedisTemplate redisTemplate;

    public IdempotentAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return joinPoint.proceed();
        HttpServletRequest request = attributes.getRequest();
        String clientKey = normalizeClientKey(request.getHeader(IDEMPOTENCY_HEADER));
        String authorizationDigest = sha256(String.valueOf(request.getHeader("Authorization")));
        String fingerprint = request.getMethod() + "|" + request.getRequestURI() + "|"
                + authorizationDigest + "|" + (clientKey == null ? "fallback" : clientKey);
        String key = "idempotent:" + sha256(fingerprint);
        Boolean exists = redisTemplate.opsForValue().setIfAbsent(key, "1", idempotent.timeout(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(exists)) {
            Asserts.fail(idempotent.message());
        }
        try {
            return joinPoint.proceed();
        } catch (Throwable error) {
            // 业务事务失败时允许使用同一请求号安全重试；成功请求保留到超时自动失效。
            try {
                redisTemplate.delete(key);
            } catch (RuntimeException ignored) {
                // 清理失败不能覆盖原始业务异常，键会在短时间后自动过期。
            }
            throw error;
        }
    }

    private String normalizeClientKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{8,128}")) {
            Asserts.fail("请求唯一编号格式错误");
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }
}
