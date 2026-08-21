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

    /** 后台普通列表使用的手机号脱敏；完整手机号只在有明确业务必要的详情接口返回。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        String value = phone.trim();
        if (value.matches("^1[3-9]\\d{9}$")) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }
        return maskAccount(value);
    }

    /** 登录账号脱敏，手机号账号沿用手机号口径，其他账号仅保留少量首尾字符。 */
    public static String maskAccount(String account) {
        if (account == null || account.isBlank()) return account;
        String value = account.trim();
        if (value.matches("^1[3-9]\\d{9}$")) return maskPhone(value);
        int length = value.length();
        if (length == 1) return "*";
        if (length == 2) return value.substring(0, 1) + "*";
        if (length <= 4) return value.substring(0, 1) + "*".repeat(length - 2) + value.substring(length - 1);
        return value.substring(0, 2) + "*".repeat(Math.min(6, length - 4)) + value.substring(length - 2);
    }

    /** 收款账号普通列表仅展示最后四位，完整值只允许财务管理详情读取。 */
    public static String maskBankAccount(String account) {
        if (account == null || account.isBlank()) return account;
        String value = account.trim();
        return value.length() <= 4 ? "****" : "**** " + value.substring(value.length() - 4);
    }

    /** 收款户名普通列表只保留首字，避免经营看板或只读财务列表泄露完整实名。 */
    public static String maskPersonName(String name) {
        if (name == null || name.isBlank()) return name;
        String value = name.trim();
        return value.substring(0, 1) + "*".repeat(Math.max(1, Math.min(3, value.length() - 1)));
    }
}
