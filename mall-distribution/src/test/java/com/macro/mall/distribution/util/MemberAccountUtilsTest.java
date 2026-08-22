package com.macro.mall.distribution.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberAccountUtilsTest {

    @Test
    void masksMemberAndPaymentIdentifiersForListResponses() {
        assertEquals("138****8000", MemberAccountUtils.maskPhone("13800138000"));
        assertEquals("te***23", MemberAccountUtils.maskAccount("test123"));
        assertEquals("张*", MemberAccountUtils.maskPersonName("张三"));
    }
}
