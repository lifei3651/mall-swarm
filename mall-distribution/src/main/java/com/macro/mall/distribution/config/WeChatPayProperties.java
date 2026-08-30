package com.macro.mall.distribution.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "shop.wechat-pay")
public class WeChatPayProperties {

    /** 客户完成微信支付签约、AppID绑定、域名配置和真实联调后才允许开启。 */
    private boolean enabled;
    private String mchId;
    private String merchantSerialNumber;
    private String privateKeyPath;
    private String publicKeyId;
    private String publicKeyPath;

    @ToString.Exclude
    private String apiV3Key;

    private String notifyUrl;
    private String refundNotifyUrl;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;

    public boolean isConfigured() {
        return enabled && present(mchId) && present(merchantSerialNumber)
                && present(privateKeyPath) && present(publicKeyId) && present(publicKeyPath)
                && present(apiV3Key) && present(notifyUrl) && present(refundNotifyUrl);
    }

    public int safeConnectTimeoutMs() {
        return bounded(connectTimeoutMs, 5000);
    }

    public int safeReadTimeoutMs() {
        return bounded(readTimeoutMs, 10000);
    }

    private int bounded(int value, int fallback) {
        int resolved = value <= 0 ? fallback : value;
        return Math.max(1000, Math.min(resolved, 60000));
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
