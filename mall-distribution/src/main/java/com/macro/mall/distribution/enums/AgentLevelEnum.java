package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 代理等级枚举
 */
@Getter
@AllArgsConstructor
public enum AgentLevelEnum {

    MEMBER(1, "会员"),

    VIP(2, "VIP会员"),

    STORE(3, "店铺"),

    AGENT(4, "代理"),

    ONE_STAR_DIRECTOR(5, "一星董事"),

    TWO_STAR_DIRECTOR(6, "二星董事"),

    THREE_STAR_DIRECTOR(7, "三星董事"),

    PARTNER(8, "合伙人");

    /** 等级值 */
    private final Integer value;

    /** 等级名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static AgentLevelEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (AgentLevelEnum level : values()) {
            if (level.getValue().equals(value)) {
                return level;
            }
        }
        return null;
    }
}
