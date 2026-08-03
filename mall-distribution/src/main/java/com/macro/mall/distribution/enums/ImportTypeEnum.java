package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导入类型枚举
 */
@Getter
@AllArgsConstructor
public enum ImportTypeEnum {

    /** 代理导入 */
    AGENT(1, "代理导入"),

    /** 订单导入 */
    ORDER(2, "订单导入"),

    /** 关系导入 */
    RELATION(3, "关系导入");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static ImportTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (ImportTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
