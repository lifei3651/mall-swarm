package com.macro.mall.distribution.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/** 只允许客户部署时明确登记的 HTTPS 前端跨域携带会话。 */
@Configuration
public class CorsSecurityConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsSecurityConfig(@Value("${security.cors.allowed-origins:}") String configuredOrigins) {
        this.allowedOrigins = parseAllowedOrigins(configuredOrigins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) return;
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                        HttpMethod.DELETE.name(), HttpMethod.PATCH.name(), HttpMethod.OPTIONS.name())
                .allowedHeaders(CorsConfiguration.ALL)
                .exposedHeaders("Content-Disposition", "Retry-After")
                .allowCredentials(true)
                .maxAge(3600);
    }

    CorsConfiguration buildConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(CorsConfiguration.ALL));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        return configuration;
    }

    static List<String> parseAllowedOrigins(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) return List.of();
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        Arrays.stream(configuredOrigins.split(",", -1)).map(String::trim).forEach(origin -> {
            if (origin.isEmpty()) throw new IllegalStateException("CORS 来源配置中存在空项");
            URI uri;
            try {
                uri = URI.create(origin);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("CORS 来源格式不正确: " + origin, e);
            }
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean localHttp = "http".equalsIgnoreCase(scheme)
                    && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
            boolean exactOrigin = host != null && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
            if (!exactOrigin || !("https".equalsIgnoreCase(scheme) || localHttp)
                    || origin.contains("*")) {
                throw new IllegalStateException("CORS 只允许明确的 HTTPS 来源（本机开发地址除外）: " + origin);
            }
            origins.add(origin);
        });
        return List.copyOf(origins);
    }
}
