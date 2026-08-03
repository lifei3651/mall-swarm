package com.macro.mall.distribution.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 生产配置保险丝：危险的测试开关被环境变量或配置中心误开启时拒绝启动。
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSafetyGuard {

    private final Environment environment;

    @PostConstruct
    void validate() {
        requireFalse("shop.payment.simulation-enabled");
        requireFalse("sms.expose-code");
        requireFalse("springdoc.api-docs.enabled");
        requireFalse("springdoc.swagger-ui.enabled");

        String testCode = environment.getProperty("sms.test-code", "");
        if (testCode != null && !testCode.isBlank()) {
            throw new IllegalStateException("生产环境禁止配置短信测试验证码");
        }
        if (Boolean.parseBoolean(environment.getProperty("payment.verification.enabled", "false"))
                && !Boolean.parseBoolean(environment.getProperty("sms.provider-enabled", "false"))) {
            throw new IllegalStateException("启用大额支付短信验证前必须先启用正式短信服务");
        }
    }

    private void requireFalse(String key) {
        if (Boolean.parseBoolean(environment.getProperty(key, "false"))) {
            throw new IllegalStateException("生产环境禁止开启危险配置：" + key);
        }
    }
}
