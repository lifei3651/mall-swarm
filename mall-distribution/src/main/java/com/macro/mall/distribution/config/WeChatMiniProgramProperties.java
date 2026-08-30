package com.macro.mall.distribution.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "shop.wechat-mini-program")
public class WeChatMiniProgramProperties {

    /** 客户完成主体、隐私和服务器域名配置后才能开启。 */
    private boolean enabled;

    /** 手机号快速验证会产生微信平台调用成本，必须由客户单独确认后开启。 */
    private boolean phoneAuthorizationEnabled;

    private String appId;

    @ToString.Exclude
    private String appSecret;

    private String privacyConsentVersion = "MINI_PROGRAM_PRIVACY_V1";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;

    public boolean loginReady() {
        return enabled && present(appId) && present(appSecret);
    }

    public boolean phoneAuthorizationReady() {
        return loginReady() && phoneAuthorizationEnabled;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
