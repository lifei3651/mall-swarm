package com.macro.mall.distribution.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationLimitFilterTest {

    private final PaginationLimitFilter filter = new PaginationLimitFilter();

    @Test
    void rejectsUnboundedAndAmbiguousPageSizesBeforeControllerExecution() throws Exception {
        for (String value : new String[]{"0", "101", "999999", "not-a-number"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/distribution/agent/list");
            request.addParameter("pageSize", value);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean invoked = new AtomicBoolean();
            FilterChain chain = (servletRequest, servletResponse) -> invoked.set(true);

            filter.doFilter(request, response, chain);

            assertEquals(400, response.getStatus(), value);
            assertTrue(response.getContentAsString().contains("1至100"));
            assertFalse(invoked.get());
        }
    }

    @Test
    void acceptsOrdinaryPageSize() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/distribution/agent/list");
        request.addParameter("pageSize", "20");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> invoked.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(invoked.get());
    }
}
