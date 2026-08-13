package com.macro.mall.distribution.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminPermissionPolicyTest {

    @Test
    void mapsEveryPreviouslyOmittedShopAdminAreaToItsBusinessPermission() {
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/categories"));
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/service-addresses"));
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/flash-sales/1"));
        assertEquals("config:manage", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/banners"));
        assertEquals("config:manage", AdminPermissionPolicy.requiredPermission("DELETE", "/shop/admin/notices/1"));
    }

    @Test
    void failsClosedForUnknownOrAmbiguousAdminPaths() {
        assertNull(AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/new-sensitive-area"));
        assertNull(AdminPermissionPolicy.requiredPermission("POST", "/distribution;x/admin-users"));
        assertNull(AdminPermissionPolicy.requiredPermission("POST", "/distribution/admin-users;anything"));
    }

    @Test
    void preservesExistingDashboardAndFinanceReadAccess() {
        assertEquals("admin:read", AdminPermissionPolicy.requiredPermission("GET", "/distribution/dashboard"));
        assertEquals("finance:read", AdminPermissionPolicy.requiredPermission("GET", "/distribution/audit/finance/summary"));
        assertEquals("finance:manage", AdminPermissionPolicy.requiredPermission("POST", "/distribution/audit/finance/refunds"));
    }
}
