package com.macro.mall.distribution.service;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.impl.ShopAuthServiceImpl;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopDirectInviterActivationTest {
    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsShopMemberSessionDao sessionDao;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private AgentService agentService;
    @Mock private LoginCaptchaService loginCaptchaService;
    @Mock private SmsVerificationService smsVerificationService;
    @Mock private DmsTenantDao tenantDao;
    @InjectMocks private ShopAuthServiceImpl authService;

    @Test
    void wechatInvitedActivationHasSameSourceAsH5InvitationNotAdminCreation() {
        when(tenantDao.selectByIdForUpdate(1L)).thenReturn(new DmsTenant());
        DmsShopMember invited = member(30L, 3003L, null, "受邀用户");
        when(memberDao.selectByUserId(3003L)).thenReturn(invited);
        when(agentService.register(any())).thenReturn(agent(33L, 3003L, null));
        when(memberDao.selectByInviterId(3003L)).thenReturn(List.of());
        authService.activateMember(3003L, 1, "微信扫码受邀注册后自动开通推广资格");
        ArgumentCaptor<AgentRegisterDTO> captor = ArgumentCaptor.forClass(AgentRegisterDTO.class);
        verify(agentService).register(captor.capture());
        assertEquals(AgentSourceTypeEnum.SCAN_CODE.getValue(), captor.getValue().getSourceType());
    }

    @Test
    void inactiveDirectInviterIsNeverSkippedToGrandparent() {
        when(tenantDao.selectByIdForUpdate(1L)).thenReturn(new DmsTenant());
        DmsShopMember c = member(30L, 3003L, 3002L, "C");
        when(memberDao.selectByUserId(3003L)).thenReturn(c);
        when(agentService.getAgentByUserId(3003L)).thenReturn(null);
        when(agentService.getAgentByUserId(3002L)).thenReturn(null); // B尚未完成首单
        when(agentService.register(any())).thenReturn(agent(33L, 3003L, null));
        when(memberDao.selectByInviterId(3003L)).thenReturn(List.of());

        authService.activateMember(3003L, 1, "完成首笔有效支付订单");

        ArgumentCaptor<AgentRegisterDTO> captor = ArgumentCaptor.forClass(AgentRegisterDTO.class);
        verify(agentService).register(captor.capture());
        assertNull(captor.getValue().getInviteCode(), "不能越过B把C挂到A名下");
        verify(agentService, never()).getAgentByUserId(3001L);
    }

    @Test
    void legacyMemberTableIdIsAcceptedByAdminActivation() {
        when(tenantDao.selectByIdForUpdate(1L)).thenReturn(new DmsTenant());
        DmsShopMember member = member(77L, 9007199254740992L, null, "后台会员");
        when(memberDao.selectByUserId(77L)).thenReturn(null);
        when(memberDao.selectById(77L)).thenReturn(member);
        when(agentService.getAgentByUserId(member.getUserId())).thenReturn(null);
        when(agentService.register(any())).thenReturn(agent(88L, member.getUserId(), null));
        when(memberDao.selectByInviterId(member.getUserId())).thenReturn(List.of());

        AgentInfoVO result = authService.activateMember(77L, 1, "后台将已有商城账号设为会员");

        assertEquals(member.getUserId(), result.getUserId());
        ArgumentCaptor<AgentRegisterDTO> captor = ArgumentCaptor.forClass(AgentRegisterDTO.class);
        verify(agentService).register(captor.capture());
        assertEquals(member.getUserId(), captor.getValue().getUserId());
    }

    @Test
    void adminCreatedMemberUsesOptionalInitialPassword() {
        AdminMemberCreateDTO dto = new AdminMemberCreateDTO();
        dto.setPhone("15500000006");
        dto.setUsername("member_account_6");
        dto.setPassword("Secure!8888");
        dto.setNickname("");

        authService.createAdminMember(dto);

        ArgumentCaptor<DmsShopMember> captor = ArgumentCaptor.forClass(DmsShopMember.class);
        verify(memberDao).insert(captor.capture());
        assertTrue(BCrypt.checkpw("Secure!8888", captor.getValue().getPasswordHash()));
        assertNotEquals("Secure!8888", captor.getValue().getPasswordHash());
        assertEquals("member_account_6", captor.getValue().getUsername());
        assertEquals("member_account_6", captor.getValue().getNickname());
    }

    @Test
    void passwordLoginAcceptsBothPhoneAndUsername() {
        DmsShopMember member = member(66L, 6600L, null, "登录会员");
        member.setPhone("15500000066");
        member.setUsername("login_user");
        member.setPasswordHash(BCrypt.hashpw("secure888"));
        member.setStatus(1);
        when(memberDao.selectByAccount("login_user")).thenReturn(member);
        when(memberDao.selectByAccount("15500000066")).thenReturn(member);

        ShopLoginDTO usernameLogin = new ShopLoginDTO();
        usernameLogin.setAccount(" login_user ");
        usernameLogin.setPassword("secure888");
        usernameLogin.setCaptchaId("captcha-1");
        usernameLogin.setCaptchaCode("1234");
        assertNotNull(authService.login(usernameLogin).getToken());

        ShopLoginDTO phoneLogin = new ShopLoginDTO();
        phoneLogin.setAccount("15500000066");
        phoneLogin.setPassword("secure888");
        phoneLogin.setCaptchaId("captcha-2");
        phoneLogin.setCaptchaCode("5678");
        assertNotNull(authService.login(phoneLogin).getToken());

        verify(memberDao).selectByAccount("login_user");
        verify(memberDao).selectByAccount("15500000066");
        verify(loginCaptchaService, times(2)).verify(eq("shop"), anyString(), anyString());
    }

    @Test
    void smsLoginOnlyAcceptsPhoneNumber() {
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount("login_user");
        dto.setLoginType("sms");
        dto.setSmsCode("123456");

        RuntimeException error = assertThrows(RuntimeException.class, () -> authService.login(dto));
        assertTrue(error.getMessage().contains("请输入正确的11位手机号"));
        verify(memberDao, never()).selectByAccount(anyString());
    }

    @Test
    void verifiedSmsLoginForUnknownPhonePromptsRegistration() {
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount("15500000088");
        dto.setLoginType("sms");
        dto.setSmsCode("123456");

        RuntimeException error = assertThrows(RuntimeException.class, () -> authService.login(dto));

        assertEquals("该手机号尚未注册，请先注册账号", error.getMessage());
        verify(smsVerificationService).verifyAndConsume("15500000088", "123456", 2);
        verify(memberDao).selectByAccount("15500000088");
    }

    @Test
    void invalidSmsCodeNeverRevealsWhetherPhoneIsRegistered() {
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount("15500000089");
        dto.setLoginType("sms");
        dto.setSmsCode("654321");
        doThrow(new IllegalArgumentException("验证码错误"))
                .when(smsVerificationService).verifyAndConsume("15500000089", "654321", 2);

        RuntimeException error = assertThrows(RuntimeException.class, () -> authService.login(dto));

        assertEquals("验证码错误", error.getMessage());
        assertNotEquals("该手机号尚未注册，请先注册账号", error.getMessage());
    }

    private DmsShopMember member(Long id, Long userId, Long inviterId, String nickname) {
        DmsShopMember member = new DmsShopMember();
        member.setId(id);
        member.setUserId(userId);
        member.setInviterId(inviterId);
        member.setNickname(nickname);
        member.setPhone("15500000000");
        return member;
    }

    private AgentInfoVO agent(Long id, Long userId, Long parentId) {
        AgentInfoVO agent = new AgentInfoVO();
        agent.setId(id);
        agent.setUserId(userId);
        agent.setParentId(parentId);
        agent.setAgentLevel(1);
        return agent;
    }
}
