package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DirectReferralAmountsTest {
    @Test void onlyParticipatingGoodsCashCountsAndFreightIsExcluded() {
        DmsShopOrder order = new DmsShopOrder();
        order.setTotalAmount(new BigDecimal("100")); order.setDiscountAmount(new BigDecimal("10"));
        order.setFreightAmount(new BigDecimal("8")); order.setPayAmount(new BigDecimal("98"));
        assertEquals(new BigDecimal("18.00"), DirectReferralAmounts.paidGoods(order,
                List.of(item("20", "INHERIT", "0"), item("80", "NONE", "0"))));
    }
    @Test void platformFundedCouponDoesNotInflateActualPaidBase() {
        DmsShopOrder order = new DmsShopOrder(); order.setCouponClaimId(4L);
        DmsShopOrderItem item = item("100", "STANDARD", "30");
        item.setCouponBonusBaseAmount(new BigDecimal("100"));
        assertEquals(new BigDecimal("70.00"), DirectReferralAmounts.paidGoods(order, List.of(item)));
    }
    @Test void noCouponAndNoDiscountProducesCashGoodsTotal() {
        assertEquals(new BigDecimal("19.99"), DirectReferralAmounts.paidGoods(new DmsShopOrder(),
                List.of(item("19.99", null, null))));
    }
    @Test void missingOrInvalidSnapshotsNeverGuessCommission() {
        assertThrows(ApiException.class, () -> DirectReferralAmounts.paidGoods(new DmsShopOrder(), List.of()));
        DmsShopOrder order = new DmsShopOrder(); order.setCouponClaimId(1L);
        assertThrows(ApiException.class, () -> DirectReferralAmounts.paidGoods(order, List.of(item("100", "INHERIT", null))));
        assertThrows(ApiException.class, () -> DirectReferralAmounts.paidGoods(order, List.of(item("100", "INHERIT", "110"))));
    }
    private DmsShopOrderItem item(String gross, String mode, String coupon) {
        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setTotalAmount(new BigDecimal(gross)); item.setTeamBonusMode(mode);
        if (coupon != null) item.setCouponDiscountAmount(new BigDecimal(coupon));
        return item;
    }
}
