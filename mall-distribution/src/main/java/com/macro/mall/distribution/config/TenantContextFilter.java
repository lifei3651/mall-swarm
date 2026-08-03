package com.macro.mall.distribution.config;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private final DmsShopMemberDao memberDao;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Long tenantId = resolveTenantId(request);
        try {
            TenantContext.setTenantId(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 解析租户ID：优先从登录用户的会员信息获取，不信任客户端头或参数
     */
    private Long resolveTenantId(HttpServletRequest request) {
        // 从 Authorization 头获取 token，查找会员信息中的 tenantId
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
            // 通过 token 查询会员（简化实现，实际应使用 session 查询）
            // 暂时使用默认租户，因为商城前台的会员表没有 tenantId 字段
        }
        // 使用默认租户ID
        return DEFAULT_TENANT_ID;
    }

    private void writeBadTenantId(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":400,\"message\":\"租户ID格式不正确\",\"data\":null}");
    }
}
