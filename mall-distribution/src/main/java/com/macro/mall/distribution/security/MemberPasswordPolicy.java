package com.macro.mall.distribution.security;

import com.macro.mall.common.exception.Asserts;

import java.util.Locale;
import java.util.Set;

/** 会员登录密码的唯一服务端策略；历史密码登录不在此处强制迁移。 */
public final class MemberPasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 32;
    private static final Set<String> COMMON = Set.of(
            "1234567890", "0123456789", "password123", "password1234", "qwerty1234",
            "qwertyuiop", "abc1234567", "admin12345", "iloveyou123", "welcome123");

    private MemberPasswordPolicy() {
    }

    public static void validate(String password, String username, String phone) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            Asserts.fail("登录密码需为10至32位");
        }
        if (password.isBlank() || password.chars().anyMatch(Character::isWhitespace)) {
            Asserts.fail("登录密码不能包含空格");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(normalized) || repeated(normalized) || sequential(normalized)) {
            Asserts.fail("登录密码过于简单，请更换更安全的密码");
        }
        String account = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String mobile = phone == null ? "" : phone.trim();
        String phoneTail = mobile.length() >= 6 ? mobile.substring(mobile.length() - 6) : mobile;
        if ((!account.isEmpty() && (normalized.equals(account) || normalized.contains(account)))
                || (!mobile.isEmpty() && (normalized.equals(mobile) || normalized.contains(mobile)
                || (!phoneTail.isEmpty() && normalized.endsWith(phoneTail))))) {
            Asserts.fail("登录密码不能包含登录账号或手机号");
        }
    }

    private static boolean repeated(String value) {
        return value.matches("^(.)\\1{9,}$") || value.matches("^(.{2,5})\\1+$");
    }

    private static boolean sequential(String value) {
        String compact = value.replaceAll("[^a-z0-9]", "");
        return compact.length() >= 10 && ("0123456789abcdefghijklmnopqrstuvwxyz".contains(compact)
                || "9876543210zyxwvutsrqponmlkjihgfedcba".contains(compact));
    }
}
