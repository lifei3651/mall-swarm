package com.macro.mall.distribution.util;

import com.macro.mall.distribution.entity.DmsShopMember;

/**
 * 会员业务账号的统一展示入口。
 *
 * <p>数据库主键和 userId 只用于内部关联，不能再拼成业务编号展示给用户。</p>
 */
public final class MemberAccountUtils {

    private MemberAccountUtils() {
    }

    /**
     * 后台、导出和审计页面统一展示登录账号；仅兼容尚未补齐登录账号的历史记录时回退手机号。
     */
    public static String display(DmsShopMember member) {
        if (member == null) return null;
        if (member.getUsername() != null && !member.getUsername().isBlank()) return member.getUsername();
        if (member.getPhone() != null && !member.getPhone().isBlank()) return member.getPhone();
        return null;
    }
}
