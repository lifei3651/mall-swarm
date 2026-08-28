package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.bonus.CustomerBonusOrderContext;
import com.macro.mall.distribution.bonus.CustomerBonusPolicy;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyRegistry;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.PerformanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionPolicyVersionFreezeTest {

    @Mock private DmsCommissionRecordDao recordDao;
    @Mock private DmsCommissionRuleVersionDao ruleVersionDao;
    @Mock private DmsOrderRelationSnapshotDao orderRelationSnapshotDao;
    @Mock private DmsAgentDao agentDao;
    @Mock private DmsAgentRelationDao relationDao;
    @Mock private DmsAgentAccountDao accountDao;
    @Mock private DmsCommissionClawbackDao clawbackDao;
    @Mock private DmsShopOrderDao shopOrderDao;
    @Mock private DmsShopAfterSaleDao shopAfterSaleDao;
    @Mock private DmsShopMemberDao shopMemberDao;
    @Mock private AgentAccountService accountService;
    @Mock private DistributionAuditService auditService;
    @Mock private MemberAssetService memberAssetService;
    @Mock private PerformanceService performanceService;
    @Mock private ShopAfterSaleWindowPolicy afterSaleWindowPolicy;
    @Mock private CustomerBonusPolicyRegistry bonusPolicyRegistry;
    @Mock private CustomerBonusPolicy frozenPolicy;

    @InjectMocks private CommissionServiceImpl service;

    @Test
    void calculationUsesPolicyVersionFrozenAtPaymentInsteadOfCurrentActiveVersion() {
        DmsOrderRelationSnapshot owner = snapshot(500L, 12L);
        DmsOrderRelationSnapshot inviter = snapshot(500L, 12L);
        when(orderRelationSnapshotDao.selectByOrderId(500L)).thenReturn(List.of(owner, inviter));

        DmsCommissionRuleVersion frozenVersion = version(12L, 7L, "CUSTOMER_ALPHA_V1");
        when(ruleVersionDao.selectById(7L, 12L)).thenReturn(frozenVersion);
        when(recordDao.selectByOrderId(500L)).thenReturn(List.of());
        when(bonusPolicyRegistry.require("CUSTOMER_ALPHA_V1")).thenReturn(frozenPolicy);
        when(frozenPolicy.calculate(any(CustomerBonusOrderContext.class))).thenReturn(List.of());

        service.calculateAndRecordCommission(
                7L, 500L, "ORDER-500", new BigDecimal("299.00"), 1008L, "客户会员");

        ArgumentCaptor<CustomerBonusOrderContext> context = ArgumentCaptor.forClass(CustomerBonusOrderContext.class);
        verify(frozenPolicy).calculate(context.capture());
        assertEquals(7L, context.getValue().tenantId());
        assertEquals(12L, context.getValue().ruleVersionId());
        verify(frozenPolicy).afterOrder(context.getValue());
        verify(ruleVersionDao, never()).selectActiveByTenantId(anyLong());
        verify(auditService).refreshOrderFinance(500L, "ORDER-500", new BigDecimal("299.00"));
    }

    @Test
    void historicalOrderWithoutSnapshotFallsBackToCurrentPolicy() {
        when(orderRelationSnapshotDao.selectByOrderId(501L)).thenReturn(List.of());
        when(ruleVersionDao.selectActiveByTenantId(7L))
                .thenReturn(version(13L, 7L, "CUSTOMER_BONUS_DISABLED"));
        when(recordDao.selectByOrderId(501L)).thenReturn(List.of());
        when(bonusPolicyRegistry.require("CUSTOMER_BONUS_DISABLED")).thenReturn(frozenPolicy);
        when(frozenPolicy.calculate(any(CustomerBonusOrderContext.class))).thenReturn(List.of());

        service.calculateAndRecordCommission(
                7L, 501L, "IMPORT-501", new BigDecimal("100.00"), 1008L, "历史会员");

        verify(ruleVersionDao).selectActiveByTenantId(7L);
        verify(ruleVersionDao, never()).selectById(anyLong(), anyLong());
    }

    @Test
    void calculationStopsWhenOneOrderContainsDifferentFrozenPolicyVersions() {
        when(orderRelationSnapshotDao.selectByOrderId(502L))
                .thenReturn(List.of(snapshot(502L, 12L), snapshot(502L, 13L)));

        ApiException exception = assertThrows(ApiException.class, () -> service.calculateAndRecordCommission(
                7L, 502L, "ORDER-502", new BigDecimal("100.00"), 1008L, "异常会员"));

        assertEquals("订单关系快照包含多个客户奖金程序版本，已阻止不一致计算", exception.getMessage());
        verifyNoInteractions(ruleVersionDao, bonusPolicyRegistry, frozenPolicy);
    }

    private DmsOrderRelationSnapshot snapshot(Long orderId, Long ruleVersionId) {
        DmsOrderRelationSnapshot snapshot = new DmsOrderRelationSnapshot();
        snapshot.setOrderId(orderId);
        snapshot.setRuleVersionId(ruleVersionId);
        return snapshot;
    }

    private DmsCommissionRuleVersion version(Long id, Long tenantId, String versionNo) {
        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setId(id);
        version.setTenantId(tenantId);
        version.setVersionNo(versionNo);
        version.setStatus(1);
        return version;
    }
}
