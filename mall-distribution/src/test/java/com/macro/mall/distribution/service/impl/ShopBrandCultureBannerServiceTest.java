package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopBannerDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsShopBanner;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.BrandCultureImagePolicy;
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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopBrandCultureBannerServiceTest {

    @Mock private DmsShopBannerDao bannerDao;
    @Mock private DmsTenantDao tenantDao;
    @Mock private ShopCatalogCacheService catalogCache;
    @Mock private BrandCultureImagePolicy brandCultureImagePolicy;
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

    @Test
    void updateLocksPersistedTenantBeforeImageAndPageValidation() {
        TenantContext.setTenantId(1L);
        String tenantTwoImage = "/api/shop/media/brand-culture/2/banner.jpg";
        DmsShopBanner existing = banner(8L, "BRAND_CULTURE");
        existing.setTenantId(1L);
        DmsShopBanner request = banner(null, "BRAND_CULTURE");
        request.setTenantId(2L);
        request.setTitle("品牌文化");
        request.setImageUrl(tenantTwoImage);
        request.setStatus(1);
        when(bannerDao.selectById(8L)).thenReturn(existing);
        when(brandCultureImagePolicy.validateBanner(1L, tenantTwoImage)).thenReturn(tenantTwoImage);
        when(tenantDao.selectById(any())).thenAnswer(invocation ->
                Long.valueOf(1L).equals(invocation.getArgument(0)) ? tenant(1L, 0) : tenant(2L, 1));

        assertThrows(ApiException.class, () -> shopService.updateBanner(8L, request));

        assertEquals(1L, request.getTenantId());
        verify(brandCultureImagePolicy).validateBanner(1L, tenantTwoImage);
        verify(tenantDao).selectById(1L);
        verify(tenantDao, never()).selectById(2L);
        verify(bannerDao, never()).update(any());
    }

    @Test
    void updateRejectsImageOwnedBySpoofedTenantEvenWhenBannerIsHidden() {
        TenantContext.setTenantId(1L);
        String tenantTwoImage = "/api/shop/media/brand-culture/2/banner.jpg";
        DmsShopBanner existing = banner(10L, "BRAND_CULTURE");
        existing.setTenantId(1L);
        DmsShopBanner request = banner(null, "BRAND_CULTURE");
        request.setTenantId(2L);
        request.setTitle("品牌文化");
        request.setImageUrl(tenantTwoImage);
        request.setStatus(0);
        when(bannerDao.selectById(10L)).thenReturn(existing);
        when(brandCultureImagePolicy.validateBanner(1L, tenantTwoImage))
                .thenThrow(new ApiException("品牌文化图片不属于当前客户"));

        assertThrows(ApiException.class, () -> shopService.updateBanner(10L, request));

        assertEquals(1L, request.getTenantId());
        verify(brandCultureImagePolicy).validateBanner(1L, tenantTwoImage);
        verify(tenantDao, never()).selectById(any());
        verify(bannerDao, never()).update(any());
    }

    @Test
    void createIgnoresClientTenantAndUsesCurrentTenantForImageAndPageGate() {
        TenantContext.setTenantId(1L);
        String tenantOneImage = "/api/shop/media/brand-culture/1/banner.jpg";
        DmsShopBanner request = banner(null, "BRAND_CULTURE");
        request.setTenantId(2L);
        request.setTitle("品牌文化");
        request.setImageUrl(tenantOneImage);
        request.setStatus(1);
        when(brandCultureImagePolicy.validateBanner(1L, tenantOneImage)).thenReturn(tenantOneImage);
        when(tenantDao.selectById(1L)).thenReturn(tenant(1L, 1));

        shopService.saveBanner(request);

        assertEquals(1L, request.getTenantId());
        verify(brandCultureImagePolicy).validateBanner(1L, tenantOneImage);
        verify(tenantDao).selectById(1L);
        verify(bannerDao).insert(request);
    }

    @Test
    void statusCannotCrossTenantEvenWhenTargetPageIsOpen() {
        TenantContext.setTenantId(1L);
        DmsShopBanner otherTenant = banner(9L, "BRAND_CULTURE");
        otherTenant.setTenantId(2L);
        when(bannerDao.selectById(9L)).thenReturn(otherTenant);

        assertThrows(ApiException.class, () -> shopService.updateBannerStatus(9L, 1));

        verify(tenantDao, never()).selectById(any());
        verify(bannerDao, never()).updateStatus(9L, 1);
    }

    private DmsTenant tenant(Long id, int brandCultureEnabled) {
        DmsTenant tenant = new DmsTenant();
        tenant.setId(id);
        tenant.setBrandCultureEnabled(brandCultureEnabled);
        return tenant;
    }

    private DmsShopBanner banner(Long id, String linkType) {
        DmsShopBanner banner = new DmsShopBanner();
        banner.setId(id);
        banner.setLinkType(linkType);
        return banner;
    }
}
