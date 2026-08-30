package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsTenant;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class ShopRegistrationInviteCompatibilityTest {

    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsShopMemberSessionDao sessionDao;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private AgentService agentService;
    @Mock private LoginCaptchaService loginCaptchaService;
    @Mock private SmsVerificationService smsVerificationService;
    @Mock private DmsTenantDao tenantDao;
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
    void teamRegistrationNeverCreatesAnAnonymousFoundingMember() {
        ShopRegisterDTO dto = validRegistration("15500000127", "team_user_1");
        dto.setInviteCode(null);

        ApiException error = assertThrows(ApiException.class, () -> authService.register(dto, "team"));

        assertEquals("请输入邀请码", error.getMessage());
        verify(memberDao, never()).insert(any(DmsShopMember.class));
    }

    @Test
    void publicRegistrationFromInviteLinkBindsDirectInviterInSameTransaction() {
        ShopRegisterDTO dto = validRegistration("15500000124", "public_user_1");
        DmsShopMember inviter = new DmsShopMember();
        inviter.setUserId(880088L);
        inviter.setStatus(1);
        when(memberDao.selectByInviteCode("INVITE01")).thenReturn(inviter);

        authService.registerPublic(dto);

        ArgumentCaptor<DmsShopMember> memberCaptor = ArgumentCaptor.forClass(DmsShopMember.class);
        verify(memberDao).insert(memberCaptor.capture());
        assertEquals(880088L, memberCaptor.getValue().getInviterId());
        assertEquals(1, memberCaptor.getValue().getTeamOptIn());
        verify(agentService, never()).getAgentByInviteCode(any());
    }

    @Test
    void publicRegistrationWithoutInviteLinkRemainsOrdinaryShoppingAccount() {
        ShopRegisterDTO dto = validRegistration("15500000125", "public_user_2");
        dto.setInviteCode(null);

        authService.registerPublic(dto);

        ArgumentCaptor<DmsShopMember> memberCaptor = ArgumentCaptor.forClass(DmsShopMember.class);
        verify(memberDao).insert(memberCaptor.capture());
        assertEquals(null, memberCaptor.getValue().getInviterId());
        assertEquals(0, memberCaptor.getValue().getTeamOptIn());
        verify(memberDao, never()).selectByInviteCode(any());
        verify(agentService, never()).getAgentByInviteCode(any());
    }

    @Test
    void registrationChecksImageAndSmsCodesOnlyAtFinalSubmission() {
        ShopRegisterDTO dto = validRegistration("15500000129", "public_user_4");
        dto.setInviteCode(null);

        authService.registerPublic(dto);

        var ordered = inOrder(loginCaptchaService, smsVerificationService);
        ordered.verify(loginCaptchaService).verify("shop", "captcha-id", "A1B2");
        ordered.verify(smsVerificationService).verifyAndConsume("15500000129", "123456", 1);
    }

    @Test
    void invitedPublicRegistrationCanOpenQualificationImmediatelyWhenCustomerChoosesIt() {
        ShopRegisterDTO dto = validRegistration("15500000126", "public_user_3");
        DmsShopMember inviter = new DmsShopMember();
        inviter.setUserId(880088L);
        inviter.setStatus(1);
        when(memberDao.selectByInviteCode("INVITE01")).thenReturn(inviter);
        DmsTenant tenant = new DmsTenant();
        tenant.setPromotionJoinMode("AUTO_ON_INVITE");
        when(tenantDao.selectById(1L)).thenReturn(tenant);
        when(tenantDao.selectByIdForUpdate(1L)).thenReturn(tenant);

        AtomicReference<DmsShopMember> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        }).when(memberDao).insert(any(DmsShopMember.class));
        when(memberDao.selectByUserId(any())).thenAnswer(invocation -> inserted.get());
        when(memberDao.selectByInviterId(any())).thenReturn(List.of());
        AgentInfoVO activated = new AgentInfoVO();
        activated.setId(99001L);
        activated.setUserId(99002L);
        activated.setAgentLevel(1);
        when(agentService.register(any(AgentRegisterDTO.class))).thenReturn(activated);

        authService.registerPublic(dto);

        ArgumentCaptor<AgentRegisterDTO> agentCaptor = ArgumentCaptor.forClass(AgentRegisterDTO.class);
        verify(agentService).register(agentCaptor.capture());
        assertEquals(2, agentCaptor.getValue().getSourceType());
        assertEquals("受邀注册后自动开通推广资格", agentCaptor.getValue().getReason());
    }

    @Test
    void ordinaryShoppingAccountCannotLoginToTeamH5AndBindLater() {
        String phone = "15500000128";
        DmsShopMember member = new DmsShopMember();
        member.setId(12L);
        member.setUserId(1200L);
        member.setPhone(phone);
        member.setStatus(1);
        member.setTeamOptIn(0);
        when(memberDao.selectByAccount(phone)).thenReturn(member);
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount(phone);
        dto.setLoginType("sms");
        dto.setSmsCode("123456");

        ApiException error = assertThrows(ApiException.class, () -> authService.login(dto, "team"));

        assertEquals("当前账号未加入团队服务，请联系平台管理员核对处理", error.getMessage());
        verify(memberDao, never()).updateLastLoginTime(12L);
        verify(sessionDao, never()).insert(any());
    }

    private ShopRegisterDTO validRegistration(String phone, String username) {
        ShopRegisterDTO dto = new ShopRegisterDTO();
        dto.setPhone(phone);
        dto.setUsername(username);
        dto.setNickname("新用户");
        dto.setPassword("secure888");
        dto.setSmsCode("123456");
        dto.setCaptchaId("captcha-id");
        dto.setCaptchaCode("A1B2");
        dto.setInviteCode("INVITE01");
        return dto;
    }
}
