package com.macro.mall.distribution.bonus;

import org.springframework.stereotype.Component;

import java.util.List;

/** 新客户基座的安全默认值：商城可交易，但不生成任何奖金。 */
@Component
public class DisabledCustomerBonusPolicy implements CustomerBonusPolicy {

    @Override
    public String policyCode() {
        return CustomerBonusPolicyCodes.DISABLED;
    }

    @Override
    public List<CustomerBonusPayout> calculate(CustomerBonusOrderContext context) {
        return List.of();
    }
}
