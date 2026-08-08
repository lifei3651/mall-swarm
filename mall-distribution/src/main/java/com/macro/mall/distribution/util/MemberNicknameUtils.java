package com.macro.mall.distribution.util;

import com.macro.mall.common.exception.Asserts;

import java.util.regex.Pattern;

public final class MemberNicknameUtils {

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{IsHan}A-Za-z0-9·_\\- ]{2,20}$");

    private MemberNicknameUtils() {
    }

    public static String normalize(String value) {
        String nickname = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (!ALLOWED.matcher(nickname).matches()) {
            Asserts.fail("昵称需为2至20个字符，仅支持中文、字母、数字、空格、·、-和_");
        }
        return nickname;
    }
}
