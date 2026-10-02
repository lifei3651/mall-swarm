package com.macro.mall.distribution.config;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.service.CustomerBusinessModePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import jakarta.servlet.http.*;

@Configuration
@RequiredArgsConstructor
public class CustomerBusinessModeBoundaryConfig implements WebMvcConfigurer {
    private final DmsTenantDao tenantDao;
    public static boolean restrictedPath(String path) {
        return path.startsWith("/distribution/performance") || (path.equals("/distribution/agent/roots") || path.startsWith("/distribution/agent/children/")
                || path.startsWith("/distribution/agent/descendants/") || path.startsWith("/distribution/agent/qrcode/"))
                || path.startsWith("/distribution/agent/line-change") || path.equals("/distribution/agent/switch-line")
                || path.equals("/shop/profile/performance");
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!"OPTIONS".equals(request.getMethod()) && restrictedPath(request.getRequestURI()))
                    CustomerBusinessModePolicy.requireLegacyQualification(tenantDao.selectById(TenantContext.getTenantId()));
                return true;
            }
        }).addPathPatterns("/distribution/**", "/shop/profile/**").order(100);
    }
}
