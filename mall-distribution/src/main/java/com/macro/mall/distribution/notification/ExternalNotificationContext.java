package com.macro.mall.distribution.notification;

import lombok.Data;

@Data
public class ExternalNotificationContext {
    private Long taskId;
    private Long tenantId;
    private Long messageId;
    private Long memberId;
    private Long userId;
    private String eventType;
    private String channel;
    private String title;
    private String summary;
    private String content;
    private String phone;
}
