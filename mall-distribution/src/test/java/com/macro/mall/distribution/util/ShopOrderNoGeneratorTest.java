package com.macro.mall.distribution.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopOrderNoGeneratorTest {

    @Test
    void orderAndTradeNumbersAreShortFixedAndKeepTheCompleteSnowflakeToken() {
        long id = 2083490924069793792L;
        String orderNo = ShopOrderNoGenerator.generate(id);
        String tradeNo = ShopOrderNoGenerator.generateTrade(id);

        assertEquals(14, orderNo.length());
        assertEquals(14, tradeNo.length());
        assertTrue(orderNo.matches("^L[0-9A-Z]{13}$"));
        assertTrue(tradeNo.matches("^T[0-9A-Z]{13}$"));
        assertEquals(Long.toUnsignedString(id, 36).toUpperCase(), orderNo.substring(1).replaceFirst("^0+", ""));
    }

    @Test
    void differentSnowflakeIdsRemainUnique() {
        Set<String> orderNumbers = new HashSet<>();

        for (long orderId = 2083490924069790000L; orderId < 2083490924069800000L; orderId++) {
            orderNumbers.add(ShopOrderNoGenerator.generate(orderId));
        }

        assertEquals(10000, orderNumbers.size());
        assertNotEquals(
                ShopOrderNoGenerator.generate(2083490924069793792L),
                ShopOrderNoGenerator.generate(2083490924069793793L));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ShopOrderNoGenerator.generate(0));
        assertThrows(IllegalArgumentException.class, () -> ShopOrderNoGenerator.generateTrade(0));
    }
}
