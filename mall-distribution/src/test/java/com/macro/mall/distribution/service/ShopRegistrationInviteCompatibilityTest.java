package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
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
        when(memberDao.countForFoundingMember()).thenReturn(5L);
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
        assertEquals("new_user_123", memberCaptor.getValue().getNickname());
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
