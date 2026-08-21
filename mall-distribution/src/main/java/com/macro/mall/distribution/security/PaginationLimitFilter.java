package com.macro.mall.distribution.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 全局限制外部分页大小，避免遗漏单个 Controller 时出现无界批量查询。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class PaginationLimitFilter extends OncePerRequestFilter {

    static final int MAX_PAGE_SIZE = 100;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getParameterValues("pageSize") == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String[] values = request.getParameterValues("pageSize");
        if (values == null || values.length == 0 || !valid(values)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"message\":\"每页数量必须为1至100\",\"data\":null}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean valid(String[] values) {
        for (String value : values) {
            if (value == null || value.isBlank()) return false;
            try {
                int pageSize = Integer.parseInt(value.trim());
                if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }
}
