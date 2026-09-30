package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsOrderRelationSnapshotDao;
import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.service.DirectReferralConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectReferralBonusPolicyTest {
    private DirectReferralConfigService configService;
    private DmsOrderRelationSnapshotDao snapshotDao;
    private DirectReferralBonusPolicy policy;

    @BeforeEach
    void setUp() {
        configService = mock(DirectReferralConfigService.class);
        snapshotDao = mock(DmsOrderRelationSnapshotDao.class);
        policy = new DirectReferralBonusPolicy(configService, snapshotDao);
    }

    @Test
    void ordinaryBuyerCreatesOnePayoutForFrozenDirectInviterOnly() {
        configured(DirectReferralRuleConfig.ALL_ORDERS, "0.075");
        DmsOrderRelationSnapshot buyer = snapshot(0, 10L, null, 0);
        buyer.setFirstPaidOrderEligible(1);
        when(snapshotDao.selectByOrderId(50L)).thenReturn(List.of(buyer,
                snapshot(1, 20L, 200L, 1), snapshot(2, 30L, 300L, 1)));

        List<CustomerBonusPayout> result = policy.calculate(context("89.01"));

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).receiverAgentId());
        assertEquals("DIRECT_REFERRAL", result.get(0).bonusCode());
        assertEquals(new BigDecimal("6.68"), result.get(0).amount());
        assertEquals(1, result.get(0).relationshipLevel());
        assertNull(buyer.getTargetAgentId(), "普通客户没有代理账号也能购买");
        verify(configService).frozen(1L, 8L);
    }

    @Test
    void ineligibleDirectInviterNeverBypassesToAnEligibleAncestor() {
        configured(DirectReferralRuleConfig.ALL_ORDERS, "0.1");
        when(snapshotDao.selectByOrderId(50L)).thenReturn(List.of(snapshot(0, 10L, null, 0),
                snapshot(1, 20L, null, 0), snapshot(2, 30L, 300L, 1)));

        assertTrue(policy.calculate(context("100.00")).isEmpty());
    }

    @Test
    void firstOrderRuleUsesFrozenEligibilityAndDoesNotReadLivePaymentHistory() {
        configured(DirectReferralRuleConfig.FIRST_PAID_ORDER, "0.1");
        DmsOrderRelationSnapshot buyer = snapshot(0, 10L, null, 0);
        buyer.setFirstPaidOrderEligible(0);
        when(snapshotDao.selectByOrderId(50L)).thenReturn(List.of(buyer, snapshot(1, 20L, 200L, 1)));
        assertTrue(policy.calculate(context("100")).isEmpty());

        buyer.setFirstPaidOrderEligible(1);
        assertEquals(new BigDecimal("10.00"), policy.calculate(context("100")).get(0).amount());
    }

    @Test
    void missingFirstOrderSnapshotAndCrossTenantTargetFailClosed() {
        configured(DirectReferralRuleConfig.FIRST_PAID_ORDER, "0.1");
        DmsOrderRelationSnapshot buyer = snapshot(0, 10L, null, 0);
        when(snapshotDao.selectByOrderId(50L)).thenReturn(List.of(buyer, snapshot(1, 20L, 200L, 1)));
        assertThrows(ApiException.class, () -> policy.calculate(context("100")));

        buyer.setFirstPaidOrderEligible(1);
        DmsOrderRelationSnapshot foreign = snapshot(1, 20L, 200L, 1);
        foreign.setTenantId(2L);
        when(snapshotDao.selectByOrderId(50L)).thenReturn(List.of(buyer, foreign));
        assertThrows(ApiException.class, () -> policy.calculate(context("100")));
    }

    @Test
    void duplicatesAndMissingQualificationDoNotProduceAmbiguousMoney() {
        configured(DirectReferralRuleConfig.ALL_ORDERS, "0.1");
        DmsOrderRelationSnapshot target = snapshot(1, 20L, 200L, 1);
        when(snapshotDao.selectByOrderId(50L)).thenReturn(List.of(snapshot(0, 10L, null, 0), target,
                snapshot(1, 30L, 300L, 1)));
        assertThrows(ApiException.class, () -> policy.calculate(context("100")));
        target.setTargetPromotionEligible(null);
        when(snapshotDao.selectByOrderId(50L)).thenReturn(List.of(snapshot(0, 10L, null, 0), target));
        assertThrows(ApiException.class, () -> policy.calculate(context("100")));
    }

    @Test
    void disabledAndZeroBaseDoNotCreateZeroAmountLedgerRows() {
        when(configService.frozen(1L, 8L)).thenReturn(DirectReferralRuleConfig.disabled());
        assertTrue(policy.calculate(context("100")).isEmpty());
        configured(DirectReferralRuleConfig.ALL_ORDERS, "0.0001");
        assertTrue(policy.calculate(context("0")).isEmpty());
        when(snapshotDao.selectByOrderId(50L)).thenReturn(List.of(snapshot(0, 10L, null, 0),
                snapshot(1, 20L, 200L, 1)));
        assertTrue(policy.calculate(context("0.01")).isEmpty());
    }

    private void configured(String scope, String rate) {
        when(configService.frozen(1L, 8L)).thenReturn(
                new DirectReferralRuleConfig(true, new BigDecimal(rate), scope, 7));
    }

    private CustomerBonusOrderContext context(String amount) {
        return new CustomerBonusOrderContext(1L, 8L, 50L, "SO50", new BigDecimal(amount), 10L, "普通客户");
    }

    private DmsOrderRelationSnapshot snapshot(int level, Long targetUserId, Long targetAgentId, Integer eligible) {
        DmsOrderRelationSnapshot row = new DmsOrderRelationSnapshot();
        row.setTenantId(1L); row.setRuleVersionId(8L); row.setOrderId(50L); row.setOrderUserId(10L);
        row.setRelationLevel(level); row.setTargetUserId(targetUserId); row.setTargetAgentId(targetAgentId);
        row.setTargetPromotionEligible(eligible);
        return row;
    }
}
