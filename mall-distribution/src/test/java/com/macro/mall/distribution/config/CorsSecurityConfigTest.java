package com.macro.mall.distribution.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsSecurityConfigTest {

    @Test
    void allowsOnlyConfiguredExactOriginsAndRequiredPreflightMethods() {
        CorsConfiguration configuration = new CorsSecurityConfig(
                "https://mall.example.com,https://team.mall.example.com").buildConfiguration();

        assertEquals("https://mall.example.com", configuration.checkOrigin("https://mall.example.com"));
        assertNull(configuration.checkOrigin("https://evil.example"));
        assertTrue(configuration.checkHttpMethod(HttpMethod.POST).contains(HttpMethod.POST));
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
    }

    @Test
    void rejectsWildcardHttpAndOriginPathsButKeepsLocalDevelopmentExplicit() {
        for (String invalid : List.of("*", "http://mall.example.com", "https://*.example.com",
                "https://mall.example.com/path", "https://mall.example.com,")) {
            assertThrows(IllegalStateException.class,
                    () -> CorsSecurityConfig.parseAllowedOrigins(invalid), invalid);
        }
        assertEquals(List.of("http://localhost:5173"),
                CorsSecurityConfig.parseAllowedOrigins("http://localhost:5173"));
    }
}
