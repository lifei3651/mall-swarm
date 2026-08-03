package com.macro.mall.distribution.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumberUtilsTest {

    @Test
    void acceptsValidMainlandMobileAfterTrimming() {
        assertTrue(PhoneNumberUtils.isValidMainlandMobile(" 13800138000 "));
    }

    @Test
    void rejectsInvalidLengthPrefixAndNonDigits() {
        assertFalse(PhoneNumberUtils.isValidMainlandMobile(null));
        assertFalse(PhoneNumberUtils.isValidMainlandMobile("12800138000"));
        assertFalse(PhoneNumberUtils.isValidMainlandMobile("1380013800"));
        assertFalse(PhoneNumberUtils.isValidMainlandMobile("138-0013-8000"));
    }
}
