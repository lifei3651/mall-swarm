package com.macro.mall.distribution.security;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonResult;
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

/** 限制外部分页大小，并把 PageHelper 线程状态严格限制在本次请求内。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class PaginationLimitFilter extends OncePerRequestFilter {

    static final int MAX_PAGE_SIZE = 100;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // startPage 后若权限校验抛错/空分支返回，没有 SQL 消费该状态。
        // Tomcat 复用线程时会污染下个请求（包括无 pageSize 的会话查询，产生双 LIMIT）。
        PageHelper.clearPage();
        try {
            String[] values = request.getParameterValues("pageSize");
            if (values != null && (values.length == 0 || !valid(values))) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(CommonResult.failed(400, "每页数量必须为1至100").toString());
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            PageHelper.clearPage();
        }
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
