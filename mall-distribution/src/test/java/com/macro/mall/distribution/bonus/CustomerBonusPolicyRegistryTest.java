package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerBonusPolicyRegistryTest {

    @Test
    void disabledPolicyKeepsBaseCommerceRunningWithoutPayouts() {
        DisabledCustomerBonusPolicy disabled = new DisabledCustomerBonusPolicy();
        CustomerBonusPolicyRegistry registry = new CustomerBonusPolicyRegistry(List.of(disabled));

        CustomerBonusOrderContext context = new CustomerBonusOrderContext(
                1L, 1L, 1L, "SO1", new BigDecimal("100.00"), 10L, "buyer");

        assertEquals(CustomerBonusPolicyCodes.DISABLED, registry.require("customer_bonus_disabled").policyCode());
        assertEquals(List.of(), disabled.calculate(context));
    }

    @Test
    void unregisteredCustomerPolicyIsRejectedInsteadOfSilentlyMiscomputing() {
        CustomerBonusPolicyRegistry registry = new CustomerBonusPolicyRegistry(
                List.of(new DisabledCustomerBonusPolicy()));

        assertThrows(ApiException.class, () -> registry.require("CUSTOMER_A_V1"));
    }

    @Test
    void duplicatePolicyCodeFailsAtStartup() {
        CustomerBonusPolicy first = policy("CLIENT_V1");
        CustomerBonusPolicy second = policy("client_v1");

        assertThrows(IllegalStateException.class,
                () -> new CustomerBonusPolicyRegistry(List.of(first, second)));
    }

    private CustomerBonusPolicy policy(String code) {
        return new CustomerBonusPolicy() {
            @Override public String policyCode() { return code; }
            @Override public List<CustomerBonusPayout> calculate(CustomerBonusOrderContext context) {
                return List.of();
            }
        };
    }
}
