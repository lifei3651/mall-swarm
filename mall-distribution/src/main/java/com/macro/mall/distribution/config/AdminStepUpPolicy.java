package com.macro.mall.distribution.config;

import org.springframework.http.HttpMethod;

/** 需要重新验证当前管理员密码的高影响写操作。 */
final class AdminStepUpPolicy {

    static final String HEADER = "X-Admin-Step-Up-Token";

    private AdminStepUpPolicy() {
    }

    static boolean requires(String method, String path) {
        if (path == null) return false;
        if (HttpMethod.PUT.matches(method) && (path.matches("/distribution/admin-users/[^/]+/(status|unlock)")
                || path.matches("/shop/admin/members/[^/]+/(status|unlock|payment-password/unlock|level)")
                || path.matches("/distribution/agent/[^/]+/(status|level)")
                || path.matches("/distribution/merchants/[^/]+/(status|controls)")
                || path.matches("/shop/admin/orders/[^/]+/cancel")
                || path.matches("/shop/admin/after-sales/[^/]+/(audit|return-received)"))) return true;
        if (HttpMethod.POST.matches(method) && (path.equals("/distribution/agent/switch-line")
                || path.matches("/distribution/agent/line-change-applications/[^/]+/audit")
                || path.matches("/shop/admin/orders/[^/]+/refund")
                || path.equals("/distribution/withdraw/audit")
                || path.matches("/distribution/commission/(settle/[^/]+|settle-batch|cancel/[^/]+)")
                || path.equals("/distribution/commission/settlement-batches")
                || path.matches("/distribution/commission/settlement-batches/[^/]+/execute")
                || path.equals("/distribution/import/external-team/file")
                || path.matches("/distribution/tenant/[^/]+/config-versions/[^/]+/restore"))) return true;
        return false;
    }
}
