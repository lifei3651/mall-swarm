package com.macro.mall.distribution.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminPermissionPolicyTest {

    @Test
    void mapsEveryPreviouslyOmittedShopAdminAreaToItsBusinessPermission() {
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/categories"));
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/service-addresses"));
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/flash-sales/1"));
        assertEquals("shop:product-review", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/merchant-product-reviews"));
        assertEquals("shop:product-review", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/merchant-product-reviews/1/decision"));
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

    @Test
    void separatesOrderShipmentFromRefundAuthority() {
        assertEquals("shop:order", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/orders/99/ship"));
        assertEquals("shop:order", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/trades/88"));
        assertEquals("shop:aftersale", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/orders/99/refund"));
        assertEquals("shop:aftersale", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/orders/99/cancel"));
    }

    @Test
    void separatesMerchantCatalogFromMerchantFunds() {
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("POST", "/distribution/merchants"));
        assertEquals("finance:read", AdminPermissionPolicy.requiredPermission("GET", "/distribution/merchant-finance/accounts"));
        assertEquals("finance:manage", AdminPermissionPolicy.requiredPermission("POST", "/distribution/merchant-finance/withdrawals/1/pay"));
    }

    @Test
    void merchantWorkspaceCannotReachPlatformDashboardButCanManageItsOwnAddressesAndWithdrawal() {
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "GET", "/distribution/dashboard"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "GET", "/shop/admin/service-addresses"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/service-addresses"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/distribution/merchant-finance/withdrawals"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/distribution/merchant-finance/withdrawals/12/cancel"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/distribution/merchant-finance/withdrawals/12/risk-freeze"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/distribution/merchant-finance/deposits/receive"));
    }

    @Test
    void suspendedMerchantWorkspaceKeepsHistoricalFulfillmentWithoutPlatformRefundAuthority() {
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "GET", "/shop/admin/orders"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/orders/99/ship"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/orders/99/service-remark"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "GET", "/shop/admin/after-sales"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/after-sales/88/audit"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/after-sales/88/return-received"));

        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/orders/99/cancel"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/orders/99/refund"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "GET", "/shop/admin/trades/88"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/orders/shipments/import"));
    }
}
