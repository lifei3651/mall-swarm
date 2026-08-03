package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 提现方式枚举
 */
@Getter
@AllArgsConstructor
public enum WithdrawTypeEnum {

    /** 银行卡 */
    BANK_CARD(1, "银行卡"),

    /** 微信 */
    WECHAT(2, "微信"),

    /** 支付宝 */
    ALIPAY(3, "支付宝");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static WithdrawTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (WithdrawTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
