package com.macro.mall.distribution.bonus;

import java.math.BigDecimal;

/** 客户奖金程序返回给商城基座的标准奖金结果。 */
public record CustomerBonusPayout(
        Long receiverAgentId,
        Integer relationshipLevel,
        String bonusCode,
        BigDecimal rate,
        BigDecimal amount,
        String remark
) {
}
