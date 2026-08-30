package com.macro.mall.common.api;

import com.macro.mall.common.log.RequestIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonResultRequestIdTest {

    @AfterEach
    void clearContext() {
        RequestIdContext.clear();
    }

    @Test
    void shouldExposeCurrentRequestIdInSuccessAndFailureResponses() {
        RequestIdContext.set("req-test-12345678");

        assertEquals("req-test-12345678", CommonResult.success("ok").getRequestId());
        assertEquals("req-test-12345678", CommonResult.failed("failed").getRequestId());
        assertTrue(CommonResult.failed(429, "slow down").toString()
                .contains("\"requestId\":\"req-test-12345678\""));
    }

    @Test
    void shouldLeaveRequestIdEmptyOutsideHttpRequest() {
        assertNull(CommonResult.success("ok").getRequestId());
    }
}
