package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.Asserts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 客户程序结果进入资金系统前必须通过的通用安全不变量。 */
public final class CustomerBonusPayoutValidator {

    private CustomerBonusPayoutValidator() {
    }

    public static List<CustomerBonusPayout> validate(CustomerBonusOrderContext context,
                                                      List<CustomerBonusPayout> payouts) {
        if (context == null || context.tenantId() == null || context.ruleVersionId() == null
                || context.orderId() == null || context.bonusBaseAmount() == null) {
            Asserts.fail("客户奖金程序缺少完整订单上下文");
        }
        if (payouts == null) Asserts.fail("客户奖金程序返回结果不能为空，请使用空列表表示本单无奖金");
        BigDecimal bonusBase = context.bonusBaseAmount().setScale(2, RoundingMode.HALF_UP);
        if (bonusBase.compareTo(BigDecimal.ZERO) < 0) Asserts.fail("订单奖金基数不能为负数");
        BigDecimal total = BigDecimal.ZERO;
        Set<String> uniqueReceivers = new HashSet<>();
        for (CustomerBonusPayout payout : payouts) {
            if (payout == null) Asserts.fail("客户奖金程序返回了空的奖金明细");
            if (payout.receiverAgentId() == null || payout.receiverAgentId() <= 0) {
                Asserts.fail("客户奖金程序返回了无效接收人");
            }
            String bonusCode = payout.bonusCode() == null ? "" : payout.bonusCode().trim();
            if (bonusCode.isBlank() || bonusCode.length() > 32) {
                Asserts.fail("客户奖金类型代码不能为空且不能超过32个字符");
            }
            if (payout.relationshipLevel() != null && payout.relationshipLevel() < 0) {
                Asserts.fail("客户奖金关系深度不能为负数");
            }
            BigDecimal amount = payout.amount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
                Asserts.fail("客户奖金金额不能为空或负数");
            }
            if (amount.scale() > 2) {
                Asserts.fail("客户奖金金额最多保留两位小数");
            }
            BigDecimal rate = payout.rate();
            if (rate != null && (rate.compareTo(BigDecimal.ZERO) < 0
                    || rate.compareTo(BigDecimal.TEN) >= 0 || rate.scale() > 4)) {
                Asserts.fail("客户奖金比例超出通用记录范围");
            }
            if (payout.remark() != null && payout.remark().length() > 256) {
                Asserts.fail("客户奖金审计说明不能超过256个字符");
            }
            String uniqueKey = payout.receiverAgentId() + ":" + bonusCode;
            if (!uniqueReceivers.add(uniqueKey)) {
                Asserts.fail("同一订单、接收人和奖金类型不能重复返回");
            }
            total = total.add(amount.setScale(2, RoundingMode.UNNECESSARY));
        }
        if (total.compareTo(bonusBase) > 0) {
            Asserts.fail("本单客户奖金合计不能超过订单奖金基数");
        }
        return List.copyOf(payouts);
    }
}
