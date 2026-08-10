package com.macro.mall.distribution.util;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyValidationUtilsTest {

    private static final BigDecimal MAX_WITHDRAW = new BigDecimal("99999999.99");

    @Test
    void acceptsExactCentsAndTrailingZeros() {
        assertEquals(new BigDecimal("12.30"),
                MoneyValidationUtils.requirePositiveAmount(new BigDecimal("12.300"), "提现金额", MAX_WITHDRAW));
    }

    @Test
    void rejectsAmountsThatWouldPreviouslyBeSilentlyRounded() {
        assertThrows(ApiException.class, () -> MoneyValidationUtils.requirePositiveAmount(
                new BigDecimal("1.999"), "提现金额", MAX_WITHDRAW));
        assertThrows(ApiException.class, () -> MoneyValidationUtils.requirePositiveAmount(
                new BigDecimal("0.001"), "提现金额", MAX_WITHDRAW));
    }

    @Test
    void rejectsZeroNegativeAndDatabaseOverflow() {
        assertThrows(ApiException.class, () -> MoneyValidationUtils.requirePositiveAmount(
                BigDecimal.ZERO, "提现金额", MAX_WITHDRAW));
        assertThrows(ApiException.class, () -> MoneyValidationUtils.requirePositiveAmount(
                new BigDecimal("-1.00"), "提现金额", MAX_WITHDRAW));
        assertThrows(ApiException.class, () -> MoneyValidationUtils.requirePositiveAmount(
                new BigDecimal("100000000.00"), "提现金额", MAX_WITHDRAW));
    }
}
