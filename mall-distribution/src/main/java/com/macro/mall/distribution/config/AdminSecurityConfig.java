package com.macro.mall.distribution.config;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.AdminStepUpService;
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
    private final AdminStepUpService adminStepUpService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminSecurityInterceptor(adminAuthService, operationLogService, adminStepUpService))
                .addPathPatterns("/distribution/**", "/shop/admin/**")
                // ERP 无法携带后台会话；发货回传仅通过各集成独立 callbackToken 鉴权。
                .excludePathPatterns("/distribution/admin-auth/login", "/distribution/erp/callbacks/**",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");
    }

    static class AdminSecurityInterceptor implements HandlerInterceptor {

        private final AdminAuthService adminAuthService;
        private final OperationLogService operationLogService;
        private final AdminStepUpService adminStepUpService;

        AdminSecurityInterceptor(AdminAuthService adminAuthService, OperationLogService operationLogService,
                                 AdminStepUpService adminStepUpService) {
            this.adminAuthService = adminAuthService;
            this.operationLogService = operationLogService;
            this.adminStepUpService = adminStepUpService;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
            if (HttpMethod.OPTIONS.matches(request.getMethod())) {
                return true;
            }
            try {
                DmsAdminUser admin = adminAuthService.requireAdmin(request.getHeader("Authorization"));
                if (Integer.valueOf(1).equals(admin.getMustChangePassword()) && !passwordChangeRequest(request)) {
                    throw new ApiException("必须先修改后台初始密码，才能继续使用管理功能");
                }
                String permission = AdminPermissionPolicy.requiredPermission(request.getMethod(), request.getRequestURI());
                if (permission == null) {
                    throw new ApiException("后台接口未配置权限或请求路径不合法");
                }
                adminAuthService.requirePermission(admin, permission);
                if (admin.getMerchantId() != null && !merchantWorkspaceRequest(request)) {
                    throw new ApiException("没有操作权限：商户工作台账号不能访问平台管理功能");
                }
                if (AdminStepUpPolicy.requires(request.getMethod(), request.getRequestURI())) {
                    adminStepUpService.consume(admin, request.getMethod(), request.getRequestURI(),
                            request.getHeader(AdminStepUpPolicy.HEADER));
                }
                AdminContext.set(admin);
                return true;
            } catch (ApiException e) {
                int status = e.getMessage() != null && (e.getMessage().startsWith("没有操作权限")
                        || e.getMessage().startsWith("必须先修改")) ? 403 : 401;
                writeError(response, status, e.getMessage());
                return false;
            }
        }

        private boolean passwordChangeRequest(HttpServletRequest request) {
            String path = request.getRequestURI();
            return path.equals("/distribution/admin-auth/me")
                    || path.equals("/distribution/admin-auth/logout")
                    || path.equals("/distribution/admin-auth/step-up")
                    || (path.equals("/distribution/admin-auth/password") && HttpMethod.PUT.matches(request.getMethod()));
        }

        private boolean merchantWorkspaceRequest(HttpServletRequest request) {
            return isMerchantWorkspaceRequest(request.getMethod(), request.getRequestURI());
        }

        static boolean isMerchantWorkspaceRequest(String method, String path) {
            if (path == null) return false;
            if (path.equals("/distribution/admin-auth/me")
                    || path.equals("/distribution/admin-auth/logout")
                    || path.equals("/distribution/admin-auth/password")
                    || path.startsWith("/shop/admin/products")
                    || path.startsWith("/shop/admin/skus")
                    || path.startsWith("/shop/admin/media")) return true;
            if (path.startsWith("/distribution/merchant-finance")) {
                return HttpMethod.GET.matches(method)
                        || (HttpMethod.POST.matches(method)
                        && (path.equals("/distribution/merchant-finance/withdrawals")
                        || path.matches("/distribution/merchant-finance/withdrawals/[^/]+/cancel")));
            }
            if (HttpMethod.GET.matches(method) && path.startsWith("/distribution/merchants")) return true;
            if (path.startsWith("/shop/admin/service-addresses")) return true;
            if (path.startsWith("/shop/admin/orders")) {
                if (HttpMethod.GET.matches(method)) return true;
                return HttpMethod.PUT.matches(method) && (path.matches("/shop/admin/orders/[^/]+/ship")
                        || path.matches("/shop/admin/orders/[^/]+/service-remark"));
            }
            if (path.startsWith("/shop/admin/after-sales")) {
                return HttpMethod.GET.matches(method) || (HttpMethod.PUT.matches(method)
                        && (path.matches("/shop/admin/after-sales/[^/]+/audit")
                        || path.matches("/shop/admin/after-sales/[^/]+/return-received")));
            }
            return HttpMethod.GET.matches(method) && (path.startsWith("/shop/admin/categories")
                    || path.startsWith("/shop/admin/product-settings")
                    || path.startsWith("/shop/admin/freight-templates"));
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
            String path = request.getRequestURI();
            return isMemberLevelRequest(request)
                    || path.equals("/distribution/admin-auth/logout")
                    || path.startsWith("/distribution/admin-users")
                    || path.startsWith("/distribution/withdraw")
                    || path.startsWith("/distribution/bonus-config")
                    || path.startsWith("/distribution/tenant")
                    || (HttpMethod.PUT.matches(request.getMethod())
                    && path.matches("/shop/admin/orders/[^/]+/service-remark"))
                    || path.matches("/shop/admin/products/[^/]+/submit-review")
                    || path.matches("/shop/admin/merchant-product-reviews/[^/]+/decision");
        }

        private boolean shouldLog(HttpServletRequest request) {
            // 二次验证只签发一次性凭证，真正的业务写操作会单独留痕，避免每次敏感操作产生两条日志。
            if (request.getRequestURI().equals("/distribution/admin-auth/step-up")) return false;
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
            if (path.matches("/shop/admin/products/[^/]+/submit-review")) return "提交商户商品审核";
            if (path.matches("/shop/admin/merchant-product-reviews/[^/]+/decision")) return "审核商户商品";
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
