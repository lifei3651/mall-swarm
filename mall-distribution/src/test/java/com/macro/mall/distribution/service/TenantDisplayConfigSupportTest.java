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
    void allFourVisualLayoutsPreserveHomeModulesCategoryVisibilityAndChildConfiguration() throws Exception {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(90L);
        config.setShowHomeCategories(0);
        config.setLiveSquareEnabled(0);
        config.setNewArrivalsEnabled(1);
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
        config.setExtraConfigJson("{\"homeModules\":["
                + "{\"type\":\"products\",\"enabled\":true,\"sort\":1},"
                + "{\"type\":\"category\",\"enabled\":false,\"sort\":2},"
                + "{\"type\":\"banner\",\"enabled\":true,\"sort\":3}],"
                + "\"bottomNav\":["
                + "{\"type\":\"home\",\"enabled\":true},"
                + "{\"type\":\"category\",\"enabled\":false},"
                + "{\"type\":\"cart\",\"enabled\":true},"
                + "{\"type\":\"orders\",\"enabled\":true},"
                + "{\"type\":\"profile\",\"enabled\":true}],"
                + "\"futureSetting\":{\"nested\":\"keep\"}}");
        JsonNode expectedModules = objectMapper.readTree(config.getExtraConfigJson()).path("homeModules").deepCopy();

        for (String layout : List.of("standard", "product-focus", "category-focus", "campaign-feed")) {
            config.setLayoutTemplate(layout);
            support.prepareForSave(config);
            JsonNode saved = objectMapper.readTree(config.getExtraConfigJson());

            assertEquals(expectedModules, saved.path("homeModules"));
            assertEquals(0, config.getShowHomeCategories());
            assertEquals("scenario", config.getCategoryGuideTemplate());
            assertEquals(0, config.getCategoryGuidePrimaryCategoriesEnabled());
            assertEquals(0, config.getCategoryGuideShelvesEnabled());
            assertEquals(0, config.getCategoryGuideQuickEntriesEnabled());
            assertEquals(0, saved.path("bottomNav").get(1).path("enabled").asInt());
            assertEquals(1, saved.path("bottomNav").get(3).path("enabled").asInt());
            assertEquals("keep", saved.path("futureSetting").path("nested").asText());
        }
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
    void requiredCapabilitiesCannotBeDisabledButMissingNavItemsAreSafelyNormalized() throws Exception {
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
        removedNav.setExtraConfigJson("{\"futureSetting\":\"keep\",\"bottomNav\":[{\"type\":\"home\",\"enabled\":true,\"futureStyle\":\"keep\"}]}");
        support.prepareForSave(removedNav);
        JsonNode normalized = objectMapper.readTree(removedNav.getExtraConfigJson());
        assertEquals(List.of("home", "category", "cart", "orders", "profile"),
                java.util.stream.StreamSupport.stream(normalized.path("bottomNav").spliterator(), false)
                        .map(item -> item.path("type").asText()).toList());
        assertEquals(1, normalized.path("bottomNav").get(1).path("enabled").asInt());
        assertEquals(0, normalized.path("bottomNav").get(3).path("enabled").asInt());
        assertEquals("keep", normalized.path("bottomNav").get(0).path("futureStyle").asText());
        assertEquals("keep", normalized.path("futureSetting").asText());
        assertEquals(1, normalized.path("bottomNavIndependent").asInt());

        DmsTenantDisplayConfig hiddenHome = new DmsTenantDisplayConfig();
        hiddenHome.setTenantId(10L);
        hiddenHome.setExtraConfigJson("{\"bottomNav\":[{\"type\":\"home\",\"enabled\":false}]}");
        assertThrows(ApiException.class, () -> support.prepareForSave(hiddenHome));
    }

    @Test
    void categoryAndOrdersVisibilityRoundTripWithoutChangingRoutesOrCapabilities() throws Exception {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(11L);
        config.setLayoutTemplate("campaign-feed");
        config.setExtraConfigJson("{\"bottomNav\":["
                + "{\"type\":\"home\",\"enabled\":true},"
                + "{\"type\":\"category\",\"enabled\":false},"
                + "{\"type\":\"cart\",\"enabled\":true},"
                + "{\"type\":\"orders\",\"enabled\":true},"
                + "{\"type\":\"profile\",\"enabled\":true}]}");

        support.prepareForSave(config);
        JsonNode saved = objectMapper.readTree(config.getExtraConfigJson());
        assertEquals(0, saved.path("bottomNav").get(1).path("enabled").asInt());
        assertEquals(1, saved.path("bottomNav").get(3).path("enabled").asInt());
        assertEquals(0, config.getShowBottomCategoryNav());
        assertEquals(1, config.getCartEnabled());
        assertEquals(1, config.getAccountSecurityEnabled());

        for (String layout : List.of("standard", "product-focus", "category-focus", "campaign-feed")) {
            config.setLayoutTemplate(layout);
            if ("category-focus".equals(layout)) config.setCategoryGuidePrimaryCategoriesEnabled(1);
            support.prepareForSave(config);
            JsonNode nav = objectMapper.readTree(config.getExtraConfigJson()).path("bottomNav");
            assertEquals(0, nav.get(1).path("enabled").asInt());
            assertEquals(1, nav.get(3).path("enabled").asInt());
        }
    }

    @Test
    void firstSaveRestoresLegacyProductFocusCategoryEvenWhenNavStoredItAsDisabled() throws Exception {
        DmsTenantDisplayConfig legacy = new DmsTenantDisplayConfig();
        legacy.setTenantId(12L);
        legacy.setLayoutTemplate("product-focus");
        legacy.setShowBottomCategoryNav(0);
        legacy.setExtraConfigJson("{\"futureSetting\":\"keep\",\"bottomNav\":["
                + "{\"type\":\"category\",\"enabled\":false,\"futureStyle\":\"keep\"},"
                + "{\"type\":\"orders\",\"enabled\":true}]}");

        support.prepareForSave(legacy);

        JsonNode saved = objectMapper.readTree(legacy.getExtraConfigJson());
        assertEquals(1, saved.path("bottomNav").get(1).path("enabled").asInt());
        assertEquals("keep", saved.path("bottomNav").get(1).path("futureStyle").asText());
        assertEquals(1, saved.path("bottomNav").get(3).path("enabled").asInt());
        assertEquals("keep", saved.path("futureSetting").asText());
        assertEquals(1, saved.path("bottomNavIndependent").asInt());
        assertEquals(1, legacy.getShowBottomCategoryNav());
    }

    @Test
    void firstSaveRestoresLegacyProductFocusCategoryWhenBottomNavIsMissing() throws Exception {
        DmsTenantDisplayConfig legacy = new DmsTenantDisplayConfig();
        legacy.setTenantId(13L);
        legacy.setExtraConfigJson("{\"layoutTemplate\":\"product-focus\","
                + "\"showBottomCategoryNav\":0,\"futureSetting\":\"keep\"}");

        support.prepareForSave(legacy);

        JsonNode saved = objectMapper.readTree(legacy.getExtraConfigJson());
        assertEquals(1, saved.path("bottomNav").get(1).path("enabled").asInt());
        assertEquals(0, saved.path("bottomNav").get(3).path("enabled").asInt());
        assertEquals("product-focus", saved.path("layoutTemplate").asText());
        assertEquals(1, saved.path("bottomNavIndependent").asInt());
        assertEquals("keep", saved.path("futureSetting").asText());
        assertEquals(1, legacy.getShowBottomCategoryNav());
    }

    @Test
    void independentMarkerPreservesUserDisabledCategoryOnProductFocus() throws Exception {
        DmsTenantDisplayConfig independent = new DmsTenantDisplayConfig();
        independent.setTenantId(14L);
        independent.setLayoutTemplate("product-focus");
        independent.setShowBottomCategoryNav(0);
        independent.setExtraConfigJson("{\"bottomNavIndependent\":1,\"bottomNav\":["
                + "{\"type\":\"category\",\"enabled\":false,\"futureStyle\":\"keep\"}]}");

        support.prepareForSave(independent);

        JsonNode saved = objectMapper.readTree(independent.getExtraConfigJson());
        assertEquals(0, saved.path("bottomNav").get(1).path("enabled").asInt());
        assertEquals("keep", saved.path("bottomNav").get(1).path("futureStyle").asText());
        assertEquals(1, saved.path("bottomNavIndependent").asInt());
        assertEquals(0, independent.getShowBottomCategoryNav());
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
