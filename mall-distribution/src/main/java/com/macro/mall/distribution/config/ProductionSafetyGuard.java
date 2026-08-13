package com.macro.mall.distribution.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 危险测试能力保险丝：不能只依赖 profile 名为 prod，因为遗漏 profile 本身就是常见误部署。
 */
@Component
@RequiredArgsConstructor
public class ProductionSafetyGuard {

    private static final Set<String> SAFE_TEST_PROFILES = Set.of("local", "test");

    private final Environment environment;

    @PostConstruct
    void validate() {
        String testCode = environment.getProperty("sms.test-code", "");
        boolean dangerousTestFeature = enabled("shop.payment.simulation-enabled")
                || enabled("sms.expose-code") || (testCode != null && !testCode.isBlank());
        if (dangerousTestFeature && !hasOnlyExplicitTestProfiles()) {
            throw new IllegalStateException("模拟支付和固定验证码只能在显式 local/test 环境启用");
        }
        if (isProductionProfile()) {
            requireFalse("shop.payment.simulation-enabled");
            requireFalse("sms.expose-code");
            requireFalse("springdoc.api-docs.enabled");
            requireFalse("springdoc.swagger-ui.enabled");
            if (testCode != null && !testCode.isBlank()) {
                throw new IllegalStateException("生产环境禁止配置短信测试验证码");
            }
        }
        if (Boolean.parseBoolean(environment.getProperty("payment.verification.enabled", "false"))
                && !Boolean.parseBoolean(environment.getProperty("sms.provider-enabled", "false"))) {
            throw new IllegalStateException("启用大额支付短信验证前必须先启用正式短信服务");
        }
    }

    private void requireFalse(String key) {
        if (enabled(key)) {
            throw new IllegalStateException("生产环境禁止开启危险配置：" + key);
        }
    }

    private boolean enabled(String key) {
        return Boolean.parseBoolean(environment.getProperty(key, "false"));
    }

    private boolean isProductionProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private boolean hasOnlyExplicitTestProfiles() {
        String[] active = environment.getActiveProfiles();
        return active.length > 0 && Arrays.stream(active).allMatch(SAFE_TEST_PROFILES::contains);
    }
}
