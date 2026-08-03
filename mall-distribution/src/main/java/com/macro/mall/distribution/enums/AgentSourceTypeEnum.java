package com.macro.mall.distribution.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 代理来源类型枚举
 */
@Getter
@AllArgsConstructor
public enum AgentSourceTypeEnum {

    /** 自主注册 */
    SELF_REGISTER(1, "自主注册"),

    /** 扫码邀请 */
    SCAN_CODE(2, "扫码邀请"),

    /** 后台添加 */
    ADMIN_ADD(3, "后台添加"),

    /** 批量导入 */
    BATCH_IMPORT(4, "批量导入");

    /** 类型值 */
    private final Integer value;

    /** 类型名称 */
    private final String name;

    /**
     * 根据值获取枚举
     */
    public static AgentSourceTypeEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (AgentSourceTypeEnum type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
