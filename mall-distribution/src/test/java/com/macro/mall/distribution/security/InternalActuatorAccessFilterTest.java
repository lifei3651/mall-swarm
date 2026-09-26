package com.macro.mall.distribution.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalActuatorAccessFilterTest {

    private final InternalActuatorAccessFilter filter = new InternalActuatorAccessFilter();

    @Test
    void rejectsPublicPeerEvenWithTrustedLookingForwardedHeaders() throws Exception {
        for (String path : new String[]{"/actuator", "/actuator/prometheus", "/actuator/metrics",
                "/api/actuator/prometheus", "/api/v1/actuator/prometheus",
                "/%61ctuator/prometheus", "/actuator%2Fprometheus"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRemoteAddr("203.0.113.19");
            request.addHeader("X-Forwarded-For", "127.0.0.1");
            request.addHeader("X-Real-IP", "10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean passed = new AtomicBoolean();
            FilterChain chain = (ignoredRequest, ignoredResponse) -> passed.set(true);

            filter.doFilter(request, response, chain);

            assertEquals(404, response.getStatus(), path);
            assertEquals("no-store", response.getHeader("Cache-Control"));
            assertFalse(passed.get(), path);
        }
    }

    @Test
    void ignoresForwardedHeaderFilterStyleRequestWrappers() throws Exception {
        MockHttpServletRequest socketRequest = new MockHttpServletRequest("GET", "/actuator/prometheus");
        socketRequest.setRemoteAddr("198.51.100.10");
        HttpServletRequestWrapper forwarded = new HttpServletRequestWrapper(socketRequest) {
            @Override public String getRemoteAddr() { return "127.0.0.1"; }
            @Override public String getRequestURI() { return "/shop/products"; }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> passed.set(true);

        filter.doFilter(forwarded, response, chain);

        assertEquals(404, response.getStatus());
        assertFalse(passed.get());
    }

    @Test
    void permitsOnlyLoopbackAndPrivateNetworkPeers() throws Exception {
        for (String internal : new String[]{"127.0.0.1", "::1", "10.5.1.2", "172.19.0.4",
                "192.168.2.3", "fd00::10"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
            request.setRemoteAddr(internal);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean passed = new AtomicBoolean();
            FilterChain chain = (ignoredRequest, ignoredResponse) -> passed.set(true);

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(), internal);
            assertTrue(passed.get(), internal);
        }
        for (String external : new String[]{"8.8.8.8", "2001:4860:4860::8888",
                "169.254.1.1", "100.64.0.1", "172.32.0.1", "fe80::1", "localhost",
                "", "0.0.0.0"}) {
            assertFalse(InternalActuatorAccessFilter.isInternalAddress(external), external);
        }
        assertTrue(InternalActuatorAccessFilter.isInternalAddress("::ffff:192.168.1.4"));
    }

    @Test
    void leavesOrdinaryBusinessPathsAloneAndHandlesContextPath() throws Exception {
        MockHttpServletRequest ordinary = new MockHttpServletRequest("GET", "/shop/products");
        ordinary.setRemoteAddr("203.0.113.19");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean passed = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> passed.set(true);
        filter.doFilter(ordinary, response, chain);
        assertTrue(passed.get());

        MockHttpServletRequest prefixed = new MockHttpServletRequest("GET", "/mall/actuator/prometheus");
        prefixed.setContextPath("/mall");
        prefixed.setRemoteAddr("203.0.113.19");
        MockHttpServletResponse prefixedResponse = new MockHttpServletResponse();
        AtomicBoolean prefixedPassed = new AtomicBoolean();
        FilterChain prefixedChain = (ignoredRequest, ignoredResponse) -> prefixedPassed.set(true);
        filter.doFilter(prefixed, prefixedResponse, prefixedChain);
        assertEquals(404, prefixedResponse.getStatus());
        assertFalse(prefixedPassed.get());
    }
}
