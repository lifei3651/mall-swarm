package com.macro.mall.common.aspect;

import com.macro.mall.common.annotation.Idempotent;
import com.macro.mall.common.exception.ApiException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentAspectTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void hashesAuthorizationInsteadOfWritingTokenIntoRedisKey() throws Throwable {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(true);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                request("safe-request-123456", "Bearer secret-token-value")));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        new IdempotentAspect(redis).around(joinPoint, annotation(30));

        verify(values).setIfAbsent(
                org.mockito.ArgumentMatchers.argThat(key -> {
                    assertFalse(key.contains("secret-token-value"));
                    return key.matches("idempotent:[a-f0-9]{64}");
                }),
                eq("1"), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void rejectsDuplicateRequestBeforeBusinessExecution() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                request("same-request-123456", "Bearer member-token")));

        assertThrows(ApiException.class,
                () -> new IdempotentAspect(redis).around(mock(ProceedingJoinPoint.class), annotation(30)));
    }

    @Test
    void releasesKeyWhenBusinessTransactionFails() throws Throwable {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(true);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                request("retry-request-123456", "Bearer member-token")));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("rollback"));

        assertThrows(IllegalStateException.class,
                () -> new IdempotentAspect(redis).around(joinPoint, annotation(30)));
        verify(redis).delete(anyString());
    }

    private MockHttpServletRequest request(String requestId, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/orders");
        request.addHeader("X-Idempotency-Key", requestId);
        request.addHeader("Authorization", authorization);
        return request;
    }

    private Idempotent annotation(long timeout) {
        Idempotent annotation = mock(Idempotent.class);
        when(annotation.timeout()).thenReturn(timeout);
        when(annotation.message()).thenReturn("请勿重复提交");
        return annotation;
    }
}
