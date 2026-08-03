package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 规则类型枚举
 */
@Getter
@AllArgsConstructor
public enum RuleTypeEnum {

    /** 按代理等级 */
    AGENT_LEVEL(1, "按代理等级"),

    /** 按商品分类 */
    CATEGORY(2, "按商品分类"),

    /** 按活动 */
    ACTIVITY(3, "按活动");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static RuleTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (RuleTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
