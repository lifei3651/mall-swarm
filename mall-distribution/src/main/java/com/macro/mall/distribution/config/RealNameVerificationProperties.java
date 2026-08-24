package com.macro.mall.distribution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "shop.real-name")
public class RealNameVerificationProperties {
    private boolean enabled;
    private String provider = "TENCENT";
    private String secretId;
    private String secretKey;
    private String region = "ap-guangzhou";
    private String endpoint = "faceid.tencentcloudapi.com";
    private int dailyMaxAttemptsPerAccount = 5;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 10;

    public boolean isReady() {
        return enabled && hasText(secretId) && hasText(secretKey)
                && "TENCENT".equalsIgnoreCase(provider)
                && "faceid.tencentcloudapi.com".equalsIgnoreCase(endpoint == null ? "" : endpoint.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
