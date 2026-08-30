package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.vo.ShopAuthVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeChatMiniProgramAccountServiceTest {

    private DmsWechatMiniProgramIdentityDao identityDao;
    private DmsShopMemberDao memberDao;
    private ShopAuthService authService;
    private WeChatMiniProgramAccountService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(9L);
        identityDao = mock(DmsWechatMiniProgramIdentityDao.class);
        memberDao = mock(DmsShopMemberDao.class);
        authService = mock(ShopAuthService.class);
        WeChatMiniProgramProperties properties = new WeChatMiniProgramProperties();
        properties.setAppId("wx1234567890abcdef");
        service = new WeChatMiniProgramAccountService(identityDao, memberDao, authService, properties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void bindsVerifiedPhoneWithoutReturningWechatIdentity() {
        when(authService.loginOrRegisterWechat("13800138000", "ABCD1234"))
                .thenReturn(auth(21L, 210L));
        when(identityDao.insert(any())).thenReturn(1);

        WeChatMiniProgramAccountService.AccountLogin result = service.bind(
                "sensitive-openid", "sensitive-unionid", "13800138000", "ABCD1234", "PRIVACY_1");

        assertTrue(result.newMember());
        ArgumentCaptor<DmsWechatMiniProgramIdentity> saved =
                ArgumentCaptor.forClass(DmsWechatMiniProgramIdentity.class);
        verify(identityDao).insert(saved.capture());
        assertEquals(9L, saved.getValue().getTenantId());
        assertEquals(21L, saved.getValue().getMemberId());
        assertEquals("sensitive-openid", saved.getValue().getOpenId());
        assertNotEquals("sensitive-openid", saved.getValue().getOpenIdHash());
        assertEquals(64, saved.getValue().getOpenIdHash().length());
    }

    @Test
    void blocksBindingOnePhoneToAnotherWechatIdentityForSameCustomerApp() {
        DmsShopMember existing = new DmsShopMember();
        existing.setId(22L);
        when(memberDao.selectByPhone("13800138000")).thenReturn(existing);
        DmsWechatMiniProgramIdentity memberIdentity = new DmsWechatMiniProgramIdentity();
        memberIdentity.setOpenIdHash("different-open-id-hash");
        when(identityDao.selectByMember(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(22L)))
                .thenReturn(memberIdentity);

        assertThrows(ApiException.class, () -> service.bind(
                "new-openid", null, "13800138000", null, "PRIVACY_1"));
        verify(authService, never()).loginOrRegisterWechat(any(), any());
    }

    @Test
    void existingIdentityUsesBoundMemberAndRefreshesConsentAudit() {
        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setId(31L);
        identity.setMemberId(21L);
        when(authService.loginWechatMember(21L)).thenReturn(auth(21L, 210L));
        when(identityDao.updateLogin(identity)).thenReturn(1);

        WeChatMiniProgramAccountService.AccountLogin result =
                service.loginExisting(identity, "union-id", "PRIVACY_2");

        assertEquals(21L, result.auth().getMember().getId());
        assertEquals("PRIVACY_2", identity.getPrivacyConsentVersion());
        assertEquals("union-id", identity.getUnionId());
        verify(identityDao).updateLogin(identity);
    }

    private ShopAuthVO auth(Long memberId, Long userId) {
        DmsShopMember member = new DmsShopMember();
        member.setId(memberId);
        member.setUserId(userId);
        ShopAuthVO auth = new ShopAuthVO();
        auth.setToken("token");
        auth.setMember(member);
        return auth;
    }
}
