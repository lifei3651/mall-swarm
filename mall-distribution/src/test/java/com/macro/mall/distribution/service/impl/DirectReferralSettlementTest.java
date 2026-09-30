package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.bonus.DirectReferralRuleConfig;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DirectReferralSettlementTest {
    @Mock DmsCommissionRecordDao recordDao;
    @Mock DmsCommissionRuleVersionDao ruleVersionDao;
    @Mock DmsOrderRelationSnapshotDao orderRelationSnapshotDao;
    @Mock DmsAgentDao agentDao;
    @Mock DmsAgentRelationDao relationDao;
    @Mock DmsAgentAccountDao accountDao;
    @Mock DmsCommissionClawbackDao clawbackDao;
    @Mock DmsShopOrderDao shopOrderDao;
    @Mock DmsShopAfterSaleDao shopAfterSaleDao;
    @Mock DmsShopMemberDao shopMemberDao;
    @Mock AgentAccountService accountService;
    @Mock DistributionAuditService auditService;
    @Mock MemberAssetService memberAssetService;
    @Mock PerformanceService performanceService;
    @Mock ShopAfterSaleWindowPolicy afterSaleWindowPolicy;
    @Mock DirectReferralConfigService directReferralConfigService;
    @InjectMocks CommissionServiceImpl service;
    DmsCommissionRecord record;
    DmsShopOrder order;

    @BeforeEach void setup() {
        record = new DmsCommissionRecord();
        record.setId(10L); record.setTenantId(1L); record.setRuleVersionId(30L);
        record.setOrderId(20L); record.setAgentId(40L); record.setStatus(0);
        record.setBonusType("DIRECT_REFERRAL"); record.setCommissionAmount(new BigDecimal("10.00"));
        order = new DmsShopOrder(); order.setId(20L); order.setTenantId(1L); order.setStatus(3);
        order.setReceiveTime(LocalDateTime.now().minusDays(15));
        when(recordDao.selectById(10L)).thenReturn(record);
        when(recordDao.selectByIdForUpdate(10L)).thenReturn(record);
        when(shopOrderDao.selectByIdForUpdate(20L)).thenReturn(order);
        when(afterSaleWindowPolicy.deadline(order)).thenReturn(LocalDateTime.now().minusDays(8));
        when(directReferralConfigService.frozen(1L, 30L)).thenReturn(
                new DirectReferralRuleConfig(true, new BigDecimal("0.1"), "ALL_ORDERS", 7));
        when(accountDao.selectByAgentIdForUpdate(40L)).thenReturn(new DmsAgentAccount());
        when(clawbackDao.updateDebtAfterOffset(anyLong(), any(), any(), anyInt())).thenReturn(1);
        when(clawbackDao.insert(any())).thenReturn(1);
    }

    @Test void everySettlementEntryRejectsUnreceivedOrder() {
        order.setStatus(2); order.setReceiveTime(null);
        assertFalse(service.settleCommission(10L));
        assertFalse(service.settleCommissionIfEligible(10L));
        assertEquals(0, service.settleCommissionBatch(List.of(10L)));
        verifyNoInteractions(accountService, memberAssetService);
    }

    @Test void afterSaleWindowAndFrozenDelayBothProtectCommission() {
        when(afterSaleWindowPolicy.deadline(order)).thenReturn(LocalDateTime.now().plusDays(1));
        assertFalse(service.settleCommission(10L));
        when(afterSaleWindowPolicy.deadline(order)).thenReturn(LocalDateTime.now().minusDays(1));
        order.setReceiveTime(LocalDateTime.now().minusDays(6));
        assertFalse(service.settleCommission(10L));
        verifyNoInteractions(memberAssetService);
    }

    @Test void openAfterSaleBlocksReleaseEvenWhenTimeHasElapsed() {
        when(shopAfterSaleDao.selectOpenByOrderId(20L)).thenReturn(new DmsShopAfterSale());
        assertFalse(service.settleCommission(10L));
        verifyNoInteractions(memberAssetService);
    }

    @Test void releaseUsesFrozenRuleAndLocksOrderBeforeRecordAndAccount() {
        assertTrue(service.settleCommission(10L));
        var ordered = inOrder(shopOrderDao, recordDao, accountDao);
        ordered.verify(recordDao).selectById(10L);
        ordered.verify(shopOrderDao).selectByIdForUpdate(20L);
        ordered.verify(recordDao).selectByIdForUpdate(10L);
        ordered.verify(accountDao).selectByAgentIdForUpdate(40L);
        verify(directReferralConfigService).frozen(1L, 30L);
        verify(ruleVersionDao, never()).selectActiveByTenantId(anyLong());
        verify(accountService).settleCommission(40L, new BigDecimal("10.00"));
        verify(memberAssetService).issue(argThat(dto -> new BigDecimal("10.00").equals(dto.getAmount())));
        assertFalse(service.settleCommission(10L), "已结记录不能重复入账");
    }

    @Test void debtOffsetAtReleaseKeepsFullAwardButOnlyIssuesNetCash() {
        DmsCommissionClawback debt = new DmsCommissionClawback();
        debt.setId(90L); debt.setDebtAmount(new BigDecimal("6.00")); debt.setDeductedAmount(BigDecimal.ZERO);
        when(clawbackDao.selectPendingDebtByAgentId(40L)).thenReturn(List.of(debt));
        assertTrue(service.settleCommission(10L));
        assertEquals(new BigDecimal("10.00"), record.getCommissionAmount());
        verify(accountService).settleCommission(40L, new BigDecimal("10.00"));
        verify(clawbackDao).updateDebtAfterOffset(90L, new BigDecimal("6.00"), new BigDecimal("0.00"), 1);
        verify(clawbackDao).insert(argThat(flow -> Integer.valueOf(4).equals(flow.getClawbackType())
                && Long.valueOf(90L).equals(flow.getSourceClawbackId())));
        verify(memberAssetService).issue(argThat(dto -> new BigDecimal("4.00").equals(dto.getAmount())));
    }

    @Test void fullyOffsetRewardNeverCreatesZeroCashWalletTransaction() {
        DmsCommissionClawback debt = new DmsCommissionClawback();
        debt.setId(90L); debt.setDebtAmount(new BigDecimal("20.00"));
        when(clawbackDao.selectPendingDebtByAgentId(40L)).thenReturn(List.of(debt));
        assertTrue(service.settleCommission(10L));
        verifyNoInteractions(memberAssetService);
        assertEquals(1, record.getStatus());
    }

    @Test void historicalCustomerManualSettlementIsNotReinterpreted() {
        record.setBonusType("DIRECT_REWARD"); order.setStatus(1); order.setReceiveTime(null);
        assertTrue(service.settleCommission(10L));
        verifyNoInteractions(directReferralConfigService);
        verify(memberAssetService).issue(any(AssetChangeDTO.class));
    }
}
