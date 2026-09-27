package com.macro.mall.distribution.security;

import com.github.pagehelper.PageHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaginationLimitFilterTest {

    private final PaginationLimitFilter filter = new PaginationLimitFilter();

    @AfterEach
    void clearPagination() {
        PageHelper.clearPage();
    }

    @Test
    void unconsumedDefaultPaginationCannotLeakToNextLoginOnSameThread() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/distribution/admin-users"),
                new MockHttpServletResponse(), (request, response) -> {
                    PageHelper.startPage(1, 10);
                    assertNotNull(PageHelper.getLocalPage());
                    ((jakarta.servlet.http.HttpServletResponse) response).setStatus(403);
                });
        assertNull(PageHelper.getLocalPage());
        filter.doFilter(new MockHttpServletRequest("POST", "/distribution/admin-auth/login"),
                new MockHttpServletResponse(), (request, response) -> assertNull(PageHelper.getLocalPage()));
    }

    @Test
    void exceptionsStillClearUnconsumedPagination() {
        assertThrows(ServletException.class, () -> filter.doFilter(
                new MockHttpServletRequest("GET", "/distribution/admin-users"),
                new MockHttpServletResponse(), (request, response) -> {
                    PageHelper.startPage(1, 10);
                    throw new ServletException("permission denied before first query");
                }));
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void emptyResultWithoutSqlStillClearsPagination() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/shop/admin/orders"),
                new MockHttpServletResponse(), (request, response) -> PageHelper.startPage(2, 20));
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void staleStateIsClearedBeforeAuthenticationAndInvalidParameters() throws Exception {
        PageHelper.startPage(1, 10);
        filter.doFilter(new MockHttpServletRequest("GET", "/distribution/admin-auth/me"),
                new MockHttpServletResponse(), (request, response) -> assertNull(PageHelper.getLocalPage()));
        PageHelper.startPage(1, 10);
        MockHttpServletRequest invalid = new MockHttpServletRequest("GET", "/shop/admin/orders");
        invalid.addParameter("pageSize", "101");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(invalid, response, (req, res) -> { throw new AssertionError("must reject"); });
        assertEquals(400, response.getStatus());
        assertNull(PageHelper.getLocalPage());
    }

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
