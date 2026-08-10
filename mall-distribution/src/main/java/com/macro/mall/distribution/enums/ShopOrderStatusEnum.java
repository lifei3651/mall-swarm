package com.macro.mall.distribution.enums;

import lombok.Getter;

/**
 * 商城订单主状态。售后处理状态由售后单单独维护，不能与订单主状态混用。
 */
@Getter
public enum ShopOrderStatusEnum {
    PENDING_PAYMENT(0, "待支付"),
    PENDING_SHIPMENT(1, "待发货"),
    SHIPPED(2, "待收货"),
    COMPLETED(3, "已完成"),
    CLOSED(4, "已关闭");

    private final Integer value;
    private final String description;

    ShopOrderStatusEnum(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    public boolean matches(Integer status) {
        return value.equals(status);
    }

    public static boolean isPaidLifecycle(Integer status) {
        return PENDING_SHIPMENT.matches(status) || SHIPPED.matches(status) || COMPLETED.matches(status);
    }
}
