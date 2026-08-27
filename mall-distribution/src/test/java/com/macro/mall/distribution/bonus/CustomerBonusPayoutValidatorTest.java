package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerBonusPayoutValidatorTest {

    private final CustomerBonusOrderContext context = new CustomerBonusOrderContext(
            1L, 2L, 3L, "ORDER-3", new BigDecimal("100.00"), 4L, "buyer");

    @Test
    void acceptsAValidCustomerDefinedPayout() {
        List<CustomerBonusPayout> payouts = List.of(payout(10L, 1, "REPURCHASE_REWARD", "20.00"));
        assertEquals(payouts, CustomerBonusPayoutValidator.validate(context, payouts));
    }

    @Test
    void rejectsMissingResultInsteadOfTreatingItAsNoBonus() {
        assertThrows(ApiException.class, () -> CustomerBonusPayoutValidator.validate(context, null));
    }

    @Test
    void rejectsDuplicateReceiverAndTypeBeforeDatabaseInsertion() {
        assertThrows(ApiException.class, () -> CustomerBonusPayoutValidator.validate(context, List.of(
                payout(10L, 1, "CUSTOM_REWARD", "10.00"),
                payout(10L, 2, "CUSTOM_REWARD", "5.00"))));
    }

    @Test
    void rejectsTotalPayoutAboveTheOrderBonusBase() {
        assertThrows(ApiException.class, () -> CustomerBonusPayoutValidator.validate(context, List.of(
                payout(10L, 1, "A", "60.00"),
                payout(11L, 2, "B", "50.00"))));
    }

    @Test
    void rejectsAmbiguousFractionalCentAmounts() {
        assertThrows(ApiException.class, () -> CustomerBonusPayoutValidator.validate(context, List.of(
                payout(10L, 1, "CUSTOM_REWARD", "10.001"))));
    }

    private CustomerBonusPayout payout(Long receiverAgentId, int level, String code, String amount) {
        return new CustomerBonusPayout(receiverAgentId, level, code, new BigDecimal("0.1000"),
                new BigDecimal(amount), "客户程序验收说明");
    }
}
