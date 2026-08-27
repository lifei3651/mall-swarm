package com.macro.mall.distribution.bonus;

import java.math.BigDecimal;

/** 支付完成后交给客户奖金程序的只读标准输入。 */
public record CustomerBonusOrderContext(
        Long tenantId,
        Long ruleVersionId,
        Long orderId,
        String orderNo,
        BigDecimal bonusBaseAmount,
        Long orderUserId,
        String orderUserName
) {
}
