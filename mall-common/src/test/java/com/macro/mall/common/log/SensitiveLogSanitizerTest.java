package com.macro.mall.common.log;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveLogSanitizerTest {
    @Test
    void masksNestedSecretsAndPersonalData() {
        Object sanitized = SensitiveLogSanitizer.sanitize(Map.of(
                "profile", Map.of("mobile", "15912345678", "nickname", "客户"),
                "items", List.of(Map.of("smsCode", "123456", "count", 1))));
        String text = String.valueOf(sanitized);
        assertFalse(text.contains("15912345678"));
        assertFalse(text.contains("123456"));
        assertTrue(text.contains("***"));
    }

    @Test
    void sanitizesRawTokensAndEmail() {
        String sanitized = SensitiveLogSanitizer.sanitizeText("Authorization: Bearer abc.def.ghi password=hunter2 user=a@example.com");
        assertEquals("Authorization: *** password=*** user=***@***", sanitized);
    }
}
