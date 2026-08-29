package com.macro.mall.distribution.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromotionJoinModeEnumTest {

    @Test
    void newCustomerDefaultsToDisabledWhileHistoricalMissingFieldKeepsLegacyBehavior() {
        assertEquals(PromotionJoinModeEnum.DISABLED, PromotionJoinModeEnum.forNew(null));
        assertEquals(PromotionJoinModeEnum.FIRST_PAID_ORDER, PromotionJoinModeEnum.forExisting(null));
    }

    @Test
    void acceptsKnownModesCaseInsensitivelyAndRejectsUnknownMode() {
        assertEquals(PromotionJoinModeEnum.AUTO_ON_INVITE,
                PromotionJoinModeEnum.forExisting(" auto_on_invite "));
        assertThrows(IllegalArgumentException.class,
                () -> PromotionJoinModeEnum.forExisting("PURCHASE_TO_JOIN"));
    }
}
