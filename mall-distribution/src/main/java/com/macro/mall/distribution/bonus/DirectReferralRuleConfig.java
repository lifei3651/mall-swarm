package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.Asserts;
import java.math.BigDecimal;

/** 规则版本内的不可变配置，佣金计算只能使用支付时冻结的版本。 */
public record DirectReferralRuleConfig(Boolean enabled, BigDecimal commissionRate,
                                       String purchaseScope, Integer settlementDelayDays) {
    public static final String ALL_ORDERS = "ALL_ORDERS";
    public static final String FIRST_PAID_ORDER = "FIRST_PAID_ORDER";

    public static DirectReferralRuleConfig disabled() {
        return new DirectReferralRuleConfig(false, BigDecimal.ZERO, ALL_ORDERS, 7);
    }

    public DirectReferralRuleConfig validated() {
        if (enabled == null || commissionRate == null || purchaseScope == null || settlementDelayDays == null) {
            Asserts.fail("请完整填写直接推荐佣金规则");
        }
        if (commissionRate.compareTo(BigDecimal.ZERO) < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0
                || (Boolean.TRUE.equals(enabled) && commissionRate.compareTo(BigDecimal.ZERO) == 0)) {
            Asserts.fail("启用时佣金比例必须大于0且不超过100%");
        }
        if (commissionRate.stripTrailingZeros().scale() > 4) {
            Asserts.fail("佣金比例最多支持4位小数");
        }
        if (!ALL_ORDERS.equals(purchaseScope) && !FIRST_PAID_ORDER.equals(purchaseScope)) {
            Asserts.fail("请选择全部有效订单或首次支付订单");
        }
        if (settlementDelayDays < 0 || settlementDelayDays > 365) {
            Asserts.fail("结算等待天数必须为0至365天");
        }
        return new DirectReferralRuleConfig(enabled, commissionRate.stripTrailingZeros(),
                purchaseScope, settlementDelayDays);
    }
}
