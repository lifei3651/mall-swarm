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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                tenantDao);
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
