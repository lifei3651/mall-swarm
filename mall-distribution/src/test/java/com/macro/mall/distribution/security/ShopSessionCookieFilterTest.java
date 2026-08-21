package com.macro.mall.distribution.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.aspect.IdempotentAspect;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.vo.ShopAuthVO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopSessionCookieFilterTest {

    private final ShopSessionCookieService cookieService = new ShopSessionCookieService();
    private final ShopSessionCookieFilter filter = new ShopSessionCookieFilter(cookieService, mock(ShopAuthService.class));

    @Test
    void cookieBecomesAuthorizationHeaderWithoutExposingItToJavascript() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/auth/me");
        request.setCookies(new Cookie(ShopSessionCookieService.COOKIE_NAME, "raw-session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> authorization = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> authorization.set(
                ((HttpServletRequest) servletRequest).getHeader("Authorization"));

        filter.doFilter(request, response, chain);

        assertEquals("Bearer raw-session-token", authorization.get());
    }

    @Test
    void paymentVerificationReceivesTheSameAuthenticatedCookieBridge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payment/checkVerify");
        request.setCookies(new Cookie(ShopSessionCookieService.COOKIE_NAME, "raw-session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> authorization = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> authorization.set(
                ((HttpServletRequest) servletRequest).getHeader("Authorization")));

        assertEquals("Bearer raw-session-token", authorization.get());
    }

    @Test
    void cookieAuthenticatedWriteRequiresStorefrontCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/orders");
        request.setCookies(new Cookie(ShopSessionCookieService.COOKIE_NAME, "raw-session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("request must be rejected before business code");
        });

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("请求来源校验失败"));
    }

    @Test
    void secureCookieIsHttpOnlyAndAuthTokenIsNotSerialized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/auth/login");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieService.write(request, response, "raw-session-token", LocalDateTime.now().plusDays(7));

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=None"));
        assertTrue(setCookie.contains("Path=/api"));

        ShopAuthVO auth = new ShopAuthVO();
        auth.setToken("raw-session-token");
        String json = new ObjectMapper().writeValueAsString(auth);
        assertFalse(json.contains("raw-session-token"));
        assertFalse(json.contains("token"));
    }

    @Test
    void idempotentWriteBindsStableMemberPrincipalBeforeBusinessCode() throws Exception {
        ShopAuthService authService = mock(ShopAuthService.class);
        ShopSessionCookieFilter principalFilter = new ShopSessionCookieFilter(cookieService, authService);
        DmsShopMember member = new DmsShopMember();
        member.setUserId(9527L);
        when(authService.resolveMember("Bearer rotating-session-token")).thenReturn(member);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shop/orders");
        request.addHeader("Authorization", "Bearer rotating-session-token");
        request.addHeader("X-Idempotency-Key", "order-submit-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Object> principal = new AtomicReference<>();

        principalFilter.doFilter(request, response, (servletRequest, servletResponse) -> principal.set(
                servletRequest.getAttribute(IdempotentAspect.PRINCIPAL_ATTRIBUTE)));

        assertEquals("member:9527", principal.get());
    }
}
