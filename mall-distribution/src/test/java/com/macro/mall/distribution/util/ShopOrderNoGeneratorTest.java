package com.macro.mall.distribution.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopOrderNoGeneratorTest {

    @Test
    void orderNumberContainsReadableCreateTimeAndFixedSafeLength() {
        String orderNo = ShopOrderNoGenerator.generate(
                2083490924069793792L,
                LocalDateTime.of(2026, 8, 2, 15, 12, 30));

        assertTrue(orderNo.startsWith("L20260802151230"));
        assertEquals(28, orderNo.length());
        assertTrue(orderNo.matches("^L\\d{14}[0-9A-Z]{13}$"));
    }

    @Test
    void differentSnowflakeIdsRemainUniqueWithinSameSecond() {
        LocalDateTime sameSecond = LocalDateTime.of(2026, 8, 2, 15, 12, 30);
        Set<String> orderNumbers = new HashSet<>();

        for (long orderId = 2083490924069790000L; orderId < 2083490924069800000L; orderId++) {
            orderNumbers.add(ShopOrderNoGenerator.generate(orderId, sameSecond));
        }

        assertEquals(10000, orderNumbers.size());
        assertNotEquals(
                ShopOrderNoGenerator.generate(2083490924069793792L, sameSecond),
                ShopOrderNoGenerator.generate(2083490924069793793L, sameSecond));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ShopOrderNoGenerator.generate(0, LocalDateTime.now()));
        assertThrows(NullPointerException.class,
                () -> ShopOrderNoGenerator.generate(1, null));
    }
}
