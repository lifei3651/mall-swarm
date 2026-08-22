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
            String databaseUser = environment.getProperty("spring.datasource.username", "");
            String databasePassword = environment.getProperty("spring.datasource.password", "");
            if (databaseUser.isBlank() || "root".equalsIgnoreCase(databaseUser.trim())) {
                throw new IllegalStateException("生产环境数据库必须使用独立最小权限账号，禁止使用 root");
            }
            if (databasePassword.isBlank() || isWeakSecret(databasePassword)) {
                throw new IllegalStateException("生产环境数据库密码缺失或仍是示例弱口令");
            }
            String redisPassword = environment.getProperty("spring.data.redis.password", "");
            if (redisPassword.length() < 32 || isWeakSecret(redisPassword)) {
                throw new IllegalStateException("生产环境Redis密码必须为至少32位的独立强随机密钥");
            }
            String dataEncryptionKey = environment.getProperty("security.data-encryption-key", "");
            if (!dataEncryptionKey.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalStateException("生产环境必须配置64位十六进制敏感字段加密密钥");
            }
            if (enabled("alipay.enabled")) {
                requireConfigured("alipay.appId", "生产环境启用支付宝前必须配置APPID");
                requireConfigured("alipay.sellerId", "生产环境启用支付宝前必须配置收款商户PID");
                requireConfigured("alipay.privateKey", "生产环境启用支付宝前必须配置应用私钥");
                requireConfigured("alipay.alipayPublicKey", "生产环境启用支付宝前必须配置支付宝公钥");
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

    private void requireConfigured(String key, String message) {
        if (environment.getProperty(key, "").isBlank()) throw new IllegalStateException(message);
    }

    private boolean isProductionProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private boolean hasOnlyExplicitTestProfiles() {
        String[] active = environment.getActiveProfiles();
        return active.length > 0 && Arrays.stream(active).allMatch(SAFE_TEST_PROFILES::contains);
    }

    private boolean isWeakSecret(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return Set.of("root", "password", "admin", "123456", "change_me", "changeme").contains(normalized);
    }
}
