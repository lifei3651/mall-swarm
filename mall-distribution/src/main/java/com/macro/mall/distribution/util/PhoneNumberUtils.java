package com.macro.mall.distribution.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 中国大陆手机号统一校验工具。 */
public final class PhoneNumberUtils {
    private static final String MAINLAND_MOBILE_PATTERN = "^1[3-9]\\d{9}$";

    private PhoneNumberUtils() {
    }

    public static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    public static boolean isValidMainlandMobile(String value) {
        String normalized = normalize(value);
        return normalized != null && normalized.matches(MAINLAND_MOBILE_PATTERN);
    }

    /** Redis 键只保存不可逆手机号摘要，避免运维查看键空间时直接暴露会员手机号。 */
    public static String redisIdentity(String value) {
        String normalized = normalize(value);
        if (normalized == null) return "";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("手机号摘要计算失败", exception);
        }
    }
}
