package com.macro.mall.distribution.security;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.service.PayloadEncryptionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;

/**
 * 对请求体加密提供第二道服务器端强制校验。
 *
 * <p>即使请求体 Advice 因配置或部署问题没有生效，只要控制器收到含敏感字段的请求，
 * 这里仍会在业务方法执行前拒绝未完成解密的载荷，避免客户端自行降级为明文请求。</p>
 */
@Aspect
@Component
// 必须排在 Spring 的 ExposeInvocationInterceptor 之后，否则无法取得 JoinPoint。
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class EncryptedPayloadEnforcementAspect {

    private final PayloadEncryptionService payloadEncryptionService;
    private final HttpServletRequest request;

    @Before("execution(public * com.macro.mall.distribution.controller..*(..))")
    public void requireEncryptedSensitivePayload(JoinPoint joinPoint) {
        if (request.getRequestURI().startsWith("/distribution/erp/callbacks/")) return;

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] arguments = joinPoint.getArgs();
        for (int index = 0; index < parameterAnnotations.length && index < arguments.length; index++) {
            if (!isRequestBody(parameterAnnotations[index])) continue;
            if (!payloadEncryptionService.hasSensitiveValue(arguments[index])) continue;
            if (!Boolean.TRUE.equals(request.getAttribute(
                    EncryptedPayloadRequestBodyAdvice.DECRYPTED_PAYLOAD_ATTRIBUTE))) {
                String challengeId = request.getHeader(EncryptedPayloadRequestBodyAdvice.CHALLENGE_HEADER);
                String encryptedKey = request.getHeader(EncryptedPayloadRequestBodyAdvice.ENCRYPTED_KEY_HEADER);
                if (challengeId == null || challengeId.isBlank() || encryptedKey == null || encryptedKey.isBlank()) {
                    Asserts.fail("检测到未加密的敏感信息，请刷新页面后重试");
                }
                // Advice 是主解密路径；这里同时作为独立兜底，避免 Advice 因代理或配置问题被跳过。
                payloadEncryptionService.decryptSensitiveValues(challengeId, encryptedKey, arguments[index]);
                request.setAttribute(EncryptedPayloadRequestBodyAdvice.DECRYPTED_PAYLOAD_ATTRIBUTE, Boolean.TRUE);
            }
        }
    }

    private boolean isRequestBody(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == RequestBody.class) return true;
        }
        return false;
    }
}
