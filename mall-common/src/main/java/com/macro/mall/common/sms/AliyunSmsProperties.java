package com.macro.mall.common.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "sms.aliyun")
public class AliyunSmsProperties {
    private String accessKeyId;
    private String accessKeySecret;
    private String endpoint = "dysmsapi.aliyuncs.com";
    private String signName;
    private Map<String, String> templates = new HashMap<>();
}
