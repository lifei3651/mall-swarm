package com.macro.mall.distribution.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.log.RequestIdContext;
import com.macro.mall.distribution.service.impl.RequestCorrelationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearContext() {
        RequestIdContext.clear();
    }

    @Test
    void shouldReturnSameRequestIdInHeaderJsonContextAndRequestAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/products");
        request.addHeader(RequestCorrelationFilter.HEADER, "gateway-req-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> jsonRequestId = new AtomicReference<>();
        AtomicReference<String> attributeRequestId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            jsonRequestId.set(CommonResult.success("ok").getRequestId());
            attributeRequestId.set(String.valueOf(
                    servletRequest.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE)));
        });

        assertEquals("gateway-req-12345678", response.getHeader(RequestCorrelationFilter.HEADER));
        assertEquals("gateway-req-12345678", jsonRequestId.get());
        assertEquals("gateway-req-12345678", attributeRequestId.get());
        assertNull(RequestIdContext.get(), "request completion must clear the reusable thread context");
    }

    @Test
    void shouldRejectUnsafeSuppliedIdAndGenerateSafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/products");
        request.addHeader(RequestCorrelationFilter.HEADER, "bad\nlog-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String generated = response.getHeader(RequestCorrelationFilter.HEADER);
        assertNotEquals("bad\nlog-id", generated);
        assertTrue(generated.matches("^[0-9a-f-]{36}$"));
        assertNull(RequestIdContext.get());
    }
}
