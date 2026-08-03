package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 代理状态枚举
 */
@Getter
@AllArgsConstructor
public enum AgentStatusEnum {

    /** 禁用 */
    DISABLED(0, "禁用"),

    /** 正常 */
    NORMAL(1, "正常"),

    /** 冻结 */
    FROZEN(2, "冻结");

    /** 状态值 */
    private final Integer value;

    /** 状态名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static AgentStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (AgentStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
