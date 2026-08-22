package com.macro.mall.distribution.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProductionSecurityConfigTest {

    @Test
    void baseSaTokenConfigurationDoesNotAcceptBrowserCookies() throws IOException {
        String configuration;
        try (var input = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            if (input == null) throw new IOException("application.yml not found");
            configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(configuration.contains("is-read-header: true"));
        assertTrue(configuration.contains("is-read-cookie: false"));
        assertTrue(configuration.contains("same-site: Strict"));
    }

    @Test
    void productionGuardAcceptsOnlySafeSwitches() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("shop.payment.simulation-enabled", "false")
                .withProperty("sms.expose-code", "false")
                .withProperty("sms.test-code", "")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("springdoc.swagger-ui.enabled", "false");

        new ProductionSafetyGuard(environment).validate();
    }

    @Test
    void productionGuardRejectsSimulationPaymentAndTestCode() {
        MockEnvironment simulation = new MockEnvironment()
                .withProperty("shop.payment.simulation-enabled", "true");
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(simulation).validate());

        MockEnvironment testCode = new MockEnvironment()
                .withProperty("sms.test-code", "123456");
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(testCode).validate());

        MockEnvironment missingSmsProvider = new MockEnvironment()
                .withProperty("payment.verification.enabled", "true")
                .withProperty("sms.provider-enabled", "false");
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGuard(missingSmsProvider).validate());
    }

    @Test
    void productionGuardRejectsRootDatabaseAndWeakPassword() {
        MockEnvironment rootDatabase = safeProductionEnvironment()
                .withProperty("spring.datasource.username", "root")
                .withProperty("spring.datasource.password", "strong-production-secret");
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(rootDatabase).validate());

        MockEnvironment weakPassword = safeProductionEnvironment()
                .withProperty("spring.datasource.username", "mall_app")
                .withProperty("spring.datasource.password", "password");
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(weakPassword).validate());
    }

    @Test
    void productionGuardRequiresStrongRedisPassword() {
        MockEnvironment missingRedisPassword = safeProductionEnvironment()
                .withProperty("spring.datasource.username", "mall_app")
                .withProperty("spring.datasource.password", "strong-production-database-secret");
        assertThrows(IllegalStateException.class,
                () -> new ProductionSafetyGuard(missingRedisPassword).validate());

        MockEnvironment secure = safeProductionEnvironment()
                .withProperty("spring.datasource.username", "mall_app")
                .withProperty("spring.datasource.password", "strong-production-database-secret")
                .withProperty("spring.data.redis.password", "redis-strong-random-secret-1234567890")
                .withProperty("security.data-encryption-key",
                        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        new ProductionSafetyGuard(secure).validate();
    }

    @Test
    void productionGuardRequiresIndependentDataEncryptionKey() {
        MockEnvironment missing = safeProductionEnvironment()
                .withProperty("spring.datasource.username", "mall_app")
                .withProperty("spring.datasource.password", "strong-production-database-secret")
                .withProperty("spring.data.redis.password", "redis-strong-random-secret-1234567890");
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(missing).validate());

        MockEnvironment malformed = safeProductionEnvironment()
                .withProperty("spring.datasource.username", "mall_app")
                .withProperty("spring.datasource.password", "strong-production-database-secret")
                .withProperty("spring.data.redis.password", "redis-strong-random-secret-1234567890")
                .withProperty("security.data-encryption-key", "too-short");
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(malformed).validate());
    }

    @Test
    void productionGuardRequiresAlipaySellerIdWhenPaymentIsEnabled() {
        MockEnvironment missingSeller = completeProductionEnvironment()
                .withProperty("alipay.enabled", "true")
                .withProperty("alipay.appId", "app-1")
                .withProperty("alipay.privateKey", "private-key")
                .withProperty("alipay.alipayPublicKey", "alipay-public-key");

        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(missingSeller).validate());

        missingSeller.withProperty("alipay.sellerId", "2088123456789012");
        new ProductionSafetyGuard(missingSeller).validate();
    }

    @Test
    void securityHeadersProtectSensitiveHttpsResponses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/wallet/summary");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityHeadersFilter().doFilter(request, response, mock(FilterChain.class));

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("max-age=31536000; includeSubDomains",
                response.getHeader("Strict-Transport-Security"));
    }

    private MockEnvironment safeProductionEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("shop.payment.simulation-enabled", "false")
                .withProperty("sms.expose-code", "false")
                .withProperty("sms.test-code", "")
                .withProperty("springdoc.api-docs.enabled", "false")
                .withProperty("springdoc.swagger-ui.enabled", "false");
        environment.setActiveProfiles("prod");
        return environment;
    }

    private MockEnvironment completeProductionEnvironment() {
        return safeProductionEnvironment()
                .withProperty("spring.datasource.username", "mall_app")
                .withProperty("spring.datasource.password", "strong-production-database-secret")
                .withProperty("spring.data.redis.password", "redis-strong-random-secret-1234567890")
                .withProperty("security.data-encryption-key",
                        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    }
}
