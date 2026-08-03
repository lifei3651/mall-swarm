package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业绩类型枚举
 */
@Getter
@AllArgsConstructor
public enum PerformanceTypeEnum {

    /** 个人业绩 */
    PERSONAL(1, "个人业绩"),

    /** 团队业绩 */
    TEAM(2, "团队业绩");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static PerformanceTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (PerformanceTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
