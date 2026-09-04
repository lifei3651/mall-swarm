package com.macro.mall.distribution.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "shop.wechat-mini-program")
public class WeChatMiniProgramProperties {

    /** 客户完成主体、隐私和服务器域名配置后才能开启。 */
    private boolean enabled;

    /** 手机号快速验证会产生微信平台调用成本，必须由客户单独确认后开启。 */
    private boolean phoneAuthorizationEnabled;

    /** 微信订阅消息必须完成模板申请和真机送达验收后才能开启。 */
    private boolean subscribeMessageEnabled;

    /** 微信支付订单发货信息同步必须完成商户号绑定和真机验收后才能开启。 */
    private boolean shippingInfoEnabled;

    private String appId;

    @ToString.Exclude
    private String appSecret;

    private String privacyConsentVersion = "MINI_PROGRAM_PRIVACY_V1";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;

    /** developer、trial 或 formal；生产默认只能使用 formal。 */
    private String miniProgramState = "formal";

    /** 事件对应的微信订阅模板；模板编号不是密钥，但必须与公众平台字段完全一致。 */
    private Map<String, SubscriptionTemplate> subscriptionTemplates = new LinkedHashMap<>();

    public boolean loginReady() {
        return enabled && present(appId) && present(appSecret);
    }

    public boolean phoneAuthorizationReady() {
        return loginReady() && phoneAuthorizationEnabled;
    }

    public boolean subscribeMessageReady() {
        return loginReady() && subscribeMessageEnabled
                && subscriptionTemplates.values().stream().anyMatch(SubscriptionTemplate::ready);
    }

    public boolean shippingInfoReady() {
        return loginReady() && shippingInfoEnabled;
    }

    public String safeMiniProgramState() {
        return switch (miniProgramState == null ? "" : miniProgramState.trim().toLowerCase()) {
            case "developer" -> "developer";
            case "trial" -> "trial";
            default -> "formal";
        };
    }

    @Data
    public static class SubscriptionTemplate {
        private String templateId;
        private String title;
        private String page;
        /** 必须对应微信模板中的 phrase 类型字段。 */
        private String statusKey;
        /** 必须对应微信模板中的 time 或 date 类型字段。 */
        private String timeKey;
        /** 必须对应微信模板中的 thing 类型字段。 */
        private String remarkKey;

        public boolean ready() {
            return present(templateId)
                    && statusKey != null && statusKey.matches("^phrase[0-9]{1,3}$")
                    && timeKey != null && timeKey.matches("^(time|date)[0-9]{1,3}$")
                    && remarkKey != null && remarkKey.matches("^thing[0-9]{1,3}$");
        }

        private boolean present(String value) {
            return value != null && !value.isBlank();
        }
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
