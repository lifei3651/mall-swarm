package com.macro.mall.distribution.security;

import com.macro.mall.common.api.CommonResult;
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

/** Moves browser admin sessions into an HttpOnly cookie and rejects cookie-based write requests without the admin client marker. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
@RequiredArgsConstructor
public class AdminSessionCookieFilter extends OncePerRequestFilter {

    private final AdminSessionCookieService cookieService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/distribution/") && !path.startsWith("/shop/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String cookieToken = cookieService.read(request);
        boolean cookieAuthenticated = (authorization == null || authorization.isBlank())
                && cookieToken != null && !cookieToken.isBlank();

        if (cookieAuthenticated && isStateChanging(request)
                && !AdminSessionCookieService.CLIENT_HEADER_VALUE.equals(request.getHeader(AdminSessionCookieService.CLIENT_HEADER))) {
            writeForbidden(response);
            return;
        }

        HttpServletRequest effectiveRequest = cookieAuthenticated
                ? new AuthorizationHeaderRequest(request, "Bearer " + cookieToken)
                : request;
        filterChain.doFilter(effectiveRequest, response);

        if (!cookieAuthenticated && authorization != null && authorization.startsWith("Bearer ")
                && "/distribution/admin-auth/me".equals(request.getRequestURI()) && response.getStatus() < 400) {
            cookieService.write(request, response, authorization.substring(7), LocalDateTime.now().plusHours(12));
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
        response.getWriter().write(CommonResult.failed(403, "后台请求来源校验失败，请刷新页面后重试").toString());
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
