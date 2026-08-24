package com.macro.mall.distribution.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "notification.sms.aliyun")
public class AliyunNotificationSmsProperties {
    private boolean enabled = false;
    private String accessKeyId;
    private String accessKeySecret;
    private String endpoint = "dysmsapi.aliyuncs.com";
    private String signName;
    private String receiptSecret;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private BigDecimal unitCost = new BigDecimal("0.0500");
    /** 仅通知短信事件到已审核模板的独立映射；与 sms.aliyun.templates 验证码映射完全隔离。 */
    private Map<String, String> templates = new HashMap<>();
}
