package com.macro.mall.distribution.wechat;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OfficialWeChatPayGatewayTest {

    @Test
    void paymentExpiryUsesRfc3339SecondsWithoutFractionalNanoseconds() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-14T17:33:03.396165800+08:00");

        String expiry = OfficialWeChatPayGateway.paymentExpireAt(now);

        assertEquals("2026-09-14T18:03:03+08:00", expiry);
        assertFalse(expiry.contains("."));
        assertEquals(0, OffsetDateTime.parse(expiry, DateTimeFormatter.ISO_OFFSET_DATE_TIME).getNano());
    }
}
