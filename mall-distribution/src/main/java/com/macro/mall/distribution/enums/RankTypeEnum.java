package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 排名类型枚举
 */
@Getter
@AllArgsConstructor
public enum RankTypeEnum {

    /** 个人业绩 */
    PERSONAL_PERFORMANCE(1, "个人业绩"),

    /** 团队业绩 */
    TEAM_PERFORMANCE(2, "团队业绩"),

    /** 新增代理 */
    NEW_AGENT(3, "新增代理");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static RankTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (RankTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
