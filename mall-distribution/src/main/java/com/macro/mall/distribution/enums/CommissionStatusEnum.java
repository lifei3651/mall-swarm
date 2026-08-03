package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 佣金状态枚举
 */
@Getter
@AllArgsConstructor
public enum CommissionStatusEnum {

    /** 待结算 */
    PENDING(0, "待结算"),

    /** 已结算 */
    SETTLED(1, "已结算"),

    /** 已取消 */
    CANCELLED(2, "已取消"),

    /** 已退款 */
    REFUNDED(3, "已退款");

    /** 状态值 */
    private final Integer value;

    /** 状态名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static CommissionStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (CommissionStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
