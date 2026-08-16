package com.macro.mall.common.aspect;

import com.macro.mall.common.annotation.Idempotent;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.idempotency.IdempotencyStore;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentAspectTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void hashesAuthorizationInsteadOfWritingTokenIntoRedisKey() throws Throwable {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquire(anyString())).thenReturn(true);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                request("safe-request-123456", "Bearer secret-token-value")));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        new IdempotentAspect(store).around(joinPoint, annotation(30));

        verify(store).tryAcquire(
                org.mockito.ArgumentMatchers.argThat(key -> {
                    assertFalse(key.contains("secret-token-value"));
                    return key.matches("[a-f0-9]{64}");
                }));
        verify(store).markSucceeded(anyString());
    }

    @Test
    void stablePrincipalKeepsSameIdempotencyScopeAfterTokenRotation() throws Throwable {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquire(anyString())).thenReturn(true);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        MockHttpServletRequest first = request("same-business-request", "Bearer first-session-token");
        first.setAttribute(IdempotentAspect.PRINCIPAL_ATTRIBUTE, "member:1001");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(first));
        new IdempotentAspect(store).around(joinPoint, annotation(30));

        MockHttpServletRequest second = request("same-business-request", "Bearer rotated-session-token");
        second.setAttribute(IdempotentAspect.PRINCIPAL_ATTRIBUTE, "member:1001");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(second));
        new IdempotentAspect(store).around(joinPoint, annotation(30));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(store, org.mockito.Mockito.times(2)).tryAcquire(keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
    }

    @Test
    void rejectsDuplicateRequestBeforeBusinessExecution() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquire(anyString())).thenReturn(false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                request("same-request-123456", "Bearer member-token")));

        assertThrows(ApiException.class,
                () -> new IdempotentAspect(store).around(mock(ProceedingJoinPoint.class), annotation(30)));
    }

    @Test
    void releasesKeyWhenBusinessTransactionFails() throws Throwable {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquire(anyString())).thenReturn(true);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                request("retry-request-123456", "Bearer member-token")));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("rollback"));

        assertThrows(IllegalStateException.class,
                () -> new IdempotentAspect(store).around(joinPoint, annotation(30)));
        verify(store).releaseFailed(anyString());
    }

    @Test
    void completionMarkerFailureNeverReopensCommittedRequest() throws Throwable {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquire(anyString())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(store).markSucceeded(anyString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                request("committed-request-123456", "Bearer member-token")));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("committed");

        assertThrows(IllegalStateException.class,
                () -> new IdempotentAspect(store).around(joinPoint, annotation(30)));
        verify(store, never()).releaseFailed(anyString());
    }

    @Test
    void rejectsMissingClientRequestKeyInsteadOfUsingSharedFallback() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        MockHttpServletRequest request = request(null, "Bearer member-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThrows(ApiException.class,
                () -> new IdempotentAspect(store).around(mock(ProceedingJoinPoint.class), annotation(30)));
    }

    private MockHttpServletRequest request(String requestId, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/orders");
        if (requestId != null) request.addHeader("X-Idempotency-Key", requestId);
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
