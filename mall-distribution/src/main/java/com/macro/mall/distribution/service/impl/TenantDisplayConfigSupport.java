package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 商城展示配置的默认值和扩展字段编解码。
 *
 * <p>布局配置保存在现有 extra_config_json 字段中，既不破坏旧数据，也允许后续继续增加
 * 商城端模块开关。未知扩展字段会在保存时原样保留。</p>
 */
@Component
@RequiredArgsConstructor
public class TenantDisplayConfigSupport {

    public static final String DEFAULT_LAYOUT_TEMPLATE = "standard";
    private static final Set<String> LAYOUT_TEMPLATES = Set.of(
            "standard", "product-focus", "category-focus", "campaign-feed");

    private final ObjectMapper objectMapper;

    public DmsTenantDisplayConfig prepareForRead(DmsTenantDisplayConfig config, Long tenantId) {
        DmsTenantDisplayConfig resolved = config == null ? new DmsTenantDisplayConfig() : config;
        if (resolved.getTenantId() == null) {
            resolved.setTenantId(tenantId);
        }
        fillDefaults(resolved);
        hydrateLayoutFields(resolved);
        return resolved;
    }

    public void prepareForSave(DmsTenantDisplayConfig config) {
        fillDefaults(config);
        normalizeLayoutFields(config);

        ObjectNode extra = readExtraObject(config.getExtraConfigJson());
        extra.put("layoutTemplate", config.getLayoutTemplate());
        extra.put("showHomeCategories", config.getShowHomeCategories());
        extra.put("showBottomCategoryNav", config.getShowBottomCategoryNav());
        extra.put("liveSquareEnabled", config.getLiveSquareEnabled());
        extra.put("newArrivalsEnabled", config.getNewArrivalsEnabled());
        try {
            config.setExtraConfigJson(objectMapper.writeValueAsString(extra));
        } catch (Exception ignored) {
            // ObjectNode 序列化理论上不会失败；兜底保证界面设置仍可保存。
            config.setExtraConfigJson("{\"layoutTemplate\":\"standard\",\"showHomeCategories\":1,\"showBottomCategoryNav\":1,\"liveSquareEnabled\":1,\"newArrivalsEnabled\":1}");
        }
    }

    private void hydrateLayoutFields(DmsTenantDisplayConfig config) {
        ObjectNode extra = readExtraObject(config.getExtraConfigJson());
        if (config.getLayoutTemplate() == null) {
            config.setLayoutTemplate(textValue(extra.get("layoutTemplate"), DEFAULT_LAYOUT_TEMPLATE));
        }
        if (config.getShowHomeCategories() == null) {
            config.setShowHomeCategories(toggleValue(extra.get("showHomeCategories"), 1));
        }
        if (config.getShowBottomCategoryNav() == null) {
            config.setShowBottomCategoryNav(toggleValue(extra.get("showBottomCategoryNav"), 1));
        }
        if (config.getLiveSquareEnabled() == null) {
            config.setLiveSquareEnabled(toggleValue(extra.get("liveSquareEnabled"), 1));
        }
        if (config.getNewArrivalsEnabled() == null) {
            config.setNewArrivalsEnabled(toggleValue(extra.get("newArrivalsEnabled"), 1));
        }
        normalizeLayoutFields(config);
    }

    private void normalizeLayoutFields(DmsTenantDisplayConfig config) {
        String template = config.getLayoutTemplate();
        config.setLayoutTemplate(template != null && LAYOUT_TEMPLATES.contains(template)
                ? template : DEFAULT_LAYOUT_TEMPLATE);
        config.setShowHomeCategories(normalizeToggle(config.getShowHomeCategories(), 1));
        config.setShowBottomCategoryNav(normalizeToggle(config.getShowBottomCategoryNav(), 1));
        config.setLiveSquareEnabled(normalizeToggle(config.getLiveSquareEnabled(), 1));
        config.setNewArrivalsEnabled(normalizeToggle(config.getNewArrivalsEnabled(), 1));
    }

    private void fillDefaults(DmsTenantDisplayConfig config) {
        config.setShowPv(normalizeToggle(config.getShowPv(), 1));
        config.setShowTeamPerformance(normalizeToggle(config.getShowTeamPerformance(), 0));
        config.setShowBonusSource(normalizeToggle(config.getShowBonusSource(), 0));
        config.setShowBonusFlow(normalizeToggle(config.getShowBonusFlow(), 0));
        config.setShowProfit(normalizeToggle(config.getShowProfit(), 0));
        config.setShowRank(normalizeToggle(config.getShowRank(), 0));
        config.setShowBinaryArea(normalizeToggle(config.getShowBinaryArea(), 0));
        config.setShowRetailModule(normalizeToggle(config.getShowRetailModule(), 0));
        config.setShowStoreModule(normalizeToggle(config.getShowStoreModule(), 0));
        config.setShowCompanyShare(normalizeToggle(config.getShowCompanyShare(), 0));
    }

    private ObjectNode readExtraObject(String rawJson) {
        if (rawJson != null && !rawJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(rawJson);
                if (node instanceof ObjectNode objectNode) {
                    return objectNode.deepCopy();
                }
            } catch (Exception ignored) {
                // 兼容历史无效 JSON：读取时使用安全默认值，下一次保存会自动修复格式。
            }
        }
        return objectMapper.createObjectNode();
    }

    private String textValue(JsonNode node, String defaultValue) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : defaultValue;
    }

    private Integer toggleValue(JsonNode node, int defaultValue) {
        if (node == null || node.isNull()) return defaultValue;
        if (node.isBoolean()) return node.asBoolean() ? 1 : 0;
        if (node.isInt() || node.isLong()) return node.asInt() == 1 ? 1 : 0;
        if (node.isTextual()) {
            String value = node.asText().trim();
            if ("1".equals(value) || "true".equalsIgnoreCase(value)) return 1;
            if ("0".equals(value) || "false".equalsIgnoreCase(value)) return 0;
        }
        return defaultValue;
    }

    private Integer normalizeToggle(Integer value, int defaultValue) {
        return value == null ? defaultValue : (value == 1 ? 1 : 0);
    }
}
