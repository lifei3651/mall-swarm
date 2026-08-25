package com.macro.mall.distribution.security;

import cn.hutool.crypto.SecureUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void appliesDedicatedRulesToProductImagesAndReviews() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));

        SecurityRateLimitFilter.Rule image = filter.resolveRule(
                new MockHttpServletRequest("POST", "/shop/admin/media/images"));
        SecurityRateLimitFilter.Rule cultureImage = filter.resolveRule(
                new MockHttpServletRequest("POST", "/shop/admin/media/brand-culture"));
        SecurityRateLimitFilter.Rule review = filter.resolveRule(
                new MockHttpServletRequest("POST", "/shop/products/18/reviews"));

        assertEquals("product-image-upload", image.name());
        assertEquals(30, image.maximumRequests());
        assertEquals("brand-culture-image-upload", cultureImage.name());
        assertEquals(30, cultureImage.maximumRequests());
        assertEquals("product-review-submit", review.name());
        assertEquals(10, review.maximumRequests());
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

    @Test
    void limitsPublicEncryptionChallengesWithoutRequiringAuthentication() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));

        SecurityRateLimitFilter.Rule rule = filter.resolveRule(
                new MockHttpServletRequest("GET", "/security/payload-encryption/key"));

        assertEquals("payload-encryption-key", rule.name());
        assertEquals(120, rule.maximumRequests());
    }

    @Test
    void limitsAuthenticatedPaymentPolicyChecks() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));

        SecurityRateLimitFilter.Rule rule = filter.resolveRule(
                new MockHttpServletRequest("GET", "/payment/checkVerify"));

        assertEquals("payment-verification", rule.name());
        assertEquals(120, rule.maximumRequests());
    }

    @Test
    void appliesDedicatedLimitToDashboardExports() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));

        SecurityRateLimitFilter.Rule rule = filter.resolveRule(
                new MockHttpServletRequest("GET", "/distribution/dashboard/export"));

        assertEquals("dashboard-export", rule.name());
        assertEquals(5, rule.maximumRequests());
        assertEquals(60, rule.windowSeconds());
    }

    @Test
    void appliesDedicatedLimitToBusinessImports() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));

        SecurityRateLimitFilter.Rule rule = filter.resolveRule(
                new MockHttpServletRequest("POST", "/distribution/import/agents/file"));

        assertEquals("business-import", rule.name());
        assertEquals(10, rule.maximumRequests());
        assertEquals(60, rule.windowSeconds());
    }

    @Test
    void appliesTightLimitsToMoneyAndCredentialChanges() {
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(mock(SecurityRateLimitService.class));

        SecurityRateLimitFilter.Rule withdrawal = filter.resolveRule(
                new MockHttpServletRequest("POST", "/shop/wallet/withdrawals"));
        SecurityRateLimitFilter.Rule transfer = filter.resolveRule(
                new MockHttpServletRequest("POST", "/shop/wallet/transfers"));
        SecurityRateLimitFilter.Rule password = filter.resolveRule(
                new MockHttpServletRequest("PUT", "/distribution/admin-users/9/password"));
        SecurityRateLimitFilter.Rule manualAsset = filter.resolveRule(
                new MockHttpServletRequest("POST", "/distribution/assets/deduct"));
        SecurityRateLimitFilter.Rule realName = filter.resolveRule(
                new MockHttpServletRequest("POST", "/shop/real-name/verify"));

        assertEquals("wallet-withdrawal", withdrawal.name());
        assertEquals(5, withdrawal.maximumRequests());
        assertEquals("wallet-funds", transfer.name());
        assertEquals(10, transfer.maximumRequests());
        assertEquals("admin-sensitive", password.name());
        assertEquals("admin-asset-change", manualAsset.name());
        assertEquals("real-name-verify", realName.name());
        assertEquals(5, realName.maximumRequests());
    }

    @Test
    void externalClientCannotSpoofForwardedHeadersToChangeRateLimitIdentity() throws Exception {
        SecurityRateLimitService limiter = mock(SecurityRateLimitService.class);
        when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString(), eq(10), eq(60))).thenReturn(true);
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/auth/login");
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Real-IP", "198.51.100.77");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(limiter).tryAcquire(eq("security:rate:shop-auth:" + SecureUtil.sha256("203.0.113.9")),
                eq(10), eq(60));
    }

    @Test
    void loopbackReverseProxyStillSuppliesTheRealClientIdentity() throws Exception {
        SecurityRateLimitService limiter = mock(SecurityRateLimitService.class);
        when(limiter.tryAcquire(org.mockito.ArgumentMatchers.anyString(), eq(10), eq(60))).thenReturn(true);
        SecurityRateLimitFilter filter = new SecurityRateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/auth/login");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.77");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(limiter).tryAcquire(eq("security:rate:shop-auth:" + SecureUtil.sha256("198.51.100.77")),
                eq(10), eq(60));
    }
}
