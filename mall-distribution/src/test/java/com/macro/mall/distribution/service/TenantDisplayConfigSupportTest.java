package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.service.impl.TenantDisplayConfigSupport;
import org.junit.jupiter.api.Test;
import com.macro.mall.distribution.vo.BrandCultureImageRefVO;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantDisplayConfigSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TenantDisplayConfigSupport support = new TenantDisplayConfigSupport(objectMapper);

    @Test
    void legacyTenantKeepsCurrentCategoryLayoutByDefault() {
        DmsTenantDisplayConfig config = support.prepareForRead(null, 1L);

        assertEquals(1L, config.getTenantId());
        assertEquals("standard", config.getLayoutTemplate());
        assertEquals(1, config.getShowHomeCategories());
        assertEquals(1, config.getShowBottomCategoryNav());
        assertEquals(1, config.getLiveSquareEnabled());
        assertEquals(1, config.getNewArrivalsEnabled());
        assertEquals(30, config.getNewArrivalWindowDays());
        assertEquals(1, config.getShowPv());
        assertEquals("directory", config.getCategoryGuideTemplate());
        assertEquals(1, config.getCategoryGuidePrimaryCategoriesEnabled());
        assertEquals(1, config.getCategoryGuideHeroCategoriesEnabled());
        assertEquals(1, config.getCategoryGuideScenariosEnabled());
        assertEquals(1, config.getProductDetailEnabled());
        assertEquals(1, config.getLegalComplianceEnabled());
    }

    @Test
    void readsLayoutFieldsFromExistingExtraConfig() {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(2L);
        config.setExtraConfigJson("{\"layoutTemplate\":\"campaign-feed\",\"showHomeCategories\":false,\"showBottomCategoryNav\":0,\"liveSquareEnabled\":false,\"newArrivalsEnabled\":false,\"newArrivalWindowDays\":0}");

        support.prepareForRead(config, 2L);

        assertEquals("campaign-feed", config.getLayoutTemplate());
        assertEquals(0, config.getShowHomeCategories());
        assertEquals(0, config.getShowBottomCategoryNav());
        assertEquals(0, config.getLiveSquareEnabled());
        assertEquals(0, config.getNewArrivalsEnabled());
        assertEquals(0, config.getNewArrivalWindowDays());
    }

    @Test
    void brandCultureImagesRoundTripInOrderAndLegacyStringEntriesRemainReadable() throws Exception {
        DmsTenantDisplayConfig legacy = new DmsTenantDisplayConfig();
        legacy.setTenantId(2L);
        legacy.setExtraConfigJson("{\"brandCultureDetailImages\":[\"/api/shop/media/brand-culture/2/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg\",{\"url\":\"/api/shop/media/brand-culture/2/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png\",\"size\":123}]} ");

        support.prepareForRead(legacy, 2L);
        assertEquals(List.of(
                "/api/shop/media/brand-culture/2/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg",
                "/api/shop/media/brand-culture/2/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png"),
                legacy.getBrandCultureDetailImages().stream().map(BrandCultureImageRefVO::getUrl).toList());

        java.util.Collections.reverse(legacy.getBrandCultureDetailImages());
        support.prepareForSave(legacy);
        JsonNode saved = objectMapper.readTree(legacy.getExtraConfigJson()).path("brandCultureDetailImages");
        assertEquals("/api/shop/media/brand-culture/2/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png", saved.get(0).path("url").asText());
        assertEquals(123L, saved.get(0).path("size").asLong());
    }

    @Test
    void brandCultureImageCountIsBoundedOnSave() {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(2L);
        config.setBrandCultureDetailImages(java.util.Collections.nCopies(11,
                new BrandCultureImageRefVO("/api/shop/media/brand-culture/2/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg", 1L)));
        assertThrows(ApiException.class, () -> support.prepareForSave(config));
    }

    @Test
    void savesFriendlyFieldsAndPreservesUnknownExtensions() throws Exception {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(3L);
        config.setLayoutTemplate("category-focus");
        config.setShowHomeCategories(1);
        config.setShowBottomCategoryNav(1);
        config.setLiveSquareEnabled(0);
        config.setNewArrivalsEnabled(0);
        config.setNewArrivalWindowDays(180);
        config.setExtraConfigJson("{\"futureSetting\":\"keep-me\",\"requiredCapabilities\":{\"futureExperimental\":0}}");

        support.prepareForSave(config);
        JsonNode json = objectMapper.readTree(config.getExtraConfigJson());

        assertEquals("category-focus", json.get("layoutTemplate").asText());
        assertEquals(1, json.get("showHomeCategories").asInt());
        assertEquals(1, json.get("showBottomCategoryNav").asInt());
        assertEquals(0, json.get("liveSquareEnabled").asInt());
        assertEquals(0, json.get("newArrivalsEnabled").asInt());
        assertEquals(180, json.get("newArrivalWindowDays").asInt());
        assertEquals("keep-me", json.get("futureSetting").asText());
        assertEquals(0, json.path("requiredCapabilities").path("futureExperimental").asInt());
    }

    @Test
    void invalidLegacyJsonFallsBackSafelyAndIsRepairedOnSave() throws Exception {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(4L);
        config.setLayoutTemplate("unknown-template");
        config.setShowHomeCategories(9);
        config.setShowBottomCategoryNav(null);
        config.setExtraConfigJson("not-json");

        support.prepareForSave(config);

        assertEquals("standard", config.getLayoutTemplate());
        assertEquals(0, config.getShowHomeCategories());
        assertEquals(1, config.getShowBottomCategoryNav());
        assertTrue(objectMapper.readTree(config.getExtraConfigJson()).isObject());
    }

    @Test
    void categoryGuideTemplateAndAllThreeModuleGroupsRoundTrip() throws Exception {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(8L);
        config.setLayoutTemplate("category-focus");
        config.setCategoryGuideTemplate("scenario");
        config.setCategoryGuidePrimaryCategoriesEnabled(0);
        config.setCategoryGuideSubcategoriesEnabled(1);
        config.setCategoryGuideHotProductsEnabled(0);
        config.setCategoryGuideHeroCategoriesEnabled(1);
        config.setCategoryGuideShelvesEnabled(0);
        config.setCategoryGuideRecommendedProductsEnabled(1);
        config.setCategoryGuideScenariosEnabled(1);
        config.setCategoryGuideQuickEntriesEnabled(0);
        config.setCategoryGuidePopularProductsEnabled(1);

        support.prepareForSave(config);
        DmsTenantDisplayConfig restored = new DmsTenantDisplayConfig();
        restored.setTenantId(8L);
        restored.setExtraConfigJson(config.getExtraConfigJson());
        support.prepareForRead(restored, 8L);

        assertEquals("scenario", restored.getCategoryGuideTemplate());
        assertEquals(0, restored.getCategoryGuidePrimaryCategoriesEnabled());
        assertEquals(1, restored.getCategoryGuideSubcategoriesEnabled());
        assertEquals(0, restored.getCategoryGuideHotProductsEnabled());
        assertEquals(1, restored.getCategoryGuideHeroCategoriesEnabled());
        assertEquals(0, restored.getCategoryGuideShelvesEnabled());
        assertEquals(1, restored.getCategoryGuideRecommendedProductsEnabled());
        assertEquals(1, restored.getCategoryGuideScenariosEnabled());
        assertEquals(0, restored.getCategoryGuideQuickEntriesEnabled());
        assertEquals(1, restored.getCategoryGuidePopularProductsEnabled());
        assertEquals(1, objectMapper.readTree(config.getExtraConfigJson())
                .path("requiredCapabilities").path("checkout").asInt());
    }

    @Test
    void parentLayoutSwitchPreservesInactiveCategoryGuideValues() {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(9L);
        config.setLayoutTemplate("standard");
        config.setCategoryGuideTemplate("showcase");
        config.setCategoryGuideShelvesEnabled(0);

        support.prepareForSave(config);
        config.setLayoutTemplate("category-focus");
        support.prepareForSave(config);

        assertEquals("showcase", config.getCategoryGuideTemplate());
        assertEquals(0, config.getCategoryGuideShelvesEnabled());
    }

    @Test
    void activeDirectoryRejectsAllModulesOffButLegacyDefaultsAndInactiveParentRemainCompatible() {
        DmsTenantDisplayConfig invalid = new DmsTenantDisplayConfig();
        invalid.setTenantId(91L);
        invalid.setLayoutTemplate("category-focus");
        invalid.setCategoryGuideTemplate("directory");
        invalid.setCategoryGuidePrimaryCategoriesEnabled(0);
        invalid.setCategoryGuideSubcategoriesEnabled(0);
        invalid.setCategoryGuideHotProductsEnabled(0);
        assertThrows(ApiException.class, () -> support.prepareForSave(invalid));

        DmsTenantDisplayConfig legacy = new DmsTenantDisplayConfig();
        legacy.setTenantId(92L);
        legacy.setLayoutTemplate("category-focus");
        legacy.setCategoryGuideTemplate("directory");
        support.prepareForSave(legacy);
        assertEquals(1, legacy.getCategoryGuidePrimaryCategoriesEnabled());
        assertEquals(1, legacy.getCategoryGuideSubcategoriesEnabled());
        assertEquals(1, legacy.getCategoryGuideHotProductsEnabled());

        invalid.setLayoutTemplate("standard");
        support.prepareForSave(invalid);
        invalid.setLayoutTemplate("category-focus");
        invalid.setCategoryGuidePrimaryCategoriesEnabled(1);
        support.prepareForSave(invalid);
        assertEquals(0, invalid.getCategoryGuideSubcategoriesEnabled());
        assertEquals(0, invalid.getCategoryGuideHotProductsEnabled());
    }

    @Test
    void requiredCapabilitiesCannotBeDisabledByTypedFieldOrExtraJson() {
        DmsTenantDisplayConfig typed = new DmsTenantDisplayConfig();
        typed.setTenantId(10L);
        typed.setCartEnabled(0);
        assertThrows(ApiException.class, () -> support.prepareForSave(typed));

        DmsTenantDisplayConfig json = new DmsTenantDisplayConfig();
        json.setTenantId(10L);
        json.setExtraConfigJson("{\"requiredCapabilities\":{\"legalCompliance\":0}}");
        assertThrows(ApiException.class, () -> support.prepareForSave(json));

        DmsTenantDisplayConfig nav = new DmsTenantDisplayConfig();
        nav.setTenantId(10L);
        nav.setExtraConfigJson("{\"bottomNav\":[{\"type\":\"cart\",\"enabled\":false}]}");
        assertThrows(ApiException.class, () -> support.prepareForSave(nav));

        DmsTenantDisplayConfig removedNav = new DmsTenantDisplayConfig();
        removedNav.setTenantId(10L);
        removedNav.setExtraConfigJson("{\"bottomNav\":[{\"type\":\"home\",\"enabled\":true}]}");
        assertThrows(ApiException.class, () -> support.prepareForSave(removedNav));
    }

    @Test
    void tenantConfigurationsRemainIndependent() {
        DmsTenantDisplayConfig first = new DmsTenantDisplayConfig();
        first.setTenantId(21L);
        first.setCategoryGuideTemplate("directory");
        first.setCategoryGuideHotProductsEnabled(0);
        first.setBrandCultureDetailImages(List.of(new BrandCultureImageRefVO(
                "/api/shop/media/brand-culture/21/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg", 100L)));
        support.prepareForSave(first);

        DmsTenantDisplayConfig second = new DmsTenantDisplayConfig();
        second.setTenantId(22L);
        second.setCategoryGuideTemplate("scenario");
        second.setCategoryGuideHotProductsEnabled(1);
        second.setBrandCultureDetailImages(List.of(new BrandCultureImageRefVO(
                "/api/shop/media/brand-culture/22/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png", 200L)));
        support.prepareForSave(second);

        assertEquals(21L, first.getTenantId());
        assertEquals("directory", first.getCategoryGuideTemplate());
        assertEquals(0, first.getCategoryGuideHotProductsEnabled());
        assertTrue(first.getBrandCultureDetailImages().get(0).getUrl().contains("/21/"));
        assertEquals(22L, second.getTenantId());
        assertEquals("scenario", second.getCategoryGuideTemplate());
        assertEquals(1, second.getCategoryGuideHotProductsEnabled());
        assertTrue(second.getBrandCultureDetailImages().get(0).getUrl().contains("/22/"));
    }
}
