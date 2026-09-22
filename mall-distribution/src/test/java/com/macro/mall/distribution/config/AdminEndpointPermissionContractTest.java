package com.macro.mall.distribution.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端公开路由和权限策略之间的合同。
 *
 * <p>新增 {@code /shop/admin/**} 控制器端点时，本测试会自动发现它；如果开发者忘记在
 * {@link AdminPermissionPolicy} 登记权限，测试必须失败，而不是等到线上返回 403 才发现。</p>
 */
class AdminEndpointPermissionContractTest {

    private static final String CONTROLLER_PACKAGE = "com.macro.mall.distribution.controller";
    private static final Set<String> MERCHANT_ASSIGNABLE_PERMISSIONS = Set.of(
            "admin:read", "shop:product", "shop:order", "shop:aftersale", "finance:read", "finance:manage");

    @Test
    void everyProtectedAdminControllerEndpointHasAnExplicitBusinessPermission() throws Exception {
        List<Endpoint> endpoints = protectedAdminEndpoints();
        assertTrue(endpoints.stream().anyMatch(endpoint -> endpoint.path().startsWith("/shop/admin")),
                "未发现 /shop/admin 控制器端点，合同扫描失效");
        assertTrue(endpoints.stream().anyMatch(endpoint -> endpoint.path().startsWith("/distribution")),
                "未发现 /distribution 控制器端点，合同扫描失效");

        List<String> missing = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            String permission = AdminPermissionPolicy.requiredPermission(endpoint.httpMethod(), endpoint.path());
            if (permission == null || permission.isBlank()) missing.add(endpoint.toString());
        }

        assertTrue(missing.isEmpty(), "下列公开管理端点没有服务端权限映射：\n" + String.join("\n", missing));
    }

    @Test
    void everyDeclaredMerchantWorkspaceEndpointUsesAPermissionThatMerchantStaffCanActuallyReceive() throws Exception {
        List<String> invalid = new ArrayList<>();
        Set<String> declaredAreas = new LinkedHashSet<>();
        for (Endpoint endpoint : protectedAdminEndpoints()) {
            if (!endpoint.path().startsWith("/shop/admin")) continue;
            if (!AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                    endpoint.httpMethod(), endpoint.path())) continue;
            String permission = AdminPermissionPolicy.requiredPermission(endpoint.httpMethod(), endpoint.path());
            if (!MERCHANT_ASSIGNABLE_PERMISSIONS.contains(permission)) {
                invalid.add(endpoint + " -> " + permission);
            }
            declaredAreas.add(endpoint.area());
        }

        assertTrue(invalid.isEmpty(), "商户工作台声明了无法授予的权限：\n" + String.join("\n", invalid));
        assertTrue(declaredAreas.containsAll(Set.of("products", "orders", "after-sales", "service-tickets")),
                "商户工作台核心履约能力未被控制器合同覆盖，实际区域=" + declaredAreas);
    }

    private List<Endpoint> protectedAdminEndpoints() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        List<Endpoint> endpoints = new ArrayList<>();
        for (var bean : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller = Class.forName(bean.getBeanClassName());
            List<String> classPaths = paths(AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));
            for (Method javaMethod : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(javaMethod, RequestMapping.class);
                if (mapping == null) continue;
                List<String> methodPaths = paths(mapping);
                List<String> verbs = Arrays.stream(mapping.method()).map(Enum::name).toList();
                for (String classPath : classPaths) {
                    for (String methodPath : methodPaths) {
                        String path = representativePath(join(classPath, methodPath));
                        if (!isProtectedAdminPath(path)) continue;
                        assertFalse(verbs.isEmpty(), () -> "管理端端点必须声明 HTTP 方法："
                                + controller.getSimpleName() + "." + javaMethod.getName() + " " + path);
                        for (String verb : verbs) {
                            assertNotNull(HttpMethod.valueOf(verb));
                            endpoints.add(new Endpoint(verb, path, controller.getSimpleName(), javaMethod.getName()));
                        }
                    }
                }
            }
        }
        return endpoints.stream()
                .distinct()
                .sorted(Comparator.comparing(Endpoint::path).thenComparing(Endpoint::httpMethod))
                .toList();
    }

    private boolean isProtectedAdminPath(String path) {
        if (path.startsWith("/shop/admin")) return true;
        if (!path.startsWith("/distribution")) return false;
        // 与 AdminSecurityConfig 的显式排除项保持一致；这些路由使用登录或集成 callbackToken 自行鉴权。
        return !path.equals("/distribution/admin-auth/login")
                && !path.startsWith("/distribution/erp/callbacks/");
    }

    private List<String> paths(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) return List.of("");
        return Arrays.asList(mapping.path());
    }

    private String join(String left, String right) {
        String combined = (left == null ? "" : left) + "/" + (right == null ? "" : right);
        return combined.replaceAll("/{2,}", "/").replaceAll("/$", "");
    }

    private String representativePath(String path) {
        return path.replaceAll("\\{[^}]+}", "1");
    }

    private record Endpoint(String httpMethod, String path, String controller, String javaMethod) {
        String area() {
            String suffix = path.substring("/shop/admin".length());
            if (suffix.startsWith("/")) suffix = suffix.substring(1);
            int separator = suffix.indexOf('/');
            return separator < 0 ? suffix : suffix.substring(0, separator);
        }

        @Override
        public String toString() {
            return httpMethod + " " + path + " (" + controller + "." + javaMethod + ")";
        }
    }
}
