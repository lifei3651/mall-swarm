package com.macro.mall.distribution.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSessionCookieFilterTest {

    private final AdminSessionCookieService cookieService = new AdminSessionCookieService();
    private final AdminSessionCookieFilter filter = new AdminSessionCookieFilter(cookieService);

    @Test
    void cookieBecomesAuthorizationHeaderForAdminApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/distribution/admin-auth/me");
        request.setCookies(new Cookie(AdminSessionCookieService.COOKIE_NAME, "raw-admin-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> authorization = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> authorization.set(
                ((HttpServletRequest) servletRequest).getHeader("Authorization"));

        filter.doFilter(request, response, chain);

        assertEquals("Bearer raw-admin-token", authorization.get());
    }

    @Test
    void cookieAuthenticatedWriteRequiresAdminClientHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/distribution/tenant/profile");
        request.setCookies(new Cookie(AdminSessionCookieService.COOKIE_NAME, "raw-admin-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("request must be rejected before business code");
        });

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("后台请求来源校验失败"));
    }

    @Test
    void secureAdminCookieIsHttpOnlyStrictAndScopedToApi() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/distribution/admin-auth/login");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.write(request, response, "raw-admin-token", LocalDateTime.now().plusHours(12));

        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertTrue(setCookie.contains("Path=/api"));
    }
}
