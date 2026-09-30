package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class OrderRelationSnapshotRuleLockTest {
    @Test
    void legacyPaymentFreezesTheCurrentReadVersionRatherThanAnEarlierTransactionSnapshot() {
        DmsOrderRelationSnapshotDao snapshots = mock(DmsOrderRelationSnapshotDao.class);
        DmsAgentDao agents = mock(DmsAgentDao.class);
        DmsAgentRelationDao relations = mock(DmsAgentRelationDao.class);
        DmsCommissionRuleVersionDao versions = mock(DmsCommissionRuleVersionDao.class);
        OrderRelationSnapshotServiceImpl service = new OrderRelationSnapshotServiceImpl(
                snapshots, agents, relations, versions, mock(DmsShopMemberDao.class));
        DmsShopOrder order = new DmsShopOrder();
        order.setId(800L); order.setTenantId(7L); order.setUserId(900L); order.setOrderNo("RULE-LOCK-800");
        DmsAgent owner = new DmsAgent();
        owner.setId(50L); owner.setUserId(900L); owner.setAgentName("历史客户会员");
        DmsCommissionRuleVersion latest = new DmsCommissionRuleVersion();
        latest.setId(12L); latest.setTenantId(7L); latest.setVersionNo("CUSTOMER_ALPHA_V2");
        when(agents.selectByUserId(900L)).thenReturn(owner);
        when(snapshots.selectByOrderId(800L)).thenReturn(List.of());
        when(relations.selectValidRelationsByUserId(900L)).thenReturn(List.of());
        when(versions.selectActiveByTenantIdForUpdate(7L)).thenReturn(latest);

        service.capture(order);

        ArgumentCaptor<DmsOrderRelationSnapshot> frozen = ArgumentCaptor.forClass(DmsOrderRelationSnapshot.class);
        verify(snapshots).insert(frozen.capture());
        assertEquals(12L, frozen.getValue().getRuleVersionId());
        assertEquals(7L, frozen.getValue().getTenantId());
        verify(versions).selectActiveByTenantIdForUpdate(7L);
        verify(versions, never()).selectActiveByTenantId(anyLong());
    }
}
