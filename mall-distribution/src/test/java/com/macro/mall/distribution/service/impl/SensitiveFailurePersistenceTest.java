package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveFailurePersistenceTest {

    @Test
    void expectedImportErrorKeepsUsefulMessageButMasksCustomerData() {
        String message = ImportServiceImpl.safeRowError(
                new IllegalArgumentException("订单归属登录账号不存在: 13812345678"));

        assertEquals("订单归属登录账号不存在: 1**********", message);
    }

    @Test
    void unexpectedImportErrorDoesNotExposeDatabaseDetails() {
        String message = ImportServiceImpl.safeRowError(
                new IllegalStateException("jdbc:mysql://db:3306/mall password=secret"));

        assertFalse(message.contains("jdbc"));
        assertFalse(message.contains("secret"));
    }

    @Test
    void bonusFailureReasonIsSanitizedBeforePersistence() {
        String message = BonusCalculationTaskServiceImpl.safeFailureReason(
                new ApiException("会员13812345678 token=secret 计算失败"));

        assertEquals("会员1********** token=*** 计算失败", message);
    }
}
