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
        if (HttpMethod.POST.matches(method) && "/distribution/admin-auth/step-up".equals(path)) {
            return new Rule("admin-step-up", 10, 60);
        }
        if (HttpMethod.POST.matches(method) && ("/shop/auth/login".equals(path)
                || "/shop/auth/register".equals(path) || "/shop/auth/resetPassword".equals(path))) {
            return new Rule("shop-auth", 10, 60);
        }
        if (HttpMethod.GET.matches(method) && "/captcha".equals(path)) {
            return new Rule("captcha", 30, 60);
        }
        if (HttpMethod.GET.matches(method) && "/security/payload-encryption/key".equals(path)) {
            return new Rule("payload-encryption-key", 120, 60);
        }
        if (HttpMethod.POST.matches(method) && "/shop/client-errors".equals(path)) {
            return new Rule("client-error-report", 30, 60);
        }
        if (HttpMethod.POST.matches(method) && "/distribution/erp/callbacks/shipment".equals(path)) {
            return new Rule("erp-shipment-callback", 120, 60);
        }
        if (HttpMethod.POST.matches(method) && "/shop/live/callbacks/tencent".equals(path)) {
            return new Rule("tencent-live-callback", 300, 60);
        }
        if (HttpMethod.POST.matches(method) && path.startsWith("/shop/notification/receipts/")) {
            return new Rule("notification-receipt", 300, 60);
        }
        if (HttpMethod.GET.matches(method) && "/payment/checkVerify".equals(path)) {
            return new Rule("payment-verification", 120, 60);
        }
        if (HttpMethod.GET.matches(method) && "/distribution/dashboard/export".equals(path)) {
            return new Rule("dashboard-export", 5, 60);
        }
        if (HttpMethod.POST.matches(method) && path.startsWith("/distribution/import/")) {
            return new Rule("business-import", 10, 60);
        }
        if (HttpMethod.POST.matches(method) && path.startsWith("/sms/send")) {
            return new Rule("sms-send", 5, 60);
        }
        if (HttpMethod.POST.matches(method) && "/sms/verify".equals(path)) {
            return new Rule("sms-verify", 10, 60);
        }
        if (HttpMethod.GET.matches(method) && ("/pay/alipay/return".equals(path)
                || "/shop/pay/alipay/return".equals(path))) {
            return new Rule("alipay-return", 30, 60);
        }
        if ((HttpMethod.POST.matches(method) || HttpMethod.PUT.matches(method))
                && (path.matches("/distribution/admin-users/[^/]+/(password|unlock)")
                || "/distribution/withdraw/audit".equals(path)
                || path.matches("/distribution/withdraw/confirm-pay/[^/]+"))) {
            return new Rule("admin-sensitive", 10, 60);
        }
        if (HttpMethod.POST.matches(method) && ("/distribution/assets/issue".equals(path)
                || "/distribution/assets/deduct".equals(path))) {
            return new Rule("admin-asset-change", 10, 60);
        }
        if (HttpMethod.PUT.matches(method) && "/shop/wallet/payment-password".equals(path)) {
            return new Rule("payment-password-change", 5, 60);
        }
        if (HttpMethod.POST.matches(method) && ("/shop/wallet/transfers".equals(path)
                || path.matches("/shop/wallet/orders/[^/]+/pay"))) {
            return new Rule("wallet-funds", 10, 60);
        }
        if (HttpMethod.POST.matches(method) && "/shop/wallet/withdrawals".equals(path)) {
            return new Rule("wallet-withdrawal", 5, 60);
        }
        if (HttpMethod.POST.matches(method) && "/shop/real-name/verify".equals(path)) {
            return new Rule("real-name-verify", 5, 60);
        }
        if (path.startsWith("/shop/wallet/") && !HttpMethod.GET.matches(method)) {
            return new Rule("wallet-write", 30, 60);
        }
        if (HttpMethod.POST.matches(method) && "/shop/media/after-sale-proofs".equals(path)) {
            return new Rule("after-sale-proof-upload", 12, 60);
        }
        if (HttpMethod.POST.matches(method) && "/shop/admin/media/images".equals(path)) {
            return new Rule("product-image-upload", 30, 60);
        }
        if (HttpMethod.POST.matches(method) && "/shop/admin/media/brand-culture".equals(path)) {
            return new Rule("brand-culture-image-upload", 30, 60);
        }
        if (HttpMethod.POST.matches(method) && path.matches("/shop/products/[^/]+/reviews")) {
            return new Rule("product-review-submit", 10, 60);
        }
        if (HttpMethod.POST.matches(method) && (path.equals("/shop/service-tickets")
                || path.matches("/shop/service-tickets/[^/]+/replies")
                || path.matches("/shop/admin/service-tickets/[^/]+/replies"))) {
            return new Rule("service-ticket-write", 10, 60);
        }
        if (HttpMethod.POST.matches(method) && path.matches("/shop/live-rooms/[^/]+/comments")) {
            return new Rule("live-comment", 20, 60);
        }
        if (HttpMethod.POST.matches(method) && path.matches("/shop/live-rooms/[^/]+/engagement")) {
            return new Rule("live-engagement", 180, 60);
        }
        if (HttpMethod.POST.matches(method) && path.matches("/shop/live-studio/rooms/[^/]+/(start|stop)")) {
            return new Rule("live-studio-control", 12, 60);
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
