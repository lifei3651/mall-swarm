package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作人类型枚举
 */
@Getter
@AllArgsConstructor
public enum OperatorTypeEnum {

    /** 系统 */
    SYSTEM(1, "系统"),

    /** 管理员 */
    ADMIN(2, "管理员"),

    /** 代理自己 */
    AGENT(3, "代理自己");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static OperatorTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (OperatorTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
