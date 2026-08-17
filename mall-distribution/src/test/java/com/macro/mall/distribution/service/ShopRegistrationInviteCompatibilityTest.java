package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.dto.ShopInviteBindDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.ShopAuthServiceImpl;
import com.macro.mall.distribution.vo.AgentInfoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class ShopRegistrationInviteCompatibilityTest {

    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsShopMemberSessionDao sessionDao;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private AgentService agentService;
    @Mock private LoginCaptchaService loginCaptchaService;
    @Mock private SmsVerificationService smsVerificationService;
    @InjectMocks private ShopAuthServiceImpl authService;

    @Test
    void registrationRejectsMissingRequiredUsernameBeforeWritingMember() {
        ShopRegisterDTO dto = new ShopRegisterDTO();
        dto.setPhone("15500000123");
        dto.setPassword("secure888");

        ApiException error = assertThrows(ApiException.class, () -> authService.register(dto));

        assertEquals("请输入登录账号", error.getMessage());
    }

    @Test
    void registrationExplainsThatPhoneIsAlreadyRegistered() {
        String phone = "15500000123";
        DmsShopMember existing = new DmsShopMember();
        existing.setPhone(phone);
        when(memberDao.selectByPhone(phone)).thenReturn(existing);

        ShopRegisterDTO dto = validRegistration(phone, "new_user_123");
        ApiException error = assertThrows(ApiException.class, () -> authService.register(dto));

        assertEquals("该手机号已注册，请直接登录或使用其他手机号", error.getMessage());
        verify(memberDao, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrationExplainsThatUsernameIsAlreadyUsed() {
        String phone = "15500000123";
        when(memberDao.selectByPhone(phone)).thenReturn(null);
        when(memberDao.selectByUsername(phone)).thenReturn(null);
        when(memberDao.selectByAccount("new_user_123")).thenReturn(new DmsShopMember());

        ShopRegisterDTO dto = validRegistration(phone, "new_user_123");
        ApiException error = assertThrows(ApiException.class, () -> authService.register(dto));

        assertEquals("该登录账号已被使用，请更换登录账号", error.getMessage());
        verify(memberDao, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrationRejectsChineseLoginAccountBeforeWritingMember() {
        ShopRegisterDTO dto = validRegistration("15500000123", "蜗牛账号");

        ApiException error = assertThrows(ApiException.class, () -> authService.register(dto));

        assertEquals("登录账号必须以英文字母开头", error.getMessage());
        verify(memberDao, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrationRejectsSpecialCharactersInLoginAccountBeforeWritingMember() {
        ShopRegisterDTO dto = validRegistration("15500000123", "user@123");

        ApiException error = assertThrows(ApiException.class, () -> authService.register(dto));

        assertEquals("登录账号仅支持英文字母、数字和下划线", error.getMessage());
        verify(memberDao, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrationAcceptsLegacyAgentInviteCodeAndBindsDirectInviter() {
        String phone = "15500000123";
        when(memberDao.countForFoundingTeamMember()).thenReturn(5L);
        when(memberDao.selectByInviteCode("OLDLINK1")).thenReturn(null);

        AgentInfoVO legacyAgent = new AgentInfoVO();
        legacyAgent.setUserId(880088L);
        legacyAgent.setStatus(1);
        when(agentService.getAgentByInviteCode("OLDLINK1")).thenReturn(legacyAgent);

        DmsShopMember inviter = new DmsShopMember();
        inviter.setUserId(880088L);
        inviter.setStatus(1);
        when(memberDao.selectByUserId(880088L)).thenReturn(inviter);

        ShopRegisterDTO dto = new ShopRegisterDTO();
        dto.setPhone(phone);
        dto.setUsername("new_user_123");
        dto.setNickname("该字段不再用于注册昵称");
        dto.setPassword("secure888");
        dto.setSmsCode("123456");
        dto.setInviteCode(" oldlink1 ");

        authService.register(dto);

        ArgumentCaptor<DmsShopMember> memberCaptor = ArgumentCaptor.forClass(DmsShopMember.class);
        verify(memberDao).insert(memberCaptor.capture());
        assertEquals(880088L, memberCaptor.getValue().getInviterId());
        assertEquals(1, memberCaptor.getValue().getTeamOptIn());
        assertEquals("new_user_123", memberCaptor.getValue().getNickname());
    }

    @Test
    void publicRegistrationNeverCreatesTeamRelationshipEvenWhenInviteCodeIsForged() {
        ShopRegisterDTO dto = validRegistration("15500000124", "public_user_1");
        dto.setInviteCode("OLDLINK1");

        authService.registerPublic(dto);

        ArgumentCaptor<DmsShopMember> memberCaptor = ArgumentCaptor.forClass(DmsShopMember.class);
        verify(memberDao).insert(memberCaptor.capture());
        assertEquals(null, memberCaptor.getValue().getInviterId());
        assertEquals(0, memberCaptor.getValue().getTeamOptIn());
        verify(memberDao, never()).countForFoundingTeamMember();
        verify(memberDao, never()).selectByInviteCode(any());
        verify(agentService, never()).getAgentByInviteCode(any());
    }

    @Test
    void teamH5CanBindAnUnboundPublicAccountExactlyOnceAndMoveActiveAgent() {
        DmsShopMember member = new DmsShopMember();
        member.setId(12L); member.setUserId(1200L); member.setStatus(1);
        DmsShopMember inviter = new DmsShopMember();
        inviter.setId(13L); inviter.setUserId(1300L); inviter.setStatus(1);
        when(memberDao.selectByIdForUpdate(12L)).thenReturn(member);
        when(memberDao.selectByInviteCode("INVITE01")).thenReturn(inviter);
        when(memberDao.bindInviterIdIfAbsent(12L, 1300L)).thenReturn(1);
        when(memberDao.selectById(12L)).thenReturn(member);
        AgentInfoVO currentAgent = new AgentInfoVO();
        currentAgent.setId(21L); currentAgent.setUserId(1200L);
        AgentInfoVO inviterAgent = new AgentInfoVO();
        inviterAgent.setId(22L); inviterAgent.setUserId(1300L);
        when(agentService.getAgentByUserId(1200L)).thenReturn(currentAgent);
        when(agentService.getAgentByUserId(1300L)).thenReturn(inviterAgent);

        ShopInviteBindDTO dto = new ShopInviteBindDTO();
        dto.setInviteCode(" invite01 ");
        authService.bindInviter(member, dto);

        ArgumentCaptor<AgentSwitchLineDTO> lineCaptor = ArgumentCaptor.forClass(AgentSwitchLineDTO.class);
        verify(agentService).switchLine(lineCaptor.capture());
        assertEquals(21L, lineCaptor.getValue().getAgentId());
        assertEquals(22L, lineCaptor.getValue().getNewParentAgentId());
        assertEquals("公开商城账号首次进入团队H5绑定直属邀请关系", lineCaptor.getValue().getReason());
    }

    private ShopRegisterDTO validRegistration(String phone, String username) {
        ShopRegisterDTO dto = new ShopRegisterDTO();
        dto.setPhone(phone);
        dto.setUsername(username);
        dto.setNickname("新用户");
        dto.setPassword("secure888");
        dto.setSmsCode("123456");
        dto.setInviteCode("INVITE01");
        return dto;
    }
}
