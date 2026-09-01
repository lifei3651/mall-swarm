package com.macro.mall.distribution.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminStepUpPolicyTest {

    @Test
    void requiresStepUpOnlyForAdminAccountAndLineRelationshipMutations() {
        assertTrue(AdminStepUpPolicy.requires("PUT", "/distribution/admin-users/8/status"));
        assertTrue(AdminStepUpPolicy.requires("PUT", "/distribution/admin-users/8/unlock"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/agent/switch-line"));
        assertTrue(AdminStepUpPolicy.requires("POST", "/distribution/agent/line-change-applications/9/audit"));
    }

    @Test
    void routineBusinessOperationsDoNotRepeatLoginPassword() {
        assertFalse(AdminStepUpPolicy.requires("GET", "/shop/admin/orders/4"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/shop/admin/orders/4/ship"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/shop/admin/orders/4/service-remark"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/shop/admin/after-sales/5/audit"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/shop/admin/after-sales/5/return-received"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/shop/admin/orders/4/refund"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/withdraw/audit"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/commission/settlement-batches/7/execute"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/import/external-team/file"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/tenant/1/config-versions/2/restore"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/distribution/merchants/3/controls"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/deposits/freeze"));
        assertFalse(AdminStepUpPolicy.requires("PUT", "/distribution/merchant-finance/withdrawals/7/review"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/audit/finance/risk-rules"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/admin-auth/step-up"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/withdrawals/7/pay"));
        assertFalse(AdminStepUpPolicy.requires("POST", "/distribution/merchant-finance/withdrawals/7/complete"));
    }
}
