package com.macro.mall.distribution.security;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.service.PayloadEncryptionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

// 不按控制器包筛选：控制器启用 AOP 代理后，包筛选可能无法匹配代理类型，导致 Advice 被跳过。
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class EncryptedPayloadRequestBodyAdvice extends RequestBodyAdviceAdapter {

    public static final String CHALLENGE_HEADER = "X-Payload-Encryption-Id";
    public static final String ENCRYPTED_KEY_HEADER = "X-Payload-Encryption-Key";
    public static final String DECRYPTED_PAYLOAD_ATTRIBUTE =
            EncryptedPayloadRequestBodyAdvice.class.getName() + ".decrypted";

    private final PayloadEncryptionService payloadEncryptionService;
    private final HttpServletRequest servletRequest;

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!payloadEncryptionService.hasSensitiveValue(body)) return body;

        // 某些 Servlet/MessageConverter 包装会遗失 HttpInputMessage 中的自定义头，
        // 以原始 HttpServletRequest 为准，并保留 inputMessage 作兼容回退。
        String challengeId = firstText(servletRequest.getHeader(CHALLENGE_HEADER),
                inputMessage.getHeaders().getFirst(CHALLENGE_HEADER));
        String encryptedKey = firstText(servletRequest.getHeader(ENCRYPTED_KEY_HEADER),
                inputMessage.getHeaders().getFirst(ENCRYPTED_KEY_HEADER));
        if ((challengeId == null || challengeId.isBlank() || encryptedKey == null || encryptedKey.isBlank())
                && requiresEncryptedPayload()) {
            Asserts.fail("页面安全组件已更新，请刷新页面后重试");
        }
        if (challengeId != null || encryptedKey != null) {
            payloadEncryptionService.decryptSensitiveValues(challengeId, encryptedKey, body);
            markPayloadDecrypted();
        }
        return body;
    }

    private void markPayloadDecrypted() {
        // Spring MVC 实际传入的 HttpInputMessage 可能是内部包装类型，不能依赖类型转换取得请求。
        // 直接使用当前 HttpServletRequest，确保 AOP 兜底层能识别本次载荷已经成功解密，
        // 避免同一个一次性 challenge 被重复消费而误报“安全请求已失效”。
        servletRequest.setAttribute(DECRYPTED_PAYLOAD_ATTRIBUTE, Boolean.TRUE);
    }

    private String firstText(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private boolean requiresEncryptedPayload() {
        String path = servletRequest.getRequestURI();
        // ERP callbacks originate outside our web clients and use their own callback-token verification.
        return !path.startsWith("/distribution/erp/callbacks/");
    }
}
