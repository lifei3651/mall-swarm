package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsAgentRelationDao;
import com.macro.mall.distribution.dao.DmsLineChangeApplicationDao;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsLineChangeApplication;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.impl.LineChangeApplicationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineChangeApplicationServiceTest {

    @Mock private DmsLineChangeApplicationDao applicationDao;
    @Mock private DmsAgentDao agentDao;
    @Mock private DmsAgentRelationDao relationDao;
    @Mock private AgentService agentService;
    @Mock private OperationLogService operationLogService;

    private LineChangeApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LineChangeApplicationServiceImpl(applicationDao, agentDao, relationDao,
                agentService, operationLogService, new ObjectMapper());
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(7L);
        admin.setUsername("operator");
        AdminContext.set(admin);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void submitExecutesImmediatelyAndStoresSnapshots() {
        DmsAgent moved = agent(20L, 10L);
        DmsAgent newParent = agent(30L, null);
        when(agentDao.selectById(20L)).thenReturn(moved);
        when(agentDao.selectById(30L)).thenReturn(newParent);
        when(relationDao.selectAllDescendants(20L)).thenReturn(List.of());
        when(agentService.switchLine(any())).thenReturn(true);
        when(applicationDao.insert(any())).thenAnswer(invocation -> {
            DmsLineChangeApplication value = invocation.getArgument(0);
            value.setId(99L);
            return 1;
        });
        when(applicationDao.markDirectExecuted(eq(99L), eq(7L), eq("operator"), any(), any(), any()))
                .thenReturn(1);
        DmsLineChangeApplication executed = new DmsLineChangeApplication();
        executed.setId(99L);
        executed.setStatus(3);
        when(applicationDao.selectById(99L)).thenReturn(executed);

        DmsLineChangeApplication result = service.submit(command());

        assertEquals(3, result.getStatus());
        ArgumentCaptor<DmsLineChangeApplication> inserted = ArgumentCaptor.forClass(DmsLineChangeApplication.class);
        verify(applicationDao).insert(inserted.capture());
        assertEquals(0, inserted.getValue().getStatus());
        assertEquals(7L, inserted.getValue().getApplicantId());
        assertNotNull(inserted.getValue().getBeforeSnapshot());
        verify(agentService).switchLine(any(AgentSwitchLineDTO.class));
        verify(applicationDao).markDirectExecuted(eq(99L), eq(7L), eq("operator"),
                eq("拥有移线管理权限，提交后直接生效"), any(), any());
        verify(operationLogService).log(eq("AGENT"), eq("LINE_CHANGE_EXECUTE"), eq("LINE_CHANGE"),
                eq("99"), any(), any(), eq("后台管理员直接移线：业务调整"));
    }

    @Test
    void failedMoveDoesNotMarkRecordExecuted() {
        when(agentDao.selectById(20L)).thenReturn(agent(20L, 10L));
        when(agentDao.selectById(30L)).thenReturn(agent(30L, null));
        when(relationDao.selectAllDescendants(20L)).thenReturn(List.of());
        when(applicationDao.insert(any())).thenAnswer(invocation -> {
            DmsLineChangeApplication value = invocation.getArgument(0);
            value.setId(99L);
            return 1;
        });
        when(agentService.switchLine(any())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.submit(command()));

        verify(applicationDao, never()).markDirectExecuted(any(), any(), any(), any(), any(), any());
        verify(operationLogService, never()).log(eq("AGENT"), eq("LINE_CHANGE_EXECUTE"),
                any(), any(), any(), any(), any());
    }

    @Test
    void pendingMoveReturnsMemberFacingMessage() {
        when(agentDao.selectById(20L)).thenReturn(agent(20L, 10L));
        when(agentDao.selectById(30L)).thenReturn(agent(30L, null));
        when(applicationDao.selectPendingByAgentId(20L)).thenReturn(new DmsLineChangeApplication());

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.submit(command()));

        assertEquals("该会员有待移线处理申请，暂不可再进行移线操作", error.getMessage());
        verify(applicationDao, never()).insert(any());
        verify(agentService, never()).switchLine(any());
    }

    @Test
    void authorizedManagerCanSeeRecordsCreatedAndHandledByOtherManagers() {
        DmsLineChangeApplication first = new DmsLineChangeApplication();
        first.setId(101L);
        first.setApplicantId(1L);
        first.setAuditorId(2L);
        DmsLineChangeApplication second = new DmsLineChangeApplication();
        second.setId(102L);
        second.setApplicantId(3L);
        second.setAuditorId(4L);
        when(applicationDao.selectList(null)).thenReturn(List.of(first, second));

        DmsAdminUser currentManager = new DmsAdminUser();
        currentManager.setId(99L);
        currentManager.setUsername("another-manager");
        AdminContext.set(currentManager);

        List<DmsLineChangeApplication> result = service.list(null);

        assertEquals(List.of(101L, 102L), result.stream().map(DmsLineChangeApplication::getId).toList());
        verify(applicationDao).selectList(null);
    }

    private AgentSwitchLineDTO command() {
        AgentSwitchLineDTO dto = new AgentSwitchLineDTO();
        dto.setAgentId(20L);
        dto.setNewParentAgentId(30L);
        dto.setReason("业务调整");
        return dto;
    }

    private DmsAgent agent(Long id, Long parentId) {
        DmsAgent agent = new DmsAgent();
        agent.setId(id);
        agent.setParentId(parentId);
        return agent;
    }
}
