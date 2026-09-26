package com.macro.mall.distribution.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Defense in depth: management endpoints must never be reached from a public socket peer. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalActuatorAccessFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        HttpServletRequest socketRequest = unwrap(request);
        String uri = socketRequest.getRequestURI();
        String contextPath = socketRequest.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return !isActuatorPath(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // ForwardedHeaderFilter can wrap both URI and remote address; inspect the original servlet request.
        if (!isInternalAddress(unwrap(request).getRemoteAddr())) {
            response.setHeader("Cache-Control", "no-store");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    private static HttpServletRequest unwrap(HttpServletRequest request) {
        HttpServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest nested) {
            current = nested;
        }
        return current;
    }

    static boolean isActuatorPath(String path) {
        if (path == null) {
            return false;
        }
        if (matchesActuatorPath(path)) {
            return true;
        }
        try {
            return matchesActuatorPath(URLDecoder.decode(path, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean matchesActuatorPath(String path) {
        return isPathOrChild(path, "/actuator")
                || isPathOrChild(path, "/api/actuator")
                || isPathOrChild(path, "/api/v1/actuator");
    }

    private static boolean isPathOrChild(String path, String prefix) {
        return prefix.equals(path) || path.startsWith(prefix + "/");
    }

    static boolean isInternalAddress(String remoteAddress) {
        if (remoteAddress == null) {
            return false;
        }
        try {
            InetAddress address = parseNumericAddress(remoteAddress);
            if (address == null) {
                return false;
            }
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc; // IPv6 unique local (fc00::/7)
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private static InetAddress parseNumericAddress(String remoteAddress) throws UnknownHostException {
        if (remoteAddress.contains(":")) {
            // A colon requires IPv6 syntax, so this cannot trigger a hostname lookup.
            return remoteAddress.matches("[0-9a-fA-F:.]+") ? InetAddress.getByName(remoteAddress) : null;
        }
        String[] octets = remoteAddress.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }
        byte[] raw = new byte[4];
        for (int i = 0; i < octets.length; i++) {
            if (!octets[i].matches("[0-9]{1,3}")) {
                return null;
            }
            int value = Integer.parseInt(octets[i]);
            if (value > 255) {
                return null;
            }
            raw[i] = (byte) value;
        }
        return InetAddress.getByAddress(raw);
    }
}
