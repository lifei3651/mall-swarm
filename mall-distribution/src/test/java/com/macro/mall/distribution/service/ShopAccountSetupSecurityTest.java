package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dto.ShopAccountSetupDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.ShopAuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopAccountSetupSecurityTest {
    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsShopMemberSessionDao sessionDao;
    @Mock private AgentService agentService;
    @Mock private LoginCaptchaService loginCaptchaService;
    @Mock private SmsVerificationService smsVerificationService;
    @InjectMocks private ShopAuthServiceImpl authService;

    @Test
    void existingLoginAccountCannotBeOverwrittenByAStolenSession() {
        DmsShopMember sessionMember = member(9L, "member_old", "15500000009");
        when(memberDao.selectByIdForUpdate(9L)).thenReturn(sessionMember);

        ApiException error = assertThrows(ApiException.class,
                () -> authService.setupAccount(sessionMember, setup("member_new")));

        assertEquals("登录账号已经设置，如需修改密码请使用账号安全功能", error.getMessage());
        verify(memberDao, never()).updateAccount(org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString());
    }

    @Test
    void legacyPhoneAccountCanInitializeOnceAndAllSessionsAreRevoked() {
        DmsShopMember legacy = member(10L, "15500000010", "15500000010");
        when(memberDao.selectByIdForUpdate(10L)).thenReturn(legacy);
        when(memberDao.updateAccount(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq("member_new"), anyString()))
                .thenReturn(1);
        DmsShopMember saved = member(10L, "member_new", "15500000010");
        when(memberDao.selectById(10L)).thenReturn(saved);

        authService.setupAccount(legacy, setup("member_new"));

        verify(sessionDao).disableByMemberId(10L);
    }

    private DmsShopMember member(Long id, String username, String phone) {
        DmsShopMember member = new DmsShopMember();
        member.setId(id);
        member.setUsername(username);
        member.setPhone(phone);
        member.setStatus(1);
        return member;
    }

    private ShopAccountSetupDTO setup(String username) {
        ShopAccountSetupDTO dto = new ShopAccountSetupDTO();
        dto.setUsername(username);
        dto.setPassword("Secure!8888");
        return dto;
    }
}
