package com.macro.mall.distribution.security;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/** 只生成短时、首次登录必须修改的后台临时凭据。 */
public final class TemporaryAdminCredential {
    public static final int VALID_HOURS = 24;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] RANDOM_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private TemporaryAdminCredential() {
    }

    public static String generate() {
        char[] value = new char[16];
        value[0] = 'A';
        value[1] = 'a';
        value[2] = '7';
        value[3] = '!';
        for (int i = 4; i < value.length; i++) value[i] = RANDOM_CHARS[RANDOM.nextInt(RANDOM_CHARS.length)];
        for (int i = value.length - 1; i > 0; i--) {
            int index = RANDOM.nextInt(i + 1);
            char swap = value[i];
            value[i] = value[index];
            value[index] = swap;
        }
        return new String(value);
    }

    public static LocalDateTime expiresAt() {
        return LocalDateTime.now().plusHours(VALID_HOURS);
    }
}
