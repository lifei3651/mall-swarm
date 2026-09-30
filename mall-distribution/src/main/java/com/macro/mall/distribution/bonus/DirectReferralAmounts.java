package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 基础直推仅以参与商品实际支付的商品款计佣，优惠承担方不影响口径。 */
public final class DirectReferralAmounts {
    private DirectReferralAmounts() { }

    public static boolean eligible(DmsShopOrderItem item) {
        if (item == null) return false;
        String mode = item.getTeamBonusMode();
        return mode == null || mode.isBlank() || "INHERIT".equals(mode) || "STANDARD".equals(mode);
    }

    public static BigDecimal paidGoods(DmsShopOrder order, List<DmsShopOrderItem> items) {
        if (order == null || items == null || items.isEmpty()) Asserts.fail("直接推荐佣金缺少支付商品快照");
        BigDecimal gross = BigDecimal.ZERO, eligibleGross = BigDecimal.ZERO, couponNet = BigDecimal.ZERO;
        for (DmsShopOrderItem item : items) {
            if (item == null || item.getTotalAmount() == null || item.getTotalAmount().signum() < 0)
                Asserts.fail("直接推荐佣金商品金额快照不正确");
            gross = gross.add(item.getTotalAmount());
            if (order.getCouponClaimId() != null) {
                BigDecimal discount = item.getCouponDiscountAmount();
                if (discount == null || discount.signum() < 0 || discount.compareTo(item.getTotalAmount()) > 0)
                    Asserts.fail("直接推荐佣金优惠分摊快照不正确");
                if (eligible(item)) couponNet = couponNet.add(item.getTotalAmount().subtract(discount));
            }
            if (eligible(item)) eligibleGross = eligibleGross.add(item.getTotalAmount());
        }
        if (order.getCouponClaimId() != null) return couponNet.setScale(2, RoundingMode.HALF_UP);
        if (gross.signum() == 0 || eligibleGross.signum() == 0) return BigDecimal.ZERO.setScale(2);
        BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        if (discount.signum() < 0 || discount.compareTo(gross) > 0) Asserts.fail("直接推荐佣金订单优惠快照不正确");
        return eligibleGross.subtract(discount.multiply(eligibleGross).divide(gross, 2, RoundingMode.HALF_UP))
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
