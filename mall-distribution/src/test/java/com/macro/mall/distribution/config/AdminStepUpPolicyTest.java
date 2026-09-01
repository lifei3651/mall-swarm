package com.macro.mall.distribution.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminStepUpPolicyTest {

    @Test
    void requiresStepUpForAccountFundsRelationshipAndRefundMutations() {
        assertTrue(AdminStepUpPolicy.requires("PUT", "/distribution/admin-users/8/status"));
        assertTrue(AdminStepUpPolicy.requires("PUT", "/shop/admin/members/9/level"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/agent/switch-line"));
        assertTrue(AdminStepUpPolicy.requires("PUT", "/distribution/merchants/3/controls"));
        assertTrue(AdminStepUpPolicy.requires("PUT", "/shop/admin/orders/4/cancel"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/shop/admin/orders/4/refund"));
        assertTrue(AdminStepUpPolicy.requires("PUT", "/shop/admin/after-sales/5/return-received"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/withdraw/audit"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/commission/settlement-batches/7/execute"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/import/external-team/file"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/tenant/1/config-versions/2/restore"));
        assertTrue(AdminStepUpPolicy.requires("PUT", "/distribution/merchants/3"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/deposits/freeze"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/withdrawals"));
        assertTrue(AdminStepUpPolicy.requires("PUT", "/distribution/merchant-finance/withdrawals/7/review"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/withdrawals/7/payment-processing"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/withdrawals/7/risk-freeze"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/audit/finance/risk-rules"));
    }

    @Test
    void preservesNormalReadFulfillmentAndServiceRemarkPaths() {
        assertFalse(AdminStepUpPolicy.requires("GET", "/shop/admin/orders/4"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/shop/admin/orders/4/ship"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/shop/admin/orders/4/service-remark"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/shop/admin/after-sales/5/audit"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/admin-auth/step-up"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/withdrawals/7/pay"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/withdrawals/7/complete"));
    }
}
