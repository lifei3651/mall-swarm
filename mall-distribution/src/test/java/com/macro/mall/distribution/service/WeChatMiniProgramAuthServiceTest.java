package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dto.WeChatMiniProgramLoginDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.WeChatMiniProgramLoginVO;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeChatMiniProgramAuthServiceTest {

    private WeChatMiniProgramProperties properties;
    private WeChatMiniProgramGateway gateway;
    private WeChatMiniProgramAccountService accountService;
    private WeChatSubscriptionService subscriptionService;
    private WeChatMiniProgramAuthService service;

    @BeforeEach
    void setUp() {
        properties = new WeChatMiniProgramProperties();
        properties.setEnabled(true);
        properties.setPhoneAuthorizationEnabled(true);
        properties.setAppId("wx1234567890abcdef");
        properties.setAppSecret("customer-strong-secret");
        properties.setPrivacyConsentVersion("PRIVACY_2026_08");
        gateway = mock(WeChatMiniProgramGateway.class);
        accountService = mock(WeChatMiniProgramAccountService.class);
        subscriptionService = mock(WeChatSubscriptionService.class);
        service = new WeChatMiniProgramAuthService(properties, gateway, accountService, subscriptionService,
                new WeChatPayProperties());
    }

    @Test
    void returningMemberLogsInWithoutRequestingPhoneAgain() {
        WeChatMiniProgramLoginDTO dto = loginDto();
        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setMemberId(7L);
        when(gateway.exchangeLoginCode("login-code"))
                .thenReturn(new WeChatMiniProgramGateway.LoginIdentity("openid", "unionid"));
        when(accountService.find("openid")).thenReturn(identity);
        when(accountService.loginExisting(identity, "unionid", "PRIVACY_2026_08"))
                .thenReturn(accountLogin(false));

        WeChatMiniProgramLoginVO result = service.login(dto);

        assertFalse(result.isPhoneAuthorizationRequired());
        assertFalse(result.isNewMember());
        assertEquals("mall-session-token", result.getAccessToken());
        verify(gateway, never()).exchangePhoneCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unknownWechatAccountAsksForExplicitPhoneAuthorization() {
        WeChatMiniProgramLoginDTO dto = loginDto();
        when(gateway.exchangeLoginCode("login-code"))
                .thenReturn(new WeChatMiniProgramGateway.LoginIdentity("new-openid", null));

        WeChatMiniProgramLoginVO result = service.login(dto);

        assertTrue(result.isPhoneAuthorizationRequired());
        assertNull(result.getAccessToken());
        verify(gateway, never()).exchangePhoneCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void authorizedPhoneCreatesOrBindsAccountAndCarriesInviteCode() {
        WeChatMiniProgramLoginDTO dto = loginDto();
        dto.setPhoneCode("phone-code");
        dto.setInviteCode("abcd1234");
        when(gateway.exchangeLoginCode("login-code"))
                .thenReturn(new WeChatMiniProgramGateway.LoginIdentity("new-openid", "unionid"));
        when(gateway.exchangePhoneCode("phone-code"))
                .thenReturn(new WeChatMiniProgramGateway.PhoneNumber("13800138000", "86"));
        when(accountService.bind("new-openid", "unionid", "13800138000", "ABCD1234", "PRIVACY_2026_08"))
                .thenReturn(accountLogin(true));

        WeChatMiniProgramLoginVO result = service.login(dto);

        assertTrue(result.isNewMember());
        assertEquals("mall-session-token", result.getAccessToken());
        verify(accountService).bind("new-openid", "unionid", "13800138000", "ABCD1234",
                "PRIVACY_2026_08");
    }

    @Test
    void rejectsStalePrivacyConsentBeforeCallingWechat() {
        WeChatMiniProgramLoginDTO dto = loginDto();
        dto.setPrivacyConsentVersion("OLD_PRIVACY");

        ApiException error = assertThrows(ApiException.class, () -> service.login(dto));

        assertTrue(error.getMessage().contains("隐私政策已更新"));
        verify(gateway, never()).exchangeLoginCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void runtimeOnlyAdvertisesSubscriptionWhenAllGatesAreReady() {
        var runtime = service.runtime();

        assertTrue(runtime.isEnabled());
        assertTrue(runtime.isPhoneAuthorizationEnabled());
        assertFalse(runtime.isPaymentEnabled());
        assertFalse(runtime.isSubscribeMessageEnabled());

        when(subscriptionService.ready()).thenReturn(true);
        assertTrue(service.runtime().isSubscribeMessageEnabled());
    }

    @Test
    void rejectsNonMainlandPhoneNumbers() {
        WeChatMiniProgramLoginDTO dto = loginDto();
        dto.setPhoneCode("phone-code");
        when(gateway.exchangeLoginCode("login-code"))
                .thenReturn(new WeChatMiniProgramGateway.LoginIdentity("openid", null));
        when(gateway.exchangePhoneCode("phone-code"))
                .thenReturn(new WeChatMiniProgramGateway.PhoneNumber("2025550100", "1"));

        assertThrows(ApiException.class, () -> service.login(dto));
        verify(accountService, never()).bind(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private WeChatMiniProgramLoginDTO loginDto() {
        WeChatMiniProgramLoginDTO dto = new WeChatMiniProgramLoginDTO();
        dto.setLoginCode("login-code");
        dto.setPrivacyAgreed(true);
        dto.setPrivacyConsentVersion("PRIVACY_2026_08");
        return dto;
    }

    private WeChatMiniProgramAccountService.AccountLogin accountLogin(boolean newMember) {
        DmsShopMember member = new DmsShopMember();
        member.setId(7L);
        member.setUserId(70L);
        ShopAuthVO auth = new ShopAuthVO();
        auth.setToken("mall-session-token");
        auth.setExpireTime(LocalDateTime.now().plusDays(7));
        auth.setMember(member);
        return new WeChatMiniProgramAccountService.AccountLogin(auth, newMember);
    }
}
