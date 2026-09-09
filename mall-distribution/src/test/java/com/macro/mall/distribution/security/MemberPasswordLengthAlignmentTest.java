package com.macro.mall.distribution.security;

import com.macro.mall.distribution.dto.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemberPasswordLengthAlignmentTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean accepted(Class<?> type, String field, String value) throws Exception {
        Object dto = type.getDeclaredConstructor().newInstance();
        var property = type.getDeclaredField(field);
        property.setAccessible(true);
        property.set(dto, value);
        return validator.validateProperty(dto, field).isEmpty();
    }

    @Test
    void allMemberCreationAndResetPathsAcceptSixAndKeepLongPasswords() throws Exception {
        Class<?>[] types = {ShopRegisterDTO.class, ShopAccountSetupDTO.class, ShopPasswordChangeDTO.class,
                ShopPasswordResetDTO.class, AdminMemberCreateDTO.class, AdminMemberPasswordResetDTO.class};
        for (Class<?> type : types) {
            String field = type == ShopRegisterDTO.class || type == ShopAccountSetupDTO.class || type == AdminMemberCreateDTO.class
                    ? "password" : "newPassword";
            assertFalse(accepted(type, field, "49382"), type.getSimpleName());
            assertTrue(accepted(type, field, "493827"), type.getSimpleName());
            assertTrue(accepted(type, field, "Safer!Pass9"), type.getSimpleName());
            assertTrue(accepted(type, field, "A".repeat(32)), type.getSimpleName());
            assertFalse(accepted(type, field, "A".repeat(33)), type.getSimpleName());
        }
    }

    @Test
    void paymentRemainsExactlySixDigitsAndAdminPasswordsStayLonger() throws Exception {
        assertTrue(accepted(PaymentPasswordDTO.class, "newPassword", "493827"));
        for (String value : new String[]{"49382", "4938271", "Ab9!x2"})
            assertFalse(accepted(PaymentPasswordDTO.class, "newPassword", value));
        assertFalse(accepted(AdminSelfPasswordDTO.class, "newPassword", "493827"));
    }
}
