package com.macro.mall.distribution.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AdminSessionCookieService {

    public static final String COOKIE_NAME = "admin_session";
    public static final String CLIENT_HEADER = "X-Admin-Client";
    public static final String CLIENT_HEADER_VALUE = "admin-web";
    private static final String COOKIE_PATH = "/api";

    public void write(HttpServletRequest request, HttpServletResponse response, String token, LocalDateTime expireTime) {
        if (token == null || token.isBlank()) return;
        long maxAge = Duration.between(LocalDateTime.now(), expireTime == null
                ? LocalDateTime.now().plusDays(7) : expireTime).getSeconds();
        response.addHeader("Set-Cookie", buildCookie(request, token, Math.max(maxAge, 1)).toString());
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie(request, "", 0).toString());
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(HttpServletRequest request, String value, long maxAge) {
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "Strict" : "Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
