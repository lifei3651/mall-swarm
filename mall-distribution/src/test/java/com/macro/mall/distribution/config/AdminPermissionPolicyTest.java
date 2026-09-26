package com.macro.mall.distribution.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminPermissionPolicyTest {

    @Test
    void merchantMayListAndReplyButNotModerateOrSpoofPlatformReviewActions() {
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/reviews/18/reply"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest("GET", "/shop/admin/reviews"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest("PUT", "/shop/admin/reviews/18/reply"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest("PUT", "/shop/admin/reviews/18/status"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest("DELETE", "/shop/admin/reviews/18/reply"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest("POST", "/shop/admin/reviews"));
    }

    @Test
    void mapsEveryPreviouslyOmittedShopAdminAreaToItsBusinessPermission() {
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/categories"));
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/service-addresses"));
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/flash-sales/1"));
        assertEquals("shop:product-review", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/merchant-product-reviews"));
        assertEquals("shop:product-review", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/merchant-product-reviews/1/decision"));
        assertEquals("config:shop", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/banners"));
        assertEquals("config:shop", AdminPermissionPolicy.requiredPermission("DELETE", "/shop/admin/notices/1"));
        assertEquals("config:shop", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/message-operations/templates"));
        assertEquals("config:shop", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/media/brand-culture"));
        assertEquals("config:bonus", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/product-settings/pv"));
        assertEquals("config:bonus", AdminPermissionPolicy.requiredPermission("PUT", "/distribution/bonus-config/display/1"));
        assertEquals("config:integration", AdminPermissionPolicy.requiredPermission("POST", "/distribution/erp/integrations"));
        assertEquals("config:integration", AdminPermissionPolicy.requiredPermission("POST", "/distribution/erp/tasks"));
    }

    @Test
    void businessModesUseDedicatedBonusPermissionWithoutBroadeningTenantProfileApis() {
        assertEquals("config:bonus", AdminPermissionPolicy.requiredPermission(
                "GET", "/distribution/tenant/1/business-modes"));
        assertEquals("config:bonus", AdminPermissionPolicy.requiredPermission(
                "PUT", "/distribution/tenant/1/business-modes"));
        assertEquals("config:shop", AdminPermissionPolicy.requiredPermission(
                "GET", "/distribution/tenant/list"));
        assertEquals("config:shop", AdminPermissionPolicy.requiredPermission(
                "POST", "/distribution/tenant"));
        assertEquals("config:shop", AdminPermissionPolicy.requiredPermission(
                "PUT", "/distribution/tenant/1/status"));
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
        assertEquals("finance:read", AdminPermissionPolicy.requiredPermission("GET", "/distribution/withdraw/list"));
        assertEquals("finance:manage", AdminPermissionPolicy.requiredPermission("GET", "/distribution/withdraw/18"));
        assertEquals("finance:manage", AdminPermissionPolicy.requiredPermission("GET", "/distribution/withdraw/pending-audit"));
    }

    @Test
    void separatesOrderShipmentFromRefundAuthority() {
        assertEquals("shop:order", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/orders/99/ship"));
        assertEquals("shop:order", AdminPermissionPolicy.requiredPermission("DELETE", "/shop/admin/orders/99/wechat-express/77"));
        assertEquals("shop:order", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/wechat-express/options"));
        assertEquals("shop:order", AdminPermissionPolicy.requiredPermission("GET", "/shop/admin/trades/88"));
        assertEquals("shop:order", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/service-tickets/18/replies"));
        assertEquals("shop:aftersale", AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/orders/99/refund"));
        assertEquals("shop:aftersale", AdminPermissionPolicy.requiredPermission("PUT", "/shop/admin/orders/99/cancel"));
        assertEquals("shop:aftersale", AdminPermissionPolicy.requiredPermission(
                "PUT", "/shop/admin/after-sales/88/exchange-shipment"));
        assertEquals("shop:aftersale", AdminPermissionPolicy.requiredPermission(
                "PUT", "/shop/admin/after-sales/88/unshipped-refund"));
        assertNull(AdminPermissionPolicy.requiredPermission("POST", "/shop/admin/wechat-express/options"));
    }

    @Test
    void separatesMerchantCatalogFromMerchantFunds() {
        assertEquals("shop:product", AdminPermissionPolicy.requiredPermission("GET", "/distribution/merchants/options"));
        assertEquals("system:manage", AdminPermissionPolicy.requiredPermission("GET", "/distribution/merchants"));
        assertEquals("system:manage", AdminPermissionPolicy.requiredPermission("POST", "/distribution/merchants"));
        assertEquals("merchant:staff-manage", AdminPermissionPolicy.requiredPermission("GET", "/distribution/merchants/current-profile"));
        assertEquals("merchant:staff-manage", AdminPermissionPolicy.requiredPermission("PUT", "/distribution/merchants/current-profile"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest("GET", "/distribution/merchants/options"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest("PUT", "/distribution/merchants/current-profile"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest("GET", "/distribution/merchants"));
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
                "GET", "/shop/admin/service-tickets"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/service-tickets/18/replies"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/orders/99/ship"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/orders/99/wechat-express"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "GET", "/shop/admin/wechat-express/options"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "DELETE", "/shop/admin/orders/99/wechat-express/77"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/orders/99/service-remark"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "GET", "/shop/admin/after-sales"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/after-sales/88/audit"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/after-sales/88/return-received"));
        assertTrue(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/after-sales/88/exchange-shipment"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/after-sales/88/unshipped-refund"));

        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "PUT", "/shop/admin/orders/99/cancel"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/orders/99/refund"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "GET", "/shop/admin/trades/88"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/orders/shipments/import"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/wechat-express/options"));
        assertFalse(AdminSecurityConfig.AdminSecurityInterceptor.isMerchantWorkspaceRequest(
                "POST", "/shop/admin/after-sales/88/exchange-shipment"));
    }
}
