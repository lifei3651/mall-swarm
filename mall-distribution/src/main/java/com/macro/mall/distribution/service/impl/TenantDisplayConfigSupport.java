package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.vo.BrandCultureImageRefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public static final String DEFAULT_CATEGORY_GUIDE_TEMPLATE = "directory";
    private static final Set<String> CATEGORY_GUIDE_TEMPLATES = Set.of(
            "directory", "showcase", "scenario");
    private static final List<String> BOTTOM_NAV_TYPES = List.of("home", "category", "cart", "orders", "profile");
    private static final Set<String> REQUIRED_BOTTOM_NAV_TYPES = Set.of("home", "cart", "profile");
    private static final Set<String> REQUIRED_CAPABILITY_KEYS = Set.of(
            "productDetail", "cart", "checkout", "accountSecurity",
            "legalCompliance", "afterSales", "customerService");

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
        validateRequiredCapabilities(config);
        fillDefaults(config);

        ObjectNode extra = readExtraObject(config.getExtraConfigJson());
        String submittedLayoutTemplate = config.getLayoutTemplate() == null
                ? textValue(extra.get("layoutTemplate"), DEFAULT_LAYOUT_TEMPLATE)
                : config.getLayoutTemplate();
        Integer submittedCategoryEnabled = config.getShowBottomCategoryNav() == null
                ? toggleValue(extra.get("showBottomCategoryNav"), 1)
                : config.getShowBottomCategoryNav();
        boolean restoreLegacyTemplateCoupledCategory = !extra.has("bottomNavIndependent")
                && "product-focus".equals(submittedLayoutTemplate)
                && normalizeToggle(submittedCategoryEnabled, 1) == 0;
        if (config.getLayoutTemplate() == null) {
            config.setLayoutTemplate(submittedLayoutTemplate);
        }
        if (config.getShowBottomCategoryNav() == null) {
            config.setShowBottomCategoryNav(submittedCategoryEnabled);
        }

        normalizeLayoutFields(config);
        validateActiveCategoryGuide(config);

        config.setShowBottomCategoryNav(normalizeBottomNav(
                extra, config.getShowBottomCategoryNav(), restoreLegacyTemplateCoupledCategory));
        List<BrandCultureImageRefVO> cultureImages = normalizeCultureImages(config.getBrandCultureDetailImages(), true);
        config.setBrandCultureDetailImages(cultureImages);
        ArrayNode cultureImageJson = extra.putArray("brandCultureDetailImages");
        cultureImages.forEach(image -> {
            ObjectNode item = cultureImageJson.addObject();
            item.put("url", image.getUrl());
            item.put("size", image.getSize() == null ? 0L : image.getSize());
        });
        extra.put("layoutTemplate", config.getLayoutTemplate());
        extra.put("showHomeCategories", config.getShowHomeCategories());
        extra.put("showBottomCategoryNav", config.getShowBottomCategoryNav());
        extra.put("liveSquareEnabled", config.getLiveSquareEnabled());
        extra.put("newArrivalsEnabled", config.getNewArrivalsEnabled());
        extra.put("newArrivalWindowDays", config.getNewArrivalWindowDays());
        extra.put("categoryGuideTemplate", config.getCategoryGuideTemplate());
        ObjectNode guideModules = extra.withObject("categoryGuideModules");
        guideModules.put("primaryCategories", config.getCategoryGuidePrimaryCategoriesEnabled());
        guideModules.put("subcategories", config.getCategoryGuideSubcategoriesEnabled());
        guideModules.put("hotProducts", config.getCategoryGuideHotProductsEnabled());
        guideModules.put("heroCategories", config.getCategoryGuideHeroCategoriesEnabled());
        guideModules.put("shelves", config.getCategoryGuideShelvesEnabled());
        guideModules.put("recommendedProducts", config.getCategoryGuideRecommendedProductsEnabled());
        guideModules.put("scenarios", config.getCategoryGuideScenariosEnabled());
        guideModules.put("quickEntries", config.getCategoryGuideQuickEntriesEnabled());
        guideModules.put("popularProducts", config.getCategoryGuidePopularProductsEnabled());
        ObjectNode requiredCapabilities = extra.withObject("requiredCapabilities");
        requiredCapabilities.put("productDetail", 1);
        requiredCapabilities.put("cart", 1);
        requiredCapabilities.put("checkout", 1);
        requiredCapabilities.put("accountSecurity", 1);
        requiredCapabilities.put("legalCompliance", 1);
        requiredCapabilities.put("afterSales", 1);
        requiredCapabilities.put("customerService", 1);
        extra.put("bottomNavIndependent", 1);
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
        if (config.getNewArrivalWindowDays() == null) {
            config.setNewArrivalWindowDays(dayValue(extra.get("newArrivalWindowDays"), 30));
        }
        if (config.getCategoryGuideTemplate() == null) {
            config.setCategoryGuideTemplate(textValue(extra.get("categoryGuideTemplate"), DEFAULT_CATEGORY_GUIDE_TEMPLATE));
        }
        JsonNode guideModules = extra.get("categoryGuideModules");
        hydrateGuideModules(config, guideModules);
        hydrateBrandCultureImages(config);
        normalizeLayoutFields(config);
    }

    /** 兼容只回传 extraConfigJson 的旧后台，不因其未识别新字段而清空已保存详情图。 */
    public void hydrateBrandCultureImages(DmsTenantDisplayConfig config) {
        if (config != null && config.getBrandCultureDetailImages() == null) {
            ObjectNode extra = readExtraObject(config.getExtraConfigJson());
            config.setBrandCultureDetailImages(readCultureImages(extra.get("brandCultureDetailImages")));
        }
    }

    private List<BrandCultureImageRefVO> readCultureImages(JsonNode node) {
        List<BrandCultureImageRefVO> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        node.forEach(item -> {
            String url = item.isTextual() ? item.asText("") : item.path("url").asText("");
            long size = item.isObject() ? Math.max(0L, item.path("size").asLong(0L)) : 0L;
            if (!url.isBlank() && result.size() < 10) result.add(new BrandCultureImageRefVO(url.trim(), size));
        });
        return result;
    }

    private List<BrandCultureImageRefVO> normalizeCultureImages(List<BrandCultureImageRefVO> images, boolean strict) {
        List<BrandCultureImageRefVO> result = new ArrayList<>();
        if (images == null) return result;
        if (strict && images.size() > 10) Asserts.fail("品牌文化详情图最多上传10张");
        for (BrandCultureImageRefVO image : images) {
            if (image == null || image.getUrl() == null || image.getUrl().isBlank()) {
                if (strict) Asserts.fail("品牌文化详情图地址不能为空");
                continue;
            }
            if (result.size() >= 10) break;
            result.add(new BrandCultureImageRefVO(image.getUrl().trim(), Math.max(0L,
                    image.getSize() == null ? 0L : image.getSize())));
        }
        return result;
    }

    private void normalizeLayoutFields(DmsTenantDisplayConfig config) {
        String template = config.getLayoutTemplate();
        config.setLayoutTemplate(template != null && LAYOUT_TEMPLATES.contains(template)
                ? template : DEFAULT_LAYOUT_TEMPLATE);
        config.setShowHomeCategories(normalizeToggle(config.getShowHomeCategories(), 1));
        config.setShowBottomCategoryNav(normalizeToggle(config.getShowBottomCategoryNav(), 1));
        config.setLiveSquareEnabled(normalizeToggle(config.getLiveSquareEnabled(), 1));
        config.setNewArrivalsEnabled(normalizeToggle(config.getNewArrivalsEnabled(), 1));
        config.setNewArrivalWindowDays(normalizeNewArrivalDays(config.getNewArrivalWindowDays()));
        String guideTemplate = config.getCategoryGuideTemplate();
        config.setCategoryGuideTemplate(guideTemplate != null && CATEGORY_GUIDE_TEMPLATES.contains(guideTemplate)
                ? guideTemplate : DEFAULT_CATEGORY_GUIDE_TEMPLATE);
        config.setCategoryGuidePrimaryCategoriesEnabled(normalizeToggle(config.getCategoryGuidePrimaryCategoriesEnabled(), 1));
        config.setCategoryGuideSubcategoriesEnabled(normalizeToggle(config.getCategoryGuideSubcategoriesEnabled(), 1));
        config.setCategoryGuideHotProductsEnabled(normalizeToggle(config.getCategoryGuideHotProductsEnabled(), 1));
        config.setCategoryGuideHeroCategoriesEnabled(normalizeToggle(config.getCategoryGuideHeroCategoriesEnabled(), 1));
        config.setCategoryGuideShelvesEnabled(normalizeToggle(config.getCategoryGuideShelvesEnabled(), 1));
        config.setCategoryGuideRecommendedProductsEnabled(normalizeToggle(config.getCategoryGuideRecommendedProductsEnabled(), 1));
        config.setCategoryGuideScenariosEnabled(normalizeToggle(config.getCategoryGuideScenariosEnabled(), 1));
        config.setCategoryGuideQuickEntriesEnabled(normalizeToggle(config.getCategoryGuideQuickEntriesEnabled(), 1));
        config.setCategoryGuidePopularProductsEnabled(normalizeToggle(config.getCategoryGuidePopularProductsEnabled(), 1));
        forceRequiredCapabilities(config);
    }

    /** A 目录版启用时至少保留一个真实模块；其他父版型只保留子值，不触发校验。 */
    private void validateActiveCategoryGuide(DmsTenantDisplayConfig config) {
        if (!"category-focus".equals(config.getLayoutTemplate())
                || !"directory".equals(config.getCategoryGuideTemplate())) {
            return;
        }
        boolean allDisabled = config.getCategoryGuidePrimaryCategoriesEnabled() == 0
                && config.getCategoryGuideSubcategoriesEnabled() == 0
                && config.getCategoryGuideHotProductsEnabled() == 0;
        if (allDisabled) {
            Asserts.fail("请至少开启一个分类导购模块");
        }
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

    private Integer dayValue(JsonNode node, int defaultValue) {
        if (node == null || !node.canConvertToInt()) return defaultValue;
        return normalizeNewArrivalDays(node.asInt());
    }

    private Integer normalizeNewArrivalDays(Integer value) {
        if (value == null) return 30;
        return value == 0 || (value >= 30 && value <= 365) ? value : 30;
    }

    private void hydrateGuideModules(DmsTenantDisplayConfig config, JsonNode modules) {
        config.setCategoryGuidePrimaryCategoriesEnabled(toggleField(
                config.getCategoryGuidePrimaryCategoriesEnabled(), modules, "primaryCategories", 1));
        config.setCategoryGuideSubcategoriesEnabled(toggleField(
                config.getCategoryGuideSubcategoriesEnabled(), modules, "subcategories", 1));
        config.setCategoryGuideHotProductsEnabled(toggleField(
                config.getCategoryGuideHotProductsEnabled(), modules, "hotProducts", 1));
        config.setCategoryGuideHeroCategoriesEnabled(toggleField(
                config.getCategoryGuideHeroCategoriesEnabled(), modules, "heroCategories", 1));
        config.setCategoryGuideShelvesEnabled(toggleField(
                config.getCategoryGuideShelvesEnabled(), modules, "shelves", 1));
        config.setCategoryGuideRecommendedProductsEnabled(toggleField(
                config.getCategoryGuideRecommendedProductsEnabled(), modules, "recommendedProducts", 1));
        config.setCategoryGuideScenariosEnabled(toggleField(
                config.getCategoryGuideScenariosEnabled(), modules, "scenarios", 1));
        config.setCategoryGuideQuickEntriesEnabled(toggleField(
                config.getCategoryGuideQuickEntriesEnabled(), modules, "quickEntries", 1));
        config.setCategoryGuidePopularProductsEnabled(toggleField(
                config.getCategoryGuidePopularProductsEnabled(), modules, "popularProducts", 1));
    }

    private Integer toggleField(Integer current, JsonNode parent, String field, int defaultValue) {
        if (current != null) return current;
        return parent != null && parent.isObject()
                ? toggleValue(parent.get(field), defaultValue) : defaultValue;
    }

    private void validateRequiredCapabilities(DmsTenantDisplayConfig config) {
        requireEnabled(config.getProductDetailEnabled(), "商品详情");
        requireEnabled(config.getCartEnabled(), "购物车");
        requireEnabled(config.getCheckoutEnabled(), "结算与下单");
        requireEnabled(config.getAccountSecurityEnabled(), "账号安全");
        requireEnabled(config.getLegalComplianceEnabled(), "合规与协议");
        requireEnabled(config.getAfterSalesEnabled(), "售后");
        requireEnabled(config.getCustomerServiceEnabled(), "客服");

        ObjectNode extra = readExtraObject(config.getExtraConfigJson());
        JsonNode required = extra.get("requiredCapabilities");
        if (required != null && required.isObject()) {
            REQUIRED_CAPABILITY_KEYS.forEach(key -> {
                if (required.has(key) && !toggleValue(required.get(key), 1).equals(1)) {
                    Asserts.fail("系统必需/合规锁定能力不能关闭：" + key);
                }
            });
        }
        JsonNode bottomNav = extra.get("bottomNav");
        if (bottomNav != null && bottomNav.isArray()) {
            bottomNav.forEach(item -> {
                String type = item.path("type").asText("");
                if (REQUIRED_BOTTOM_NAV_TYPES.contains(type)
                        && !toggleValue(item.get("enabled"), 1).equals(1)) {
                    Asserts.fail("系统必需入口不能关闭：" + type);
                }
            });
        }
    }

    /**
     * 底部导航独立于首页版型。保留每个已知项的未知字段，仅将受控入口归一为最多五项。
     * 旧数组缺少分类时默认显示，缺少订单时默认隐藏；完全没有数组时仍尊重旧 showBottomCategoryNav 字段。
     * 仅当独立标记缺失且命中旧紧凑版联动指纹时，首次保存恢复分类；写入标记后尊重用户手动关闭。
     */
    private Integer normalizeBottomNav(ObjectNode extra, Integer legacyCategoryEnabled,
                                       boolean restoreLegacyTemplateCoupledCategory) {
        JsonNode raw = extra.get("bottomNav");
        boolean hasConfiguredNav = raw != null && raw.isArray() && !raw.isEmpty();
        Map<String, ObjectNode> configured = new LinkedHashMap<>();
        if (hasConfiguredNav) {
            raw.forEach(item -> {
                if (!item.isObject()) return;
                String type = item.path("type").asText("");
                if (BOTTOM_NAV_TYPES.contains(type) && !configured.containsKey(type)) {
                    configured.put(type, ((ObjectNode) item).deepCopy());
                }
            });
        }

        ArrayNode normalized = objectMapper.createArrayNode();
        int categoryEnabled = 1;
        for (String type : BOTTOM_NAV_TYPES) {
            ObjectNode item = configured.getOrDefault(type, objectMapper.createObjectNode());
            item.put("type", type);
            if (!item.hasNonNull("label") || item.path("label").asText().isBlank()) {
                item.put("label", switch (type) {
                    case "home" -> "首页";
                    case "category" -> "分类";
                    case "cart" -> "购物车";
                    case "orders" -> "订单";
                    default -> "我的";
                });
            }
            int enabled;
            if (REQUIRED_BOTTOM_NAV_TYPES.contains(type)) {
                enabled = 1;
            } else if ("category".equals(type) && restoreLegacyTemplateCoupledCategory) {
                enabled = 1;
            } else if (configured.containsKey(type)) {
                enabled = toggleValue(item.get("enabled"), "category".equals(type) ? 1 : 0);
            } else if ("category".equals(type)) {
                enabled = hasConfiguredNav ? 1 : normalizeToggle(legacyCategoryEnabled, 1);
            } else {
                enabled = 0;
            }
            item.put("enabled", enabled == 1);
            if ("category".equals(type)) categoryEnabled = enabled;
            normalized.add(item);
        }
        extra.set("bottomNav", normalized);
        return categoryEnabled;
    }

    private void requireEnabled(Integer value, String label) {
        if (value != null && value != 1) {
            Asserts.fail("系统必需/合规锁定能力不能关闭：" + label);
        }
    }

    private void forceRequiredCapabilities(DmsTenantDisplayConfig config) {
        config.setProductDetailEnabled(1);
        config.setCartEnabled(1);
        config.setCheckoutEnabled(1);
        config.setAccountSecurityEnabled(1);
        config.setLegalComplianceEnabled(1);
        config.setAfterSalesEnabled(1);
        config.setCustomerServiceEnabled(1);
    }
}
