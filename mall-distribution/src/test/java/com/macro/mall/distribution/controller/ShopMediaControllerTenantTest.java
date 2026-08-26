package com.macro.mall.distribution.controller;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopMediaStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopMediaControllerTenantTest {

    @Mock private ShopMediaStorageService mediaStorageService;
    @Mock private ShopAuthService authService;
    @Mock private ShopAfterSaleService afterSaleService;
    @InjectMocks private ShopMediaController controller;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void brandCultureUploadRejectsClientSelectedOtherTenantBeforeStorage() throws Exception {
        TenantContext.setTenantId(1L);
        MockMultipartFile file = image();

        assertThrows(ApiException.class, () -> controller.uploadBrandCulture(2L, "banner", file));

        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void brandCultureUploadStoresOnlyInCurrentTenantDirectory() throws Exception {
        TenantContext.setTenantId(1L);
        MockMultipartFile file = image();
        when(mediaStorageService.storeBrandCultureImage(1L, true, file))
                .thenReturn(new ShopMediaStorageService.StoredImage("safe.jpg", Path.of("safe.jpg"), "image/jpeg", 12L));

        var result = controller.uploadBrandCulture(1L, "banner", file);

        assertEquals("/api/shop/media/brand-culture/1/safe.jpg", result.getData().getUrl());
        verify(mediaStorageService).storeBrandCultureImage(1L, true, file);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "banner.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
