package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 提现状态枚举
 */
@Getter
@AllArgsConstructor
public enum WithdrawStatusEnum {

    /** 待审核 */
    PENDING_AUDIT(0, "待审核"),

    /** 审核通过 */
    AUDIT_PASSED(1, "审核通过"),

    /** 打款中 */
    PAYING(2, "打款中"),

    /** 打款成功 */
    PAY_SUCCESS(3, "打款成功"),

    /** 审核拒绝 */
    AUDIT_REJECTED(4, "审核拒绝");

    /** 状态值 */
    private final Integer value;

    /** 状态名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static WithdrawStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (WithdrawStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
