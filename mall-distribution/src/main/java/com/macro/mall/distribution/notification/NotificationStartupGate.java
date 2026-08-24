package com.macro.mall.distribution.notification;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class NotificationStartupGate {
    private final ExternalNotificationProperties external;
    private final AliyunNotificationSmsProperties sms;
    private final MockNotificationProperties mock;
    private final Environment environment;

    @PostConstruct
    void validate() {
        boolean mockChannel = mock.isAppPushEnabled() || mock.isMiniProgramEnabled();
        if (!external.isEnabled()) {
            if (external.isWorkerEnabled() || sms.isEnabled() || mock.isEnabled() || mockChannel)
                throw new IllegalStateException("外部通知总开关关闭时，各发送器和适配器也必须关闭");
            return;
        }
        if (!external.isWorkerEnabled()) throw new IllegalStateException("外部通知总开关开启时必须显式开启发送器");
        if (sms.isEnabled() && (blank(sms.getAccessKeyId()) || blank(sms.getAccessKeySecret())
                || blank(sms.getSignName()) || blank(sms.getReceiptSecret()) || sms.getTemplates().isEmpty())) {
            throw new IllegalStateException("通知短信凭据、签名、回执密钥或审核模板不完整");
        }
        if (mockChannel) {
            boolean safeProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equals("local") || p.equals("test"));
            if (!mock.isEnabled() || !safeProfile) throw new IllegalStateException("App/小程序模拟适配器只允许在 local/test 使用");
        }
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
