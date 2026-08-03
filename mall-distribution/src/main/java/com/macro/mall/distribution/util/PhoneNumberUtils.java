package com.macro.mall.distribution.util;

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
}
