package com.macro.mall.distribution.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ProductionSecurityConfigTest {

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
}
