package com.macro.mall.distribution.security;

import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EncryptedStringTypeHandlerTest {

    private static final String KEY = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    @Test
    void encryptsWithRandomIvAndDecryptsWithoutChangingLegacyPlaintext() throws Exception {
        EncryptedStringTypeHandler handler = new EncryptedStringTypeHandler(KEY);
        String first = handler.encrypt("430102199001011234");
        String second = handler.encrypt("430102199001011234");

        assertTrue(first.startsWith(EncryptedStringTypeHandler.PREFIX));
        assertNotEquals(first, second);
        assertEquals("430102199001011234", handler.decrypt(first));
        assertEquals("legacy-plain-text", handler.decrypt("legacy-plain-text"));
    }

    @Test
    void rejectsTamperedCiphertextAndMissingKey() throws Exception {
        EncryptedStringTypeHandler handler = new EncryptedStringTypeHandler(KEY);
        String encrypted = handler.encrypt("6222020202020202020");
        byte[] payload = Base64.getUrlDecoder().decode(encrypted.substring(EncryptedStringTypeHandler.PREFIX.length()));
        payload[payload.length - 1] ^= 0x01;
        String tampered = EncryptedStringTypeHandler.PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);

        assertThrows(SQLException.class, () -> handler.decrypt(tampered));
        EncryptedStringTypeHandler withoutKey = new EncryptedStringTypeHandler("");
        assertThrows(SQLException.class, () -> withoutKey.encrypt("sensitive"));
        assertThrows(SQLException.class, () -> withoutKey.decrypt(encrypted));
    }

    @Test
    void compatibilityModeReadsCiphertextButKeepsNewWritesPlaintext() throws Exception {
        EncryptedStringTypeHandler encrypting = new EncryptedStringTypeHandler(KEY);
        String encrypted = encrypting.encrypt("legacy-compatible");
        EncryptedStringTypeHandler compatibility = new EncryptedStringTypeHandler(KEY, false);
        PreparedStatement statement = mock(PreparedStatement.class);

        compatibility.setNonNullParameter(statement, 1, "new-plain-value", null);

        verify(statement).setString(1, "new-plain-value");
        assertEquals("legacy-compatible", compatibility.decrypt(encrypted));
    }
}
