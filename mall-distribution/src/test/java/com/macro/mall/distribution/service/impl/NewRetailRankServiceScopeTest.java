package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsAgentChangeLogDao;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMigrationBaselineDao;
import com.macro.mall.distribution.dao.DmsOrderPerformanceDetailDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsOrderPerformanceDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewRetailRankServiceScopeTest {

    @Mock private DmsAgentDao agentDao;
    @Mock private DmsAgentChangeLogDao changeLogDao;
    @Mock private DmsOrderPerformanceDetailDao performanceDetailDao;
    @Mock private DmsMigrationBaselineDao migrationBaselineDao;
    @InjectMocks private NewRetailRankService rankService;

    @Test
    void newOrderRecalculatesOnlyAgentsAffectedByThatOrder() {
        DmsAgent affected = agent(960001L);
        DmsAgent unrelated = agent(960002L);
        DmsOrderPerformanceDetail detail = new DmsOrderPerformanceDetail();
        detail.setTargetAgentId(affected.getId());
        when(performanceDetailDao.selectByOrderId(970001L)).thenReturn(List.of(detail));
        when(agentDao.selectAll()).thenReturn(new ArrayList<>(List.of(affected, unrelated)));
        when(performanceDetailDao.sumEffectiveTeamUnits(affected.getId())).thenReturn(10);

        rankService.refreshRanksAfterOrder(970001L);

        verify(agentDao).update(argThat(item -> affected.getId().equals(item.getId())
                && Integer.valueOf(2).equals(item.getAgentLevel())));
        verify(agentDao, never()).update(argThat(item -> unrelated.getId().equals(item.getId())));
    }

    private DmsAgent agent(long id) {
        DmsAgent agent = new DmsAgent();
        agent.setId(id);
        agent.setUserId(id);
        agent.setAgentLevel(1);
        agent.setLevelDepth(1);
        agent.setStatus(1);
        return agent;
    }
}
