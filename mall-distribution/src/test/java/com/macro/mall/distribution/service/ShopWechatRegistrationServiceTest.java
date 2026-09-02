package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopMemberSession;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.service.impl.ShopAuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShopWechatRegistrationServiceTest {

    @Test
    void verifiedWechatPhoneRegistersAndBindsScannedInviterInOneTransaction() {
        DmsShopMemberDao memberDao = mock(DmsShopMemberDao.class);
        DmsShopMemberSessionDao sessionDao = mock(DmsShopMemberSessionDao.class);
        AgentService agentService = mock(AgentService.class);
        LoginCaptchaService captchaService = mock(LoginCaptchaService.class);
        SmsVerificationService smsService = mock(SmsVerificationService.class);
        DmsTenantDao tenantDao = mock(DmsTenantDao.class);

        DmsShopMember inviter = new DmsShopMember();
        inviter.setId(10L);
        inviter.setUserId(1000L);
        inviter.setStatus(1);
        inviter.setSystemAccount(0);
        when(memberDao.selectByInviteCode("ABCD1234")).thenReturn(inviter);
        AgentInfoVO activeInviter = new AgentInfoVO();
        activeInviter.setUserId(1000L);
        activeInviter.setStatus(1);
        activeInviter.setAgentLevel(1);
        when(agentService.getAgentByUserId(1000L)).thenReturn(activeInviter);
        when(memberDao.insert(any())).thenAnswer(invocation -> {
            DmsShopMember member = invocation.getArgument(0);
            member.setId(20L);
            return 1;
        });
        DmsTenant tenant = new DmsTenant();
        tenant.setPromotionJoinMode("DISABLED");
        when(tenantDao.selectById(1L)).thenReturn(tenant);

        ShopAuthServiceImpl service = new ShopAuthServiceImpl(memberDao, sessionDao, agentService,
                captchaService, smsService, tenantDao, mock(MemberMessageService.class));

        var result = service.loginOrRegisterWechat("13800138000", "abcd1234");

        ArgumentCaptor<DmsShopMember> created = ArgumentCaptor.forClass(DmsShopMember.class);
        verify(memberDao).insert(created.capture());
        assertEquals("13800138000", created.getValue().getPhone());
        assertEquals(1000L, created.getValue().getInviterId());
        assertEquals(1, created.getValue().getTeamOptIn());
        assertNotNull(result.getToken());

        ArgumentCaptor<DmsShopMemberSession> session = ArgumentCaptor.forClass(DmsShopMemberSession.class);
        verify(sessionDao).insert(session.capture());
        assertEquals("mini-program", session.getValue().getSurface());
        verifyNoInteractions(smsService, captchaService);
        verify(agentService, never()).register(any());
    }
}
