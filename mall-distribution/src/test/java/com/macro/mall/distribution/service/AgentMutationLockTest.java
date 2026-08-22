package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.impl.AgentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AgentMutationLockTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void levelChangeLocksTenantMutationScopeBeforeReadingAgent() {
        Fixture fixture = new Fixture();
        DmsAgent agent = new DmsAgent();
        agent.setId(9L);
        agent.setAgentLevel(1);
        when(fixture.tenantDao.selectByIdForUpdate(1L)).thenReturn(new DmsTenant());
        when(fixture.agentDao.selectById(9L)).thenReturn(agent);

        fixture.service.adjustLevel(9L, 1, "保持级别");

        InOrder order = inOrder(fixture.tenantDao, fixture.agentDao);
        order.verify(fixture.tenantDao).selectByIdForUpdate(1L);
        order.verify(fixture.agentDao).selectById(9L);
    }

    @Test
    void lineChangeFailsClosedWhenMutationScopeCannotBeLocked() {
        Fixture fixture = new Fixture();
        when(fixture.tenantDao.selectByIdForUpdate(1L)).thenReturn(null);
        AgentSwitchLineDTO dto = new AgentSwitchLineDTO();
        dto.setAgentId(1L);
        dto.setNewParentAgentId(2L);
        dto.setReason("测试移线");

        assertThrows(ApiException.class, () -> fixture.service.switchLine(dto));
        verifyNoInteractions(fixture.agentDao);
    }

    private static final class Fixture {
        final DmsAgentDao agentDao = mock(DmsAgentDao.class);
        final DmsTenantDao tenantDao = mock(DmsTenantDao.class);
        final AgentServiceImpl service = new AgentServiceImpl(
                agentDao,
                mock(DmsAgentRelationDao.class),
                mock(DmsAgentAccountDao.class),
                mock(DmsAgentChangeLogDao.class),
                mock(DmsShopMemberDao.class),
                mock(DmsLineChangeApplicationDao.class),
                mock(DmsCommissionRecordDao.class),
                mock(DmsOrderBalanceAllocationDao.class),
                mock(DmsCommissionClawbackDao.class),
                mock(AgentRelationService.class),
                mock(CommissionService.class),
                mock(PerformanceService.class),
                mock(OperationLogService.class),
                tenantDao);
    }
}
