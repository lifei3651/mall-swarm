package com.macro.mall.distribution.util;

import com.macro.mall.common.exception.Asserts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Exact cents, deterministic allocation and cumulative refund deltas. */
public final class CouponAmounts {
    private CouponAmounts() {}
    public static BigDecimal money(BigDecimal n) { return n == null ? BigDecimal.ZERO.setScale(2) : n.setScale(2, RoundingMode.HALF_UP); }

    public static List<BigDecimal> allocate(BigDecimal discount, List<BigDecimal> gross) {
        if (gross == null || gross.isEmpty() || gross.stream().anyMatch(n -> n == null || n.signum() < 0)) Asserts.fail("优惠分摊商品金额异常");
        long total = gross.stream().map(CouponAmounts::money).reduce(BigDecimal.ZERO, BigDecimal::add).movePointRight(2).longValueExact();
        long cents = money(discount).movePointRight(2).longValueExact();
        if (cents < 0 || total <= 0 || cents >= total) Asserts.fail("优惠后可用商品金额必须大于0");
        List<BigDecimal> result = new ArrayList<>();
        long assigned = 0;
        for (BigDecimal value : gross) {
            long part = BigDecimal.valueOf(cents).multiply(money(value)).divide(BigDecimal.valueOf(total).movePointLeft(2),0,RoundingMode.DOWN).longValueExact();
            result.add(BigDecimal.valueOf(part,2)); assigned += part;
        }
        // At most one rounding cent per positive line; stable order keeps quote and submit consistent.
        for (int i=0; assigned<cents && i<result.size(); i++) {
            if (result.get(i).compareTo(money(gross.get(i))) < 0) {
                result.set(i,result.get(i).add(new BigDecimal("0.01"))); assigned++;
            }
        }
        if (assigned != cents) Asserts.fail("优惠金额分摊异常");
        return result;
    }

    public static List<BigDecimal> merchantParts(BigDecimal amount, int percent, List<BigDecimal> discounts) {
        BigDecimal burden = money(amount.multiply(BigDecimal.valueOf(percent)).divide(new BigDecimal("100")));
        if (burden.signum() == 0) return discounts.stream().map(v -> money(null)).toList();
        if (burden.compareTo(money(amount)) == 0) return discounts;
        return allocate(burden, discounts);
    }

    public static BigDecimal refundDelta(BigDecimal total, int quantity, int returned, int current) {
        if (quantity<=0 || returned<0 || current<=0 || returned>quantity-current) Asserts.fail("优惠订单退款数量异常");
        BigDecimal base=money(total);
        if (base.signum()<0) Asserts.fail("优惠订单金额异常");
        return base.multiply(BigDecimal.valueOf(returned+current)).divide(BigDecimal.valueOf(quantity),2,RoundingMode.DOWN)
                .subtract(base.multiply(BigDecimal.valueOf(returned)).divide(BigDecimal.valueOf(quantity),2,RoundingMode.DOWN));
    }
}
