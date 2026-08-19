package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.enums.CommissionStatusEnum;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.PerformanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionBulkSettlementTransactionTest {

    @Mock private DmsCommissionRecordDao recordDao;
    @Mock private DmsCommissionRuleVersionDao ruleVersionDao;
    @Mock private DmsAgentDao agentDao;
    @Mock private DmsAgentRelationDao relationDao;
    @Mock private DmsOrderRelationSnapshotDao relationSnapshotDao;
    @Mock private DmsAgentAccountDao accountDao;
    @Mock private DmsCommissionClawbackDao clawbackDao;
    @Mock private DmsShopOrderDao shopOrderDao;
    @Mock private DmsShopAfterSaleDao shopAfterSaleDao;
    @Mock private DmsShopMemberDao shopMemberDao;
    @Mock private AgentAccountService accountService;
    @Mock private DistributionAuditService auditService;
    @Mock private MemberAssetService memberAssetService;
    @Mock private NewRetailRankService newRetailRankService;
    @Mock private PerformanceService performanceService;
    @Mock private ShopAfterSaleWindowPolicy afterSaleWindowPolicy;

    @InjectMocks private CommissionServiceImpl service;

    @Test
    void bulkSettlementPropagatesAccountingFailureSoTransactionCanRollback() {
        DmsCommissionRecord record = new DmsCommissionRecord();
        record.setId(10L);
        record.setAgentId(20L);
        record.setCommissionAmount(new BigDecimal("88.00"));
        record.setStatus(CommissionStatusEnum.PENDING.getValue());
        when(recordDao.selectByAgentIdAndStatus(20L, CommissionStatusEnum.PENDING.getValue()))
                .thenReturn(List.of(record));
        when(recordDao.selectByIdForUpdate(10L)).thenReturn(record);
        doThrow(new IllegalStateException("模拟账户结算失败"))
                .when(accountService).settleCommission(20L, new BigDecimal("88.00"));

        assertThrows(IllegalStateException.class,
                () -> service.settleAgentAndDescendantCommissions(20L));

        verify(recordDao).update(record);
        verifyNoInteractions(memberAssetService);
        verifyNoInteractions(relationDao);
    }
}
