package com.macro.mall.distribution.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecurityRateLimitFilterTest {

    @Test
    void blocksExcessiveShopLoginBeforeControllerExecution() throws Exception {
        SecurityRateLimitService limiter = mock(SecurityRateLimitService.class);
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/auth/login");
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString(), eq(10), eq(60))).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("操作过于频繁"));
        verifyNoInteractions(chain);
    }

    @Test
    void appliesStricterRuleToSmsSending() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sms/send");

        SecurityRateLimitFilter.Rule rule = filter.resolveRule(request);

        assertEquals("sms-send", rule.name());
        assertEquals(5, rule.maximumRequests());
        assertEquals(60, rule.windowSeconds());
    }

    @Test
    void appliesDedicatedRuleToSmsVerification() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));
        SecurityRateLimitFilter.Rule rule = filter.resolveRule(new MockHttpServletRequest("POST", "/sms/verify"));
        assertEquals("sms-verify", rule.name());
        assertEquals(10, rule.maximumRequests());
    }

    @Test
    void appliesDedicatedRuleToAfterSaleProofUploads() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/media/after-sale-proofs");

        SecurityRateLimitFilter.Rule rule = filter.resolveRule(request);

        assertEquals("after-sale-proof-upload", rule.name());
        assertEquals(12, rule.maximumRequests());
        assertEquals(60, rule.windowSeconds());
    }

    @Test
    void appliesDedicatedRuleToAnonymousAlipayReturn() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pay/alipay/return");

        SecurityRateLimitFilter.Rule rule = filter.resolveRule(request);

        assertEquals("alipay-return", rule.name());
        assertEquals(30, rule.maximumRequests());
        assertEquals(60, rule.windowSeconds());
    }
}
