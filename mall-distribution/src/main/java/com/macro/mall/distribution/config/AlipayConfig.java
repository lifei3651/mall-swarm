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

    /** 签约支付宝商户UID（PID），用于确认回调收款主体。 */
    private String sellerId;

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

    /** 支付宝网关连接与读取超时，防止第三方网络异常长期占用业务线程。 */
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;

    public boolean isConfigured() {
        return enabled
                && appId != null && !appId.isBlank()
                && sellerId != null && !sellerId.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && alipayPublicKey != null && !alipayPublicKey.isBlank();
    }
}
