package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsAgentRelation;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.enums.AgentStatusEnum;
import com.macro.mall.distribution.service.impl.AgentServiceImpl;
import com.macro.mall.distribution.vo.AgentTeamMemberCountVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void lineChangeLoadsSubtreeAndRefreshesCountsInBatches() {
        Fixture fixture = new Fixture();
        when(fixture.tenantDao.selectByIdForUpdate(1L)).thenReturn(new DmsTenant());

        DmsAgent root = agent(10L, 100L, 5L, "5", 2);
        DmsAgent newParent = agent(20L, 200L, 30L, "30", 2);
        newParent.setStatus(AgentStatusEnum.NORMAL.getValue());
        DmsAgent oldParent = agent(5L, 50L, null, null, 1);
        DmsAgent child = agent(11L, 110L, 10L, "5,10", 3);
        DmsAgent grandchild = agent(12L, 120L, 11L, "5,10,11", 4);

        when(fixture.agentDao.selectById(10L)).thenReturn(root);
        when(fixture.agentDao.selectById(20L)).thenReturn(newParent);
        when(fixture.agentDao.selectById(5L)).thenReturn(oldParent);
        when(fixture.agentDao.selectByIds(List.of(11L, 12L))).thenReturn(List.of(child, grandchild));
        when(fixture.relationDao.selectAllDescendants(10L)).thenReturn(List.of(
                relation(11L), relation(12L)));
        when(fixture.relationService.bindRelation(anyLong(), anyLong(), anyLong(), anyLong(), anyInt()))
                .thenReturn(true);
        AgentTeamMemberCountVO newParentCount = new AgentTeamMemberCountVO();
        newParentCount.setAgentId(20L);
        newParentCount.setTeamMemberCount(2);
        when(fixture.relationDao.selectTeamMemberCounts(List.of(5L, 20L, 30L)))
                .thenReturn(List.of(newParentCount));

        AgentSwitchLineDTO dto = new AgentSwitchLineDTO();
        dto.setAgentId(10L);
        dto.setNewParentAgentId(20L);
        dto.setReason("批量查询测试");
        fixture.service.switchLine(dto);

        verify(fixture.agentDao).selectByIds(List.of(11L, 12L));
        verify(fixture.agentDao, never()).selectById(11L);
        verify(fixture.agentDao, never()).selectById(12L);
        verify(fixture.relationDao).selectTeamMemberCounts(List.of(5L, 20L, 30L));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<AgentTeamMemberCountVO>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(fixture.accountDao).updateTotalTeamMembersBatch(captor.capture());
        assertEquals(List.of(5L, 20L, 30L), captor.getValue().stream()
                .map(AgentTeamMemberCountVO::getAgentId).toList());
        assertEquals(List.of(0, 2, 0), captor.getValue().stream()
                .map(AgentTeamMemberCountVO::getTeamMemberCount).toList());
        verify(fixture.accountDao, never()).updateTotalTeamMembers(anyLong(), anyInt());
    }

    private static DmsAgent agent(Long id, Long userId, Long parentId, String ancestorIds, int levelDepth) {
        DmsAgent agent = new DmsAgent();
        agent.setId(id);
        agent.setUserId(userId);
        agent.setParentId(parentId);
        agent.setAncestorIds(ancestorIds);
        agent.setLevelDepth(levelDepth);
        agent.setAgentName("会员" + id);
        return agent;
    }

    private static DmsAgentRelation relation(Long agentId) {
        DmsAgentRelation relation = new DmsAgentRelation();
        relation.setAgentId(agentId);
        return relation;
    }

    private static final class Fixture {
        final DmsAgentDao agentDao = mock(DmsAgentDao.class);
        final DmsAgentRelationDao relationDao = mock(DmsAgentRelationDao.class);
        final DmsAgentAccountDao accountDao = mock(DmsAgentAccountDao.class);
        final AgentRelationService relationService = mock(AgentRelationService.class);
        final DmsTenantDao tenantDao = mock(DmsTenantDao.class);
        final AgentServiceImpl service = new AgentServiceImpl(
                agentDao,
                relationDao,
                accountDao,
                mock(DmsAgentChangeLogDao.class),
                mock(DmsShopMemberDao.class),
                mock(DmsShopMemberSessionDao.class),
                mock(DmsLineChangeApplicationDao.class),
                mock(DmsCommissionRecordDao.class),
                mock(DmsOrderBalanceAllocationDao.class),
                mock(DmsCommissionClawbackDao.class),
                relationService,
                mock(CommissionService.class),
                mock(PerformanceService.class),
                mock(OperationLogService.class),
                tenantDao);
    }
}
