package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.sms.SmsBusinessType;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dto.ShopNicknameUpdateDTO;
import com.macro.mall.distribution.dto.ShopPhoneUpdateDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.ShopAuthServiceImpl;
import com.macro.mall.distribution.vo.ShopAccountIdentityVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopAccountSettingsServiceTest {

    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsShopMemberSessionDao sessionDao;
    @Mock private AgentService agentService;
    @Mock private LoginCaptchaService loginCaptchaService;
    @Mock private SmsVerificationService smsVerificationService;
    @Mock private DmsTenantDao tenantDao;
    @Mock private MemberMessageService memberMessageService;

    @Test
    void phoneLoginIsAnExistingShopAccountAndOrdinaryRegistrationHasNoInviter() {
        DmsShopMember member = member();
        member.setUsername(member.getPhone());
        when(memberDao.selectById(12L)).thenReturn(member);

        ShopAccountIdentityVO identity = service().accountIdentity(member);

        assertEquals("PHONE", identity.getAccountMode());
        assertEquals("手机号账号", identity.getAccountDisplay());
        assertTrue(identity.getCanSetupLoginAccount());
        assertEquals("NONE", identity.getInviterStatus());
        verify(memberDao, never()).selectByUserId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void identityReturnsOnlyInviterPublicNicknameWithoutRebindingRelationship() {
        DmsShopMember member = member();
        member.setInviterId(3300L);
        DmsShopMember inviter = new DmsShopMember();
        inviter.setId(33L);
        inviter.setUserId(3300L);
        inviter.setPhone("13899998888");
        inviter.setUsername("private_login");
        inviter.setNickname("直属推荐人");
        inviter.setStatus(1);
        inviter.setSystemAccount(0);
        when(memberDao.selectById(12L)).thenReturn(member);
        when(memberDao.selectByUserId(3300L)).thenReturn(inviter);

        ShopAccountIdentityVO identity = service().accountIdentity(member);

        assertEquals("CUSTOM", identity.getAccountMode());
        assertEquals("member_12", identity.getAccountDisplay());
        assertFalse(identity.getCanSetupLoginAccount());
        assertEquals("BOUND", identity.getInviterStatus());
        assertEquals("直属推荐人", identity.getInviterName());
        verify(memberDao, never()).updateInviterId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void danglingInviterRelationshipIsReportedForManualVerification() {
        DmsShopMember member = member();
        member.setInviterId(9900L);
        when(memberDao.selectById(12L)).thenReturn(member);
        when(memberDao.selectByUserId(9900L)).thenReturn(null);

        ShopAccountIdentityVO identity = service().accountIdentity(member);

        assertEquals("INVALID", identity.getInviterStatus());
        assertEquals(null, identity.getInviterName());
        verify(memberDao, never()).updateInviterId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nicknameSupportsCommonChineseDisplayNamesAndRejectsEmoji() {
        DmsShopMember member = member();
        DmsShopMember updatedMember = member();
        updatedMember.setNickname("灵启 小李");
        when(memberDao.selectById(12L)).thenReturn(member, updatedMember);
        when(memberDao.updateNickname(12L, "灵启 小李")).thenReturn(1);

        ShopNicknameUpdateDTO valid = new ShopNicknameUpdateDTO();
        valid.setNickname("  灵启  小李  ");
        DmsShopMember updated = service().updateNickname(member, valid);
        assertEquals("灵启 小李", updated.getNickname());
        verify(memberDao).updateNickname(12L, "灵启 小李");

        ShopNicknameUpdateDTO invalid = new ShopNicknameUpdateDTO();
        invalid.setNickname("小李🙂");
        assertThrows(ApiException.class, () -> service().updateNickname(member, invalid));
    }

    @Test
    void memberPhoneChangeRequiresBothSmsCodesAndRevokesSessions() {
        DmsShopMember member = member();
        when(memberDao.selectById(12L)).thenReturn(member);
        when(memberDao.selectByAccount("13800000000")).thenReturn(null);
        when(memberDao.updatePhoneAndDefaults(12L, "13900000000", "13800000000")).thenReturn(1);

        ShopPhoneUpdateDTO dto = new ShopPhoneUpdateDTO();
        dto.setCurrentPhoneSmsCode("123456");
        dto.setNewPhone("13800000000");
        dto.setNewPhoneSmsCode("654321");

        service().updatePhone(member, dto);

        verify(smsVerificationService).verifyAndConsume("13800000000", "654321", SmsBusinessType.CHANGE_PHONE_NEW);
        verify(smsVerificationService).verifyAndConsume("13900000000", "123456", SmsBusinessType.CHANGE_PHONE_CURRENT);
        verify(memberDao).updatePhoneAndDefaults(12L, "13900000000", "13800000000");
        verify(sessionDao).disableByMemberId(12L);
        ArgumentCaptor<MemberMessageEvent> message = ArgumentCaptor.forClass(MemberMessageEvent.class);
        verify(memberMessageService).publish(message.capture());
        assertEquals("PHONE_CHANGED", message.getValue().eventType());
        assertEquals("ACCOUNT_SECURITY", message.getValue().category());
        assertEquals(1200L, message.getValue().userId());
    }

    @Test
    void memberPhoneChangeRejectsExistingAccountBeforeConsumingSms() {
        DmsShopMember member = member();
        DmsShopMember conflict = new DmsShopMember();
        conflict.setId(99L);
        when(memberDao.selectById(12L)).thenReturn(member);
        when(memberDao.selectByAccount("13800000000")).thenReturn(conflict);
        ShopPhoneUpdateDTO dto = new ShopPhoneUpdateDTO();
        dto.setCurrentPhoneSmsCode("123456");
        dto.setNewPhone("13800000000");
        dto.setNewPhoneSmsCode("654321");

        assertThrows(ApiException.class, () -> service().updatePhone(member, dto));
        verify(smsVerificationService, never()).verifyAndConsume(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private ShopAuthServiceImpl service() {
        return new ShopAuthServiceImpl(memberDao, sessionDao, agentService, loginCaptchaService, smsVerificationService,
                tenantDao, memberMessageService);
    }

    private DmsShopMember member() {
        DmsShopMember member = new DmsShopMember();
        member.setId(12L);
        member.setUserId(1200L);
        member.setPhone("13900000000");
        member.setUsername("member_12");
        member.setNickname("测试会员");
        member.setStatus(1);
        return member;
    }
}
