package com.macro.mall.distribution.security;

import cn.hutool.crypto.SecureUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Application-layer safety net in addition to the production Nginx limits. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class SecurityRateLimitFilter extends OncePerRequestFilter {

    private final SecurityRateLimitService rateLimitService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return resolveRule(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        Rule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientKey = SecureUtil.sha256(resolveClientAddress(request));
        String redisKey = "security:rate:" + rule.name + ":" + clientKey;
        if (!rateLimitService.tryAcquire(redisKey, rule.maximumRequests, rule.windowSeconds)) {
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(rule.windowSeconds));
            response.getWriter().write("{\"code\":429,\"message\":\"操作过于频繁，请稍后再试\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    Rule resolveRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (HttpMethod.POST.matches(method) && "/distribution/admin-auth/login".equals(path)) {
            return new Rule("admin-login", 10, 60);
        }
        if (HttpMethod.POST.matches(method) && ("/shop/auth/login".equals(path)
                || "/shop/auth/register".equals(path) || "/shop/auth/resetPassword".equals(path))) {
            return new Rule("shop-auth", 10, 60);
        }
        if (HttpMethod.GET.matches(method) && "/captcha".equals(path)) {
            return new Rule("captcha", 30, 60);
        }
        if (HttpMethod.POST.matches(method) && path.startsWith("/sms/send")) {
            return new Rule("sms-send", 5, 60);
        }
        if (HttpMethod.GET.matches(method) && ("/pay/alipay/return".equals(path)
                || "/shop/pay/alipay/return".equals(path))) {
            return new Rule("alipay-return", 30, 60);
        }
        if (path.startsWith("/shop/wallet/") && !HttpMethod.GET.matches(method)) {
            return new Rule("wallet-write", 30, 60);
        }
        if (HttpMethod.POST.matches(method) && "/shop/media/after-sale-proofs".equals(path)) {
            return new Rule("after-sale-proof-upload", 12, 60);
        }
        if ((path.startsWith("/shop/") || path.startsWith("/distribution/"))
                && !HttpMethod.GET.matches(method)) {
            return new Rule("business-write", 120, 60);
        }
        if (path.startsWith("/shop/") || path.startsWith("/distribution/")) {
            return new Rule("api-read", 600, 60);
        }
        return null;
    }

    private String resolveClientAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isLoopback(remote)) {
            String forwarded = request.getHeader("X-Real-IP");
            if (forwarded == null || forwarded.isBlank()) {
                forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && forwarded.contains(",")) forwarded = forwarded.split(",", 2)[0];
            }
            if (forwarded != null && !forwarded.isBlank()) return forwarded.trim();
        }
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "0:0:0:0:0:0:0:1".equals(address) || "::1".equals(address);
    }

    record Rule(String name, int maximumRequests, int windowSeconds) {}
}
