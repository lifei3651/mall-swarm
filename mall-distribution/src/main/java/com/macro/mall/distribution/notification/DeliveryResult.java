package com.macro.mall.distribution.notification;

import java.math.BigDecimal;

public record DeliveryResult(DeliveryState state, String providerMessageId, BigDecimal actualCost,
                             String errorCode, String safeErrorMessage) {
    public static DeliveryResult accepted(String providerMessageId, BigDecimal cost) {
        return new DeliveryResult(DeliveryState.ACCEPTED, providerMessageId, cost, null, null);
    }
    public static DeliveryResult delivered(String providerMessageId, BigDecimal cost) {
        return new DeliveryResult(DeliveryState.DELIVERED, providerMessageId, cost, null, null);
    }
    public static DeliveryResult retryable(String code) {
        return new DeliveryResult(DeliveryState.RETRYABLE, null, BigDecimal.ZERO, code, "供应商暂时不可用");
    }
    public static DeliveryResult permanent(String code) {
        return new DeliveryResult(DeliveryState.PERMANENT, null, BigDecimal.ZERO, code, "供应商拒绝本次通知");
    }
    public static DeliveryResult unknown(String providerMessageId, String code) {
        return new DeliveryResult(DeliveryState.UNKNOWN, providerMessageId, BigDecimal.ZERO, code, "供应商结果待查询");
    }
}
