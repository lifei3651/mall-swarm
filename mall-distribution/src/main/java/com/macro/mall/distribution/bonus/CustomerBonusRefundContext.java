package com.macro.mall.distribution.bonus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 基座完成退款业绩冲销与奖金追回后，交给客户程序的不可变退款事件。 */
public record CustomerBonusRefundContext(
        Long tenantId,
        Long ruleVersionId,
        Long orderId,
        Long refundId,
        BigDecimal productRefundAmount,
        Integer refundQuantity,
        LocalDateTime refundTime
) {
}
