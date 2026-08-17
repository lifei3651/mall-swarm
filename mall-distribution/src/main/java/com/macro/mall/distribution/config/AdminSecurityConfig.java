package com.macro.mall.distribution.config;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class AdminSecurityConfig implements WebMvcConfigurer {

    private final AdminAuthService adminAuthService;
    private final OperationLogService operationLogService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminSecurityInterceptor(adminAuthService, operationLogService))
                .addPathPatterns("/distribution/**", "/shop/admin/**")
                // ERP 无法携带后台会话；发货回传仅通过各集成独立 callbackToken 鉴权。
                .excludePathPatterns("/distribution/admin-auth/login", "/distribution/erp/callbacks/**",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");
    }

    private static class AdminSecurityInterceptor implements HandlerInterceptor {

        private final AdminAuthService adminAuthService;
        private final OperationLogService operationLogService;

        AdminSecurityInterceptor(AdminAuthService adminAuthService, OperationLogService operationLogService) {
            this.adminAuthService = adminAuthService;
            this.operationLogService = operationLogService;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
            if (HttpMethod.OPTIONS.matches(request.getMethod())) {
                return true;
            }
            try {
                DmsAdminUser admin = adminAuthService.requireAdmin(request.getHeader("Authorization"));
                String permission = AdminPermissionPolicy.requiredPermission(request.getMethod(), request.getRequestURI());
                if (permission == null) {
                    throw new ApiException("后台接口未配置权限或请求路径不合法");
                }
                adminAuthService.requirePermission(admin, permission);
                AdminContext.set(admin);
                return true;
            } catch (ApiException e) {
                int status = e.getMessage() != null && e.getMessage().startsWith("没有操作权限") ? 403 : 401;
                writeError(response, status, e.getMessage());
                return false;
            }
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            try {
                boolean success = ex == null && response.getStatus() < 400;
                // 会员调级由业务服务写入带会员身份、前后级别和原因的详细审计记录。
                // 成功请求不再额外写一条只有 URI 的通用 ADMIN_API 记录；失败请求仍保留通用失败日志。
                if (shouldLog(request) && !(hasDetailedBusinessLog(request) && success)) {
                    String remark = describeRequest(request) + "，结果：" + (success ? "成功" : "失败")
                            + "（HTTP " + response.getStatus() + "）";
                    operationLogService.log("ADMIN_API", request.getMethod(), "HTTP", request.getRequestURI(),
                            null, null, remark);
                }
            } finally {
                AdminContext.clear();
            }
        }

        private boolean isMemberLevelRequest(HttpServletRequest request) {
            return request.getRequestURI().matches("(/shop/admin/members|/distribution/agent)/[^/]+/level")
                    && HttpMethod.PUT.matches(request.getMethod());
        }

        private boolean hasDetailedBusinessLog(HttpServletRequest request) {
            return isMemberLevelRequest(request)
                    || (HttpMethod.PUT.matches(request.getMethod())
                    && request.getRequestURI().matches("/shop/admin/orders/[^/]+/service-remark"));
        }

        private boolean shouldLog(HttpServletRequest request) {
            String method = request.getMethod();
            return HttpMethod.POST.matches(method) || HttpMethod.PUT.matches(method) || HttpMethod.DELETE.matches(method);
        }

        private String describeRequest(HttpServletRequest request) {
            String path = request.getRequestURI();
            String method = request.getMethod();
            if (path.matches("/shop/admin/members/[^/]+/level")) return "调整会员级别";
            if (path.matches("/shop/admin/members/[^/]+/status")) return "修改登录账号状态";
            if (path.matches("/shop/admin/members/[^/]+/unlock")) return "解除会员登录锁定";
            if (path.matches("/shop/admin/members/[^/]+/payment-password/unlock")) return "解除会员支付密码锁定";
            if (path.matches("/shop/admin/members/[^/]+/phone")) return "修改会员登录手机号";
            if (path.matches("/shop/admin/members/[^/]+/login-password")) return "重置会员登录密码";
            if (path.equals("/shop/admin/members") && HttpMethod.POST.matches(method)) return "后台新增商城会员";
            if (path.equals("/distribution/assets/issue")) return "直接增加会员余额";
            if (path.equals("/distribution/assets/deduct")) return "直接扣减会员余额";
            if (path.equals("/distribution/agent/switch-line")) return "执行会员移线";
            if (path.matches("/distribution/agent/line-change-applications/[^/]+/audit")) return "处理旧版移线申请";
            if (path.matches("/shop/admin/products/[^/]+/publish") || path.equals("/shop/admin/products/publish")) return "发布商品";
            if (path.matches("/shop/admin/products/[^/]+/status")) return "修改商品上架状态";
            if (path.startsWith("/shop/admin/products")) return "保存商品资料";
            if (path.startsWith("/shop/admin/categories")) return "维护商品分类";
            if (path.matches("/shop/admin/orders/[^/]+/ship")) return "商城订单发货";
            if (path.matches("/shop/admin/orders/[^/]+/service-remark")) return "修改订单客服备注";
            if (path.matches("/shop/admin/orders/[^/]+/cancel")) return "后台取消或退款商城订单";
            if (path.equals("/shop/admin/orders/shipments/import")) return "Excel批量导入订单物流并发货";
            if (path.matches("/shop/admin/after-sales/[^/]+/audit")) return "审核商城售后";
            if (path.matches("/shop/admin/reviews/[^/]+/status")) return "显示或隐藏商品评价";
            if (path.startsWith("/distribution/withdraw")) return "处理会员提现";
            if (path.startsWith("/distribution/tenant")) return "修改商城品牌或界面设置";
            if (path.matches("/distribution/admin-users/[^/]+/unlock")) return "解除后台管理员登录锁定";
            if (path.startsWith("/distribution/admin-users")) return "维护后台管理员及权限";
            if (path.startsWith("/distribution/erp")) return "维护或执行ERP对接";
            if (path.startsWith("/distribution/merchants")) return "维护商户资料";
            if (path.startsWith("/distribution/merchant-finance")) return "处理商户货款、发票与打款";
            String action = HttpMethod.POST.matches(method) ? "新增/提交" : HttpMethod.PUT.matches(method) ? "修改" : "删除";
            return action + "后台业务数据（" + path + "）";
        }

        private void writeError(HttpServletResponse response, int status, String message) throws IOException {
            response.setStatus(status);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + escapeJson(message) + "\",\"data\":null}");
        }

        private String escapeJson(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
