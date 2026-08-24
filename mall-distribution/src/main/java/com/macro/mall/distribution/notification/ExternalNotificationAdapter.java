package com.macro.mall.distribution.notification;

import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;

import java.math.BigDecimal;
import java.util.Map;

public interface ExternalNotificationAdapter {
    String channel();
    String providerCode();
    GateDecision readiness(ExternalNotificationContext context);
    BigDecimal estimatedCost(ExternalNotificationContext context);
    DeliveryResult send(ExternalNotificationContext context, String idempotencyKey);
    DeliveryResult query(ExternalNotificationContext context, DmsMessageDeliveryAttempt attempt);
    default boolean verifyReceipt(Map<String, String> headers, byte[] body) { return false; }
    default NotificationReceipt parseReceipt(byte[] body) { throw new IllegalArgumentException("回执格式不支持"); }
}
