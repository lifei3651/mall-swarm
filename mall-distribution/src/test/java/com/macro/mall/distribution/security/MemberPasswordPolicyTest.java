package com.macro.mall.distribution.security;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberPasswordPolicyTest {

    @Test
    void acceptsAReasonableNewLoginPassword() {
        assertDoesNotThrow(() -> MemberPasswordPolicy.validate(
                "Safer!Pass9", "member_1001", "13900001001"));
    }

    @Test
    void rejectsShortCommonRepeatedSequentialAndAccountDerivedPasswords() {
        assertThrows(ApiException.class,
                () -> MemberPasswordPolicy.validate("short888", "member_1001", "13900001001"));
        assertThrows(ApiException.class,
                () -> MemberPasswordPolicy.validate("password123", "member_1001", "13900001001"));
        assertThrows(ApiException.class,
                () -> MemberPasswordPolicy.validate("ababababab", "member_1001", "13900001001"));
        assertThrows(ApiException.class,
                () -> MemberPasswordPolicy.validate("0123456789", "member_1001", "13900001001"));
        assertThrows(ApiException.class,
                () -> MemberPasswordPolicy.validate("member_1001!", "member_1001", "13900001001"));
        assertThrows(ApiException.class,
                () -> MemberPasswordPolicy.validate("Safe!001001", "member_1001", "13900001001"));
    }
}
