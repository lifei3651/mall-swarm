package com.macro.mall.distribution.security;

import com.macro.mall.common.aspect.IdempotentAspect;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Makes the HttpOnly storefront cookie available to the existing authorization
 * services without weakening compatibility with trusted Bearer clients.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class ShopSessionCookieFilter extends OncePerRequestFilter {

    private final ShopSessionCookieService cookieService;
    private final ShopAuthService shopAuthService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/shop/") && !path.startsWith("/sms/")
                && !path.startsWith("/payment/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String cookieToken = cookieService.read(request);
        boolean cookieAuthenticated = (authorization == null || authorization.isBlank())
                && cookieToken != null && !cookieToken.isBlank();

        if (cookieAuthenticated && isStateChanging(request)
                && !ShopSessionCookieService.CLIENT_HEADER_VALUE.equals(request.getHeader(ShopSessionCookieService.CLIENT_HEADER))) {
            writeForbidden(response);
            return;
        }

        HttpServletRequest effectiveRequest = cookieAuthenticated
                ? new AuthorizationHeaderRequest(request, "Bearer " + cookieToken)
                : request;
        bindStableIdempotencyPrincipal(effectiveRequest);
        filterChain.doFilter(effectiveRequest, response);

        // One-time compatibility bridge: an existing browser session receives
        // the HttpOnly cookie after /auth/me validates the legacy bearer token.
        if (!cookieAuthenticated && authorization != null && authorization.startsWith("Bearer ")
                && "/shop/auth/me".equals(request.getRequestURI()) && response.getStatus() < 400) {
            cookieService.write(request, response, authorization.substring(7), LocalDateTime.now().plusDays(7));
        }
    }

    private void bindStableIdempotencyPrincipal(HttpServletRequest request) {
        String requestKey = request.getHeader("X-Idempotency-Key");
        if (requestKey == null || requestKey.isBlank()) {
            return;
        }
        DmsShopMember member = shopAuthService.resolveMember(request.getHeader("Authorization"));
        if (member != null && member.getUserId() != null) {
            request.setAttribute(IdempotentAspect.PRINCIPAL_ATTRIBUTE, "member:" + member.getUserId());
        }
    }

    private boolean isStateChanging(HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod())
                && !HttpMethod.HEAD.matches(request.getMethod())
                && !HttpMethod.OPTIONS.matches(request.getMethod());
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"请求来源校验失败，请刷新页面后重试\",\"data\":null}");
    }

    private static final class AuthorizationHeaderRequest extends HttpServletRequestWrapper {
        private final String authorization;

        private AuthorizationHeaderRequest(HttpServletRequest request, String authorization) {
            super(request);
            this.authorization = authorization;
        }

        @Override
        public String getHeader(String name) {
            return "Authorization".equalsIgnoreCase(name) ? authorization : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return "Authorization".equalsIgnoreCase(name)
                    ? Collections.enumeration(Collections.singletonList(authorization))
                    : super.getHeaders(name);
        }
    }
}
