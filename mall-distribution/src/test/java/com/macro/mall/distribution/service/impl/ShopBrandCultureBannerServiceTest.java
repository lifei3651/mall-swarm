package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopBannerDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsShopBanner;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.ShopCatalogCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopBrandCultureBannerServiceTest {

    @Mock private DmsShopBannerDao bannerDao;
    @Mock private DmsTenantDao tenantDao;
    @Mock private ShopCatalogCacheService catalogCache;
    @InjectMocks private ShopServiceImpl shopService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void closingPageFiltersCultureBannerWithoutDeletingItsConfiguration() {
        DmsShopBanner culture = banner(1L, "BRAND_CULTURE");
        DmsShopBanner product = banner(2L, "PRODUCT");

        List<DmsShopBanner> hidden = ShopServiceImpl.filterPublicBanners(List.of(culture, product), false);
        List<DmsShopBanner> visible = ShopServiceImpl.filterPublicBanners(List.of(culture, product), true);

        assertEquals(List.of(product), hidden);
        assertEquals(List.of(culture, product), visible);
    }

    @Test
    void cannotEnableCultureBannerWhilePageIsClosedButCanStillDisableIt() {
        TenantContext.setTenantId(1L);
        DmsShopBanner culture = banner(7L, "BRAND_CULTURE");
        culture.setTenantId(1L);
        DmsTenant tenant = new DmsTenant();
        tenant.setId(1L);
        tenant.setBrandCultureEnabled(0);
        when(bannerDao.selectById(7L)).thenReturn(culture);
        when(tenantDao.selectById(1L)).thenReturn(tenant);

        assertThrows(ApiException.class, () -> shopService.updateBannerStatus(7L, 1));
        verify(bannerDao, never()).updateStatus(7L, 1);

        when(bannerDao.updateStatus(7L, 0)).thenReturn(1);
        assertTrue(shopService.updateBannerStatus(7L, 0));
        verify(bannerDao).updateStatus(7L, 0);
    }

    private DmsShopBanner banner(Long id, String linkType) {
        DmsShopBanner banner = new DmsShopBanner();
        banner.setId(id);
        banner.setLinkType(linkType);
        return banner;
    }
}
