package com.macro.mall.distribution.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.vo.ShopAuthVO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiniProgramAccountAuthControllerTest {
    final ShopAuthService auth = mock(ShopAuthService.class);
    final WeChatMiniProgramProperties properties = new WeChatMiniProgramProperties();
    final MiniProgramAccountAuthController controller = new MiniProgramAccountAuthController(auth, properties);

    @Test void explicitCurrentConsentIsRequiredBeforeCredentialsAreProcessed() {
        assertThrows(RuntimeException.class, () -> controller.login(new MiniProgramAccountAuthController.Login(new ShopLoginDTO(), false, properties.getPrivacyConsentVersion())));
        assertThrows(RuntimeException.class, () -> controller.register(new MiniProgramAccountAuthController.Registration(new ShopRegisterDTO(), true, "old")));
        verifyNoInteractions(auth);
    }

    @Test void passwordAndSmsUseTheSamePublicAuthenticationWithoutChangingBrowserSerialization() throws Exception {
        for (String type : new String[]{"password", "sms"}) {
            ShopLoginDTO dto = new ShopLoginDTO(); dto.setLoginType(type);
            ShopAuthVO source = new ShopAuthVO(); source.setToken("test-session");
            when(auth.login(dto, "public")).thenReturn(source);
            var result = controller.login(new MiniProgramAccountAuthController.Login(dto, true, properties.getPrivacyConsentVersion())).getData();
            assertEquals("test-session", result.getAccessToken());
            verify(auth).login(dto, "public");
            assertFalse(new ObjectMapper().writeValueAsString(source).contains("test-session"));
        }
        verify(auth, never()).loginWechatMember(any());
    }

    @Test void registrationUsesExistingPublicInvitationTransactionAndPropagatesRejection() {
        ShopRegisterDTO dto = new ShopRegisterDTO(); dto.setInviteCode("ABCD1234");
        ShopAuthVO source = new ShopAuthVO(); source.setToken("test-session");
        when(auth.registerPublic(dto)).thenReturn(source);
        assertTrue(controller.register(new MiniProgramAccountAuthController.Registration(dto, true, properties.getPrivacyConsentVersion())).getData().isNewMember());
        verify(auth).registerPublic(dto);
        when(auth.registerPublic(dto)).thenThrow(new IllegalArgumentException("验证码无效"));
        assertThrows(IllegalArgumentException.class, () -> controller.register(new MiniProgramAccountAuthController.Registration(dto, true, properties.getPrivacyConsentVersion())));
    }
}
