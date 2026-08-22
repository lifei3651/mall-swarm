package com.macro.mall.distribution.config;

import org.springframework.http.HttpMethod;

/** 后台接口的唯一服务端权限矩阵；未登记路径一律拒绝。 */
final class AdminPermissionPolicy {

    private AdminPermissionPolicy() {
    }

    static String requiredPermission(String method, String path) {
        if (path == null || path.indexOf(';') >= 0 || path.indexOf('\\') >= 0) return null;
        if (path.matches("/shop/admin/members/[^/]+/level")) return "distribution:manage";
        if (HttpMethod.POST.matches(method) && path.matches("/distribution/agent/line-change-applications/[^/]+/audit")) {
            return "line-change:apply";
        }
        if (HttpMethod.POST.matches(method) && path.equals("/distribution/agent/switch-line")) return "line-change:apply";
        if (HttpMethod.GET.matches(method) && path.equals("/distribution/agent/line-change-applications")) return "line-change:apply";
        if (path.startsWith("/distribution/admin-auth/")) return "admin:read";
        if (path.startsWith("/distribution/admin-users")) return "system:manage";
        if (path.startsWith("/distribution/operation-logs")) return "system:manage";
        if (path.startsWith("/distribution/erp")) return "config:integration";
        if (path.startsWith("/distribution/bonus-config") || path.startsWith("/distribution/audit/settings")) {
            return "config:bonus";
        }
        if (HttpMethod.PUT.matches(method) && path.equals("/shop/admin/product-settings/pv")) return "config:bonus";
        if (path.startsWith("/distribution/tenant")) return "config:shop";
        if (path.startsWith("/shop/admin/banners") || path.startsWith("/shop/admin/notices")) return "config:shop";
        if (path.startsWith("/shop/admin/merchant-product-reviews")) return "shop:product-review";
        if (path.startsWith("/shop/admin/products") || path.startsWith("/shop/admin/skus")
                || path.startsWith("/shop/admin/media") || path.startsWith("/shop/admin/freight-templates")
                || path.startsWith("/shop/admin/product-settings") || path.startsWith("/shop/admin/reviews")
                || path.startsWith("/shop/admin/categories") || path.startsWith("/shop/admin/service-addresses")
                || path.startsWith("/shop/admin/flash-sales")) return "shop:product";
        if (path.startsWith("/distribution/merchants")) return "shop:product";
        if (HttpMethod.POST.matches(method) && path.matches("/shop/admin/orders/[^/]+/refund")) return "shop:aftersale";
        if (HttpMethod.PUT.matches(method) && path.matches("/shop/admin/orders/[^/]+/cancel")) return "shop:aftersale";
        if (path.startsWith("/shop/admin/trades")) return "shop:order";
        if (path.startsWith("/shop/admin/orders") || path.startsWith("/shop/admin/events/orders")) return "shop:order";
        if (path.startsWith("/shop/admin/after-sales")) return "shop:aftersale";
        if (path.startsWith("/shop/admin/members")) return "shop:member";
        if (path.startsWith("/distribution/import")) return "import:manage";
        if (path.startsWith("/distribution/commission")) return "commission:manage";
        if (path.startsWith("/distribution/agent") || path.startsWith("/distribution/performance")
                || path.startsWith("/distribution/account")) return "distribution:manage";
        if (path.startsWith("/distribution/dashboard")) return "admin:read";
        if (HttpMethod.GET.matches(method) && (path.matches("/distribution/withdraw/\\d+")
                || path.equals("/distribution/withdraw/pending-audit"))) return "finance:manage";
        if (path.startsWith("/distribution/audit/finance") || path.startsWith("/distribution/withdraw")
                || path.startsWith("/distribution/assets") || path.startsWith("/distribution/order-asset-payments")
                || path.startsWith("/distribution/audit/orders") || path.startsWith("/distribution/audit/bonus-sources")
                || path.startsWith("/distribution/audit/person-profile")) {
            return HttpMethod.GET.matches(method) ? "finance:read" : "finance:manage";
        }
        if (path.startsWith("/distribution/merchant-finance")) {
            return HttpMethod.GET.matches(method) ? "finance:read" : "finance:manage";
        }
        return null;
    }
}
