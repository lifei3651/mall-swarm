package com.macro.mall.distribution.controller;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.WeChatMiniProgramAuthService;
import com.macro.mall.distribution.service.WeChatMiniProgramMemberService;
import com.macro.mall.distribution.service.WeChatSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeChatMiniProgramCapabilitiesControllerTest {
    private final ShopAuthService auth = mock(ShopAuthService.class);
    private final WeChatMiniProgramMemberService members = mock(WeChatMiniProgramMemberService.class);
    private final WeChatMiniProgramController controller = new WeChatMiniProgramController(
            mock(WeChatMiniProgramAuthService.class), auth, mock(WeChatSubscriptionService.class), members);

    @Test void unauthenticatedCallCannotQueryAnyMemberCapabilities() {
        when(auth.requireMember(null)).thenThrow(new IllegalArgumentException("unauthenticated"));
        assertThrows(IllegalArgumentException.class, () -> controller.capabilities(null, new MockHttpServletResponse()));
        verifyNoInteractions(members);
    }

    @Test void capabilitiesAreOnlyForAuthenticatedMemberAndNeverPubliclyCached() {
        var member = new DmsShopMember();
        member.setId(42L);
        when(auth.requireMember("Bearer mock-session")).thenReturn(member);
        var rights = new WeChatMiniProgramMemberService.Capabilities(false, false, null, true, true);
        when(members.capabilities(member)).thenReturn(rights);
        var response = new MockHttpServletResponse();
        assertSame(rights, controller.capabilities("Bearer mock-session", response).getData());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        verify(members).capabilities(member);
    }
}
