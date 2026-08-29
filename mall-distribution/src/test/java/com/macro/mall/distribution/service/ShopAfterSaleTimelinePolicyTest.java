package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.service.impl.ShopAfterSaleTimelinePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopAfterSaleTimelinePolicyTest {

    @Test
    void merchantDeadlinesEscalateToPlatformWithoutChangingRefundStatus() {
        ShopAfterSaleTimelinePolicy policy = policy();
        DmsShopAfterSale pending = new DmsShopAfterSale();
        pending.setStatus(0);
        pending.setCreateTime(LocalDateTime.now().minusHours(49));

        policy.enrich(pending);

        assertEquals(0, pending.getStatus());
        assertEquals("MERCHANT", pending.getNextActionParty());
        assertTrue(pending.getNextActionOverdue());
        assertTrue(pending.getNextActionHint().contains("平台客服优先介入"));
        assertNotNull(pending.getNextActionDeadline());
    }

    @Test
    void returnShipmentAndMerchantReceiptUseDifferentResponsibleParties() {
        ShopAfterSaleTimelinePolicy policy = policy();
        DmsShopAfterSale waitingMember = new DmsShopAfterSale();
        waitingMember.setStatus(4);
        waitingMember.setAuditTime(LocalDateTime.now().minusDays(2));
        policy.enrich(waitingMember);
        assertEquals("MEMBER", waitingMember.getNextActionParty());
        assertFalse(waitingMember.getNextActionOverdue());

        DmsShopAfterSale waitingMerchant = new DmsShopAfterSale();
        waitingMerchant.setStatus(5);
        waitingMerchant.setReturnShippedAt(LocalDateTime.now().minusDays(8));
        policy.enrich(waitingMerchant);
        assertEquals("MERCHANT", waitingMerchant.getNextActionParty());
        assertTrue(waitingMerchant.getNextActionOverdue());
        assertEquals(5, waitingMerchant.getStatus());
    }

    @Test
    void exchangeShipmentAndReceiptExposeTheCorrectResponsibleParty() {
        ShopAfterSaleTimelinePolicy policy = policy();
        DmsShopAfterSale waitingMerchantShipment = new DmsShopAfterSale();
        waitingMerchantShipment.setApplyType(3);
        waitingMerchantShipment.setStatus(7);
        waitingMerchantShipment.setReturnReceivedAt(LocalDateTime.now().minusDays(4));
        policy.enrich(waitingMerchantShipment);
        assertEquals("MERCHANT", waitingMerchantShipment.getNextActionParty());
        assertTrue(waitingMerchantShipment.getNextActionOverdue());
        assertTrue(waitingMerchantShipment.getNextActionHint().contains("换货商品"));

        DmsShopAfterSale waitingMemberReceipt = new DmsShopAfterSale();
        waitingMemberReceipt.setApplyType(3);
        waitingMemberReceipt.setStatus(8);
        waitingMemberReceipt.setExchangeShippedAt(LocalDateTime.now().minusDays(2));
        policy.enrich(waitingMemberReceipt);
        assertEquals("MEMBER", waitingMemberReceipt.getNextActionParty());
        assertFalse(waitingMemberReceipt.getNextActionOverdue());
        assertTrue(waitingMemberReceipt.getNextActionHint().contains("确认收货"));
    }

    private ShopAfterSaleTimelinePolicy policy() {
        ShopAfterSaleTimelinePolicy policy = new ShopAfterSaleTimelinePolicy();
        ReflectionTestUtils.setField(policy, "merchantAuditTimeoutHours", 48);
        ReflectionTestUtils.setField(policy, "returnShipmentTimeoutDays", 7);
        ReflectionTestUtils.setField(policy, "merchantReturnConfirmTimeoutDays", 7);
        ReflectionTestUtils.setField(policy, "merchantExchangeShipmentTimeoutDays", 3);
        ReflectionTestUtils.setField(policy, "exchangeAutoReceiveDays", 15);
        return policy;
    }
}
