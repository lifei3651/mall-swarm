package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.service.impl.TenantDisplayConfigSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(1, config.getShowPv());
    }

    @Test
    void readsLayoutFieldsFromExistingExtraConfig() {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(2L);
        config.setExtraConfigJson("{\"layoutTemplate\":\"campaign-feed\",\"showHomeCategories\":false,\"showBottomCategoryNav\":0,\"liveSquareEnabled\":false}");

        support.prepareForRead(config, 2L);

        assertEquals("campaign-feed", config.getLayoutTemplate());
        assertEquals(0, config.getShowHomeCategories());
        assertEquals(0, config.getShowBottomCategoryNav());
        assertEquals(0, config.getLiveSquareEnabled());
    }

    @Test
    void savesFriendlyFieldsAndPreservesUnknownExtensions() throws Exception {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(3L);
        config.setLayoutTemplate("category-focus");
        config.setShowHomeCategories(1);
        config.setShowBottomCategoryNav(1);
        config.setLiveSquareEnabled(0);
        config.setExtraConfigJson("{\"futureSetting\":\"keep-me\"}");

        support.prepareForSave(config);
        JsonNode json = objectMapper.readTree(config.getExtraConfigJson());

        assertEquals("category-focus", json.get("layoutTemplate").asText());
        assertEquals(1, json.get("showHomeCategories").asInt());
        assertEquals(1, json.get("showBottomCategoryNav").asInt());
        assertEquals(0, json.get("liveSquareEnabled").asInt());
        assertEquals("keep-me", json.get("futureSetting").asText());
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
}
