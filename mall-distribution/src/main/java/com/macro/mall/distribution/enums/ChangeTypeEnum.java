package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 变更类型枚举
 */
@Getter
@AllArgsConstructor
public enum ChangeTypeEnum {

    /** 切线 */
    SWITCH_LINE(1, "切线"),

    /** 升级 */
    UPGRADE(2, "升级"),

    /** 降级 */
    DOWNGRADE(3, "降级"),

    /** 冻结 */
    FREEZE(4, "冻结"),

    /** 解冻 */
    UNFREEZE(5, "解冻"),

    /** 信息变更 */
    INFO_CHANGE(6, "信息变更");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static ChangeTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (ChangeTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
