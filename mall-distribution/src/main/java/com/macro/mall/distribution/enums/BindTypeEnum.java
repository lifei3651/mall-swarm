package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 绑定方式枚举
 */
@Getter
@AllArgsConstructor
public enum BindTypeEnum {

    /** 扫码绑定 */
    SCAN_CODE(1, "扫码绑定"),

    /** 邀请码绑定 */
    INVITE_CODE(2, "邀请码绑定"),

    /** 后台绑定 */
    ADMIN_BIND(3, "后台绑定"),

    /** 导入绑定 */
    IMPORT_BIND(4, "导入绑定");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static BindTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (BindTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
