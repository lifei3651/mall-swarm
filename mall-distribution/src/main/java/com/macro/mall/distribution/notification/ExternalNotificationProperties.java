package com.macro.mall.distribution.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification.external")
public class ExternalNotificationProperties {
    private boolean enabled = false;
    private boolean workerEnabled = false;
    private int batchSize = 20;
    private int leaseSeconds = 120;
    private int taskTtlHours = 72;
    private int maxAttempts = 5;
    private int baseRetrySeconds = 60;
    private int maxRetrySeconds = 21600;
    private int unknownQueryLimit = 12;
}
