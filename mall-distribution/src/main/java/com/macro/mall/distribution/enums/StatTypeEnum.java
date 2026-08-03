package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统计类型枚举
 */
@Getter
@AllArgsConstructor
public enum StatTypeEnum {

    /** 日 */
    DAILY(1, "日"),

    /** 周 */
    WEEKLY(2, "周"),

    /** 月 */
    MONTHLY(3, "月"),

    /** 年 */
    YEARLY(4, "年");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static StatTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (StatTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
