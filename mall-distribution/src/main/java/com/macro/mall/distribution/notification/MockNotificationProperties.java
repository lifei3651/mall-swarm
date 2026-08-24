package com.macro.mall.distribution.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification.mock")
public class MockNotificationProperties {
    private boolean enabled = false;
    private boolean appPushEnabled = false;
    private boolean miniProgramEnabled = false;
}
