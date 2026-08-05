package com.macro.mall.distribution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝配置
 * 在 application.yml 中配置：
 * alipay:
 *   appId: your-app-id
 *   privateKey: your-private-key
 *   alipayPublicKey: alipay-public-key
 *   notifyUrl: https://lingqimall.com/api/pay/alipay/notify
 *   returnUrl: https://lingqimall.com/api/pay/alipay/return
 *   gatewayUrl: https://openapi.alipay.com/gateway.do
 *   signType: RSA2
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    /** 应用ID */
    private String appId;

    /** 应用私钥 */
    private String privateKey;

    /** 支付宝公钥 */
    private String alipayPublicKey;

    /** 异步通知地址 */
    private String notifyUrl;

    /** 同步跳转地址 */
    private String returnUrl;

    /** 网关地址 */
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";

    /** 签名类型 */
    private String signType = "RSA2";

    /** 是否启用 */
    private boolean enabled = false;

    public boolean isConfigured() {
        return enabled
                && appId != null && !appId.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && alipayPublicKey != null && !alipayPublicKey.isBlank();
    }
}
