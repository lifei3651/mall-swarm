package com.macro.mall.distribution.controller;

import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopMediaStorageService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopMemberAvatarControllerTest {
    @Test void otherMemberIdentifierNeverReachesPrivateStorage() throws Exception {
        ShopAuthService auth = mock(ShopAuthService.class);
        ShopMediaStorageService storage = mock(ShopMediaStorageService.class);
        DmsShopMember member = new DmsShopMember(); member.setId(12L);
        when(auth.requireMember("Bearer owner")).thenReturn(member);
        ShopMemberAvatarController controller = new ShopMemberAvatarController(auth, storage, mock(DmsShopMemberDao.class));
        assertEquals(404, controller.read("Bearer owner", 13L, "avatar.jpg").getStatusCode().value());
        verifyNoInteractions(storage);
        assertEquals(404, controller.read("Bearer owner", 12L, "avatar.jpg").getStatusCode().value());
        verify(storage).loadMemberAvatar(12L, "avatar.jpg");
    }
}
