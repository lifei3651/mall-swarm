package com.macro.mall.distribution.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Notification DATETIME values and budget days use one business zone, not the host default. */
public final class NotificationTime {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private NotificationTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    public static LocalDate today() {
        return now().toLocalDate();
    }
}
