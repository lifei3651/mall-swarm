package com.macro.mall.distribution.service;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dto.AdminMemberPasswordResetDTO;
import com.macro.mall.distribution.dto.AdminMemberPhoneUpdateDTO;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.AdminMemberSecurityServiceImpl;
import com.macro.mall.distribution.vo.AgentInfoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMemberSecurityServiceTest {

    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsShopMemberSessionDao sessionDao;
    @Mock private AgentService agentService;
    @Mock private OperationLogService operationLogService;

    @Test
    void changingPhoneUpdatesMemberAndAgentAndRevokesSessions() {
        DmsShopMember member = member(12L, 1200L, "13900000000", BCrypt.hashpw("old-password"));
        when(memberDao.selectById(12L)).thenReturn(member);
        when(memberDao.selectByAccount("13800000000")).thenReturn(null);
        when(memberDao.updatePhoneAndDefaults(12L, "13900000000", "13800000000")).thenReturn(1);
        AgentInfoVO agent = new AgentInfoVO();
        agent.setId(88L);
        agent.setAgentName("13900000000");
        when(agentService.getAgentByUserId(1200L)).thenReturn(agent);

        AdminMemberPhoneUpdateDTO dto = new AdminMemberPhoneUpdateDTO();
        dto.setPhone("13800000000");
        dto.setReason("客户完成身份核实后申请变更");

        service().updatePhone(12L, dto);

        ArgumentCaptor<AgentUpdateDTO> agentUpdate = ArgumentCaptor.forClass(AgentUpdateDTO.class);
        verify(agentService).updateAgentInfo(eq(88L), agentUpdate.capture());
        assertTrue("13800000000".equals(agentUpdate.getValue().getPhone()));
        assertTrue("13800000000".equals(agentUpdate.getValue().getAgentName()));
        verify(sessionDao).disableByMemberId(12L);
        verify(operationLogService).log(eq("MEMBER_SECURITY"), eq("PHONE_UPDATE"), eq("SHOP_MEMBER"),
                eq("12"), eq("手机号：139****0000"), eq("手机号：138****0000"), eq(dto.getReason()));
    }

    @Test
    void changingPhoneRejectsAnotherMembersLoginAccount() {
        DmsShopMember current = member(12L, 1200L, "13900000000", BCrypt.hashpw("old-password"));
        DmsShopMember conflict = member(13L, 1300L, "13700000000", BCrypt.hashpw("other-password"));
        when(memberDao.selectById(12L)).thenReturn(current);
        when(memberDao.selectByAccount("13800000000")).thenReturn(conflict);
        AdminMemberPhoneUpdateDTO dto = new AdminMemberPhoneUpdateDTO();
        dto.setPhone("13800000000");
        dto.setReason("客户申请变更");

        assertThrows(RuntimeException.class, () -> service().updatePhone(12L, dto));
        verify(memberDao, never()).updatePhoneAndDefaults(any(), any(), any());
        verify(sessionDao, never()).disableByMemberId(any());
    }

    @Test
    void resettingLoginPasswordStoresOnlyBcryptAndRevokesSessions() {
        String newPassword = "new-login-password";
        DmsShopMember member = member(12L, 1200L, "13900000000", BCrypt.hashpw("old-password"));
        when(memberDao.selectById(12L)).thenReturn(member);
        when(memberDao.updatePassword(eq(12L), any())).thenReturn(1);
        AdminMemberPasswordResetDTO dto = new AdminMemberPasswordResetDTO();
        dto.setNewPassword(newPassword);
        dto.setReason("客户完成身份核实后申请重置");

        service().resetLoginPassword(12L, dto);

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(memberDao).updatePassword(eq(12L), passwordHash.capture());
        assertFalse(passwordHash.getValue().contains(newPassword));
        assertTrue(BCrypt.checkpw(newPassword, passwordHash.getValue()));
        verify(sessionDao).disableByMemberId(12L);
        verify(operationLogService).log(eq("MEMBER_SECURITY"), eq("LOGIN_PASSWORD_RESET"),
                eq("SHOP_MEMBER"), eq("12"), eq(null),
                eq("登录密码已重置，全部旧会话已失效"), eq(dto.getReason()));
    }

    private AdminMemberSecurityServiceImpl service() {
        return new AdminMemberSecurityServiceImpl(memberDao, sessionDao, agentService, operationLogService);
    }

    private DmsShopMember member(Long id, Long userId, String phone, String passwordHash) {
        DmsShopMember member = new DmsShopMember();
        member.setId(id);
        member.setUserId(userId);
        member.setPhone(phone);
        member.setPasswordHash(passwordHash);
        member.setStatus(1);
        return member;
    }
}
