package com.macro.mall.common.aspect;

import com.macro.mall.common.annotation.Idempotent;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.idempotency.IdempotencyStore;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotentAspect {
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    public static final String PRINCIPAL_ATTRIBUTE = IdempotentAspect.class.getName() + ".principal";

    private final IdempotencyStore idempotencyStore;

    public IdempotentAspect(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return joinPoint.proceed();
        HttpServletRequest request = attributes.getRequest();
        String clientKey = normalizeClientKey(request.getHeader(IDEMPOTENCY_HEADER));
        if (clientKey == null) Asserts.fail("请求唯一编号不能为空，请刷新页面后重试");
        Object stablePrincipal = request.getAttribute(PRINCIPAL_ATTRIBUTE);
        String principalScope = stablePrincipal == null || String.valueOf(stablePrincipal).isBlank()
                ? "token:" + sha256(String.valueOf(request.getHeader("Authorization")))
                : "principal:" + stablePrincipal;
        String fingerprint = request.getMethod() + "|" + request.getRequestURI() + "|"
                + principalScope + "|" + clientKey;
        String key = sha256(fingerprint);
        if (!idempotencyStore.tryAcquire(key)) {
            Asserts.fail(idempotent.message());
        }
        Object result;
        try {
            // 幂等切面排在事务切面外层；proceed 返回时，内部业务事务已经成功提交。
            result = joinPoint.proceed();
        } catch (Throwable error) {
            // 明确抛出的业务失败允许同一请求号重试；进程崩溃不会执行此删除，记录会保留待核对。
            try {
                idempotencyStore.releaseFailed(key);
            } catch (RuntimeException ignored) {
                // 清理失败不能覆盖原始业务异常；保留处理中记录比重复执行资金操作更安全。
            }
            throw error;
        }
        // 业务已经提交后，如完成标记失败必须保留 PROCESSING，绝不能删除后放开重复资金操作。
        idempotencyStore.markSucceeded(key);
        return result;
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
