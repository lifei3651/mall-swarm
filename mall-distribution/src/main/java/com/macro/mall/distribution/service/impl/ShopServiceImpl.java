package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.constants.ShopBusinessType;
import com.macro.mall.distribution.dto.OrderFinanceDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopSkuDTO;
import com.macro.mall.distribution.dto.ProductPublishDTO;
import com.macro.mall.distribution.dto.ProductNewArrivalDTO;
import com.macro.mall.distribution.dto.FreightTemplateRuleDTO;
import com.macro.mall.distribution.dto.FreightTemplateSaveDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.enums.AgentStatusEnum;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.service.PaymentVerificationService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.ShopCatalogCacheService;
import com.macro.mall.distribution.service.ShopBusinessModeService;
import com.macro.mall.distribution.service.FlashSaleStockGate;
import com.macro.mall.distribution.util.ShopOrderNoGenerator;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.OrderRelationSnapshotService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.service.OrderRealtimeService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.MerchantService;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.MerchantProductReviewService;
import com.macro.mall.distribution.service.LiveRoomService;
import com.macro.mall.distribution.service.ContentModerationService;
import com.macro.mall.distribution.service.BrandCultureImagePolicy;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.OrderFinanceVO;
import com.macro.mall.distribution.vo.ShopHomeVO;
import com.macro.mall.distribution.vo.ShopLegalConfigVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.ShopOrderStatusSummaryVO;
import com.macro.mall.distribution.vo.ShopTradeDetailVO;
import com.macro.mall.distribution.vo.ShopProductDetailVO;
import com.macro.mall.distribution.vo.ShopProfileVO;
import com.macro.mall.distribution.vo.ShopBrandCultureVO;
import com.macro.mall.distribution.vo.FreightQuoteVO;
import com.macro.mall.distribution.vo.PurchaseLimitCheckVO;
import com.macro.mall.distribution.vo.DistributionSettingsVO;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.pagehelper.PageHelper;
import cn.hutool.crypto.digest.DigestUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

import static com.macro.mall.distribution.util.ShopPublicViewSanitizer.product;
import static com.macro.mall.distribution.util.ShopPublicViewSanitizer.sku;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopServiceImpl implements ShopService {

    private static final Pattern BRAND_CULTURE_IMAGE_REFERENCE = Pattern.compile(
            "(?i)^[^\\r\\n。！？；]{1,240}\\.(?:jpe?g|png|webp|gif)(?:\\?[^\\r\\n]*)?$");

    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Value("${shop.catalog.max-categories:500}")
    private int maxCategories;

    private final DmsShopProductDao productDao;
    private final DmsShopCategoryDao categoryDao;
    private final DmsShopBannerDao bannerDao;
    private final DmsShopNoticeDao noticeDao;
    private final DmsShopSkuDao skuDao;
    private final DmsFreightTemplateDao freightTemplateDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopTradeDao tradeDao;
    private final DmsShopOrderItemDao orderItemDao;
    private final DmsShopOrderShipmentDao orderShipmentDao;
    private final DmsShopAddressDao addressDao;
    private final DmsShopServiceAddressDao serviceAddressDao;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopProductReviewDao productReviewDao;
    private final DmsOrderPvDetailDao orderPvDetailDao;
    private final DmsAgentDao agentDao;
    private final DmsShopMemberDao memberDao;
    private final DmsAgentAccountDao accountDao;
    private final DmsTenantDao tenantDao;
    private final DmsTenantDisplayConfigDao displayConfigDao;
    private final ContentModerationService contentModerationService;
    private final DmsMigrationBaselineDao migrationBaselineDao;
    private final DistributionAuditService auditService;
    private final PerformanceService performanceService;
    private final CommissionService commissionService;
    private final MemberAssetService memberAssetService;
    private final OrderRelationSnapshotService relationSnapshotService;
    @Autowired(required = false)
    private OrderRealtimeService orderRealtimeService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;
    private final ShopAuthService authService;
    private final PaymentVerificationService paymentVerificationService;
    private final com.macro.mall.distribution.service.ErpIntegrationService erpIntegrationService;
    private final com.macro.mall.distribution.service.OrderShipmentService orderShipmentService;
    private final ObjectMapper objectMapper;
    private final ShopCatalogCacheService catalogCache;
    private final ShopAfterSaleWindowPolicy afterSaleWindowPolicy;
    private final ShopBusinessModeService businessModeService;
    private final DmsFlashSaleActivityDao flashSaleActivityDao;
    private final DmsFlashSaleReservationDao flashSaleReservationDao;
    private final FlashSaleStockGate flashSaleStockGate;
    private final OperationLogService operationLogService;
    private final DmsMerchantDao merchantDao;
    private final MerchantService merchantService;
    private final AdminAuthService adminAuthService;
    private final MerchantProductReviewService merchantProductReviewService;
    private final LiveRoomService liveRoomService;
    private final BrandCultureImagePolicy brandCultureImagePolicy;
    private final TransactionTemplate transactionTemplate;

    @Value("${shop.order.pending-timeout-minutes:30}")
    private long pendingOrderTimeoutMinutes;

    @Value("${shop.catalog-cache.home-ttl-seconds:30}")
    private long homeCacheTtlSeconds;

    @Value("${shop.catalog-cache.product-ttl-seconds:15}")
    private long productCacheTtlSeconds;

    @Value("${shop.catalog-cache.category-ttl-seconds:60}")
    private long categoryCacheTtlSeconds;

    @Override
    public ShopHomeVO getHome(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        return catalogCache.get(resolvedTenantId, "home", ShopHomeVO.class, homeCacheTtlSeconds,
                () -> loadHome(resolvedTenantId));
    }

    private ShopHomeVO loadHome(Long resolvedTenantId) {
        DmsTenant tenant = tenantDao.selectById(resolvedTenantId);
        ShopHomeVO vo = new ShopHomeVO();
        vo.setBrandName(tenant == null || tenant.getBrandName() == null ? "商城" : tenant.getBrandName());
        vo.setLogoUrl(tenant == null ? null : tenant.getLogoUrl());
        vo.setThemeColor(tenant == null || tenant.getThemeColor() == null ? "#0f766e" : tenant.getThemeColor());
        vo.setProductTemplate(tenant == null || tenant.getProductTemplate() == null ? "standard" : tenant.getProductTemplate());
        vo.setBrandCultureEnabled(tenant != null && Integer.valueOf(1).equals(tenant.getBrandCultureEnabled()));
        List<DmsShopCategory> categories = categoryDao.selectHomeCategories(resolvedTenantId);
        vo.setCategoryList(categories);
        vo.setCategories(categories.isEmpty()
                ? productDao.selectCategories(resolvedTenantId)
                : categories.stream().map(DmsShopCategory::getCategoryName).toList());
        boolean brandCultureEnabled = tenant != null && Integer.valueOf(1).equals(tenant.getBrandCultureEnabled());
        vo.setBanners(filterPublicBanners(bannerDao.selectActive(resolvedTenantId), brandCultureEnabled));
        vo.setNotices(noticeDao.selectActive(resolvedTenantId));
        List<DmsShopProduct> featuredProducts = productDao.selectFrontList(resolvedTenantId, null, null, 1, null);
        DmsTenantDisplayConfig displayConfig = getDisplayConfig(resolvedTenantId);
        if (!isEnabled(displayConfig.getShowPv())) {
            featuredProducts.forEach(product -> product.setPvValue(ZERO));
        }
        featuredProducts.forEach(item -> product(item, false));
        vo.setFeaturedProducts(featuredProducts);
        vo.setNewArrivals(isEnabled(displayConfig.getNewArrivalsEnabled())
                ? loadNewArrivals(resolvedTenantId, 8, displayConfig.getNewArrivalWindowDays()) : List.of());
        vo.setLiveRooms(liveRoomService.listPublic(resolvedTenantId, 8));
        DistributionSettingsVO publicSettings = auditService.getSettings();
        if (publicSettings != null) publicSettings.setPermissions(null);
        vo.setDistributionSettings(publicSettings);
        vo.setDisplayConfig(displayConfig);
        vo.setLegalConfig(ShopLegalConfigVO.from(tenant));
        vo.setBusinessConfig(businessModeService.config(resolvedTenantId, null));
        return vo;
    }

    @Override
    public List<DmsShopProduct> listProducts(Long tenantId, String keyword, String categoryName, Integer status, String stockStatus) {
        List<DmsShopProduct> products = productDao.selectFrontList(resolveTenantId(tenantId), keyword, categoryName, 1, stockStatus);
        products.forEach(item -> product(item, false));
        return products;
    }

    @Override
    public List<DmsShopProduct> listNewArrivals(Long tenantId, Integer limit) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        DmsTenantDisplayConfig displayConfig = getDisplayConfig(resolvedTenantId);
        if (!isEnabled(displayConfig.getNewArrivalsEnabled())) {
            return List.of();
        }
        return loadNewArrivals(resolvedTenantId, limit == null ? 40 : limit,
                displayConfig.getNewArrivalWindowDays());
    }

    private List<DmsShopProduct> loadNewArrivals(Long tenantId, int limit, Integer windowDays) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        int safeDays = windowDays == null ? 30 : windowDays;
        LocalDateTime cutoff = safeDays == 0 ? null
                : LocalDateTime.now().minusDays(Math.max(30, Math.min(365, safeDays)));
        List<DmsShopProduct> products = productDao.selectNewArrivals(
                tenantId, cutoff, safeLimit);
        products.forEach(item -> product(item, false));
        return products;
    }

    @Override
    public ShopBrandCultureVO getBrandCulture(Long tenantId) {
        DmsTenant tenant = tenantDao.selectById(resolveTenantId(tenantId));
        ShopBrandCultureVO vo = new ShopBrandCultureVO();
        boolean enabled = tenant != null && Integer.valueOf(1).equals(tenant.getBrandCultureEnabled());
        vo.setEnabled(enabled);
        if (!enabled) return vo;
        vo.setBrandName(tenant.getBrandName());
        vo.setLogoUrl(tenant.getLogoUrl());
        vo.setTitle(tenant.getBrandCultureTitle());
        vo.setSubtitle(tenant.getBrandCultureSubtitle());
        vo.setCoverUrl(tenant.getBrandCultureCoverUrl());
        vo.setContent(sanitizeBrandCultureContent(tenant.getBrandCultureContent()));
        DmsTenantDisplayConfig display = getDisplayConfig(tenant.getId());
        vo.setDetailImages(display.getBrandCultureDetailImages() == null ? List.of()
                : display.getBrandCultureDetailImages().stream()
                .map(com.macro.mall.distribution.vo.BrandCultureImageRefVO::getUrl)
                .filter(Objects::nonNull).toList());
        return vo;
    }

    private String sanitizeBrandCultureContent(String content) {
        if (content == null) return null;
        String trimmed = content.trim();
        if (trimmed.isEmpty() || BRAND_CULTURE_IMAGE_REFERENCE.matcher(trimmed).matches()) return null;
        return content;
    }

    @Override
    public com.macro.mall.distribution.vo.ShopBusinessConfigVO getBusinessConfig(DmsShopMember member) {
        return businessModeService.config(resolveTenantId(null), member);
    }

    @Override
    public List<DmsShopProduct> listRepurchaseProducts(String keyword, DmsShopMember member) {
        Long tenantId = resolveTenantId(null);
        businessModeService.requireEnabled(tenantId, ShopBusinessType.REPURCHASE, member);
        List<DmsShopProduct> products = productDao.selectRepurchaseList(tenantId, keyword);
        if (!isPvEnabled(tenantId)) products.forEach(product -> product.setRepurchasePv(ZERO));
        products.forEach(item -> product(item, true));
        return products;
    }

    @Override
    public ShopProductDetailVO getRepurchaseProductDetail(Long id, DmsShopMember member) {
        Long tenantId = resolveTenantId(null);
        businessModeService.requireEnabled(tenantId, ShopBusinessType.REPURCHASE, member);
        DmsShopProduct product = productDao.selectById(id);
        if (product == null || !tenantId.equals(product.getTenantId()) || !Integer.valueOf(1).equals(product.getStatus())
                || !Integer.valueOf(1).equals(product.getRepurchaseSaleEnabled())) Asserts.fail("复购商品不存在或已下架");
        List<DmsShopSku> skus = skuDao.selectByProductId(id, 1);
        if (!isPvEnabled(tenantId)) {
            product.setRepurchasePv(ZERO);
            skus.forEach(sku -> sku.setRepurchasePv(ZERO));
        }
        product(product, true);
        skus.forEach(item -> sku(item, true));
        ShopProductDetailVO vo = new ShopProductDetailVO();
        vo.setProduct(product);
        vo.setSkus(skus);
        vo.setDisplayConfig(getDisplayConfig(tenantId));
        return vo;
    }

    @Override
    public CommonPage<DmsShopProduct> listProductPage(Long tenantId, String keyword, String categoryName,
                                                      Integer status, String stockStatus,
                                                      Integer pageNum, Integer pageSize) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        int resolvedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 12 : Math.min(pageSize, 100);
        String parameters = String.join("|",
                String.valueOf(keyword), String.valueOf(categoryName), "1",
                String.valueOf(stockStatus), String.valueOf(resolvedPageNum), String.valueOf(resolvedPageSize));
        String cacheKey = "products:" + DigestUtil.sha256Hex(parameters);
        return catalogCache.getPage(resolvedTenantId, cacheKey, DmsShopProduct.class, productCacheTtlSeconds, () -> {
            PageHelper.startPage(resolvedPageNum, resolvedPageSize);
            List<DmsShopProduct> products = productDao.selectFrontList(
                    resolvedTenantId, keyword, categoryName, 1, stockStatus);
            products.forEach(item -> product(item, false));
            return CommonPage.restPage(products);
        });
    }

    @Override
    public CommonPage<DmsShopProduct> listAdminProductPage(Long tenantId, String keyword, String categoryName,
                                                           Integer status, String stockStatus,
                                                           Integer pageNum, Integer pageSize) {
        int safePage = pageNum == null ? 1 : Math.max(1, pageNum);
        int safeSize = pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
        PageHelper.startPage(safePage, safeSize);
        return CommonPage.restPage(productDao.selectList(resolveTenantId(tenantId), keyword, categoryName, status,
                stockStatus, merchantProductReviewService.currentMerchantId()));
    }

    @Override
    public List<String> listCategories(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        return catalogCache.getList(resolvedTenantId, "category-names", String.class, categoryCacheTtlSeconds, () -> {
            List<DmsShopCategory> categories = categoryDao.selectList(resolvedTenantId, 1);
            return categories.isEmpty()
                    ? productDao.selectCategories(resolvedTenantId)
                    : categories.stream().map(DmsShopCategory::getCategoryName).toList();
        });
    }

    @Override
    public List<DmsShopCategory> listFrontCategories(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        return catalogCache.getList(resolvedTenantId, "front-categories", DmsShopCategory.class, categoryCacheTtlSeconds,
                () -> categoryDao.selectList(resolvedTenantId, 1));
    }

    @Override
    public List<DmsShopCategory> listAdminCategories(Long tenantId, Integer status) {
        return categoryDao.selectList(resolveTenantId(tenantId), status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopCategory saveCategory(DmsShopCategory category) {
        assertPlatformCategoryManagement();
        fillCategoryDefaults(category);
        assertTenantAccess(category.getTenantId());
        if (categoryDao.countByTenantId(category.getTenantId()) >= Math.max(1, maxCategories)) {
            Asserts.fail("商品分类已达到商城上限（" + Math.max(1, maxCategories) + "个），请先整理现有分类");
        }
        assertCategoryNameAvailable(category.getTenantId(), category.getCategoryName(), null);
        categoryDao.insert(category);
        catalogCache.invalidateAfterCommit(category.getTenantId());
        return categoryDao.selectById(category.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopCategory updateCategory(Long id, DmsShopCategory category) {
        assertPlatformCategoryManagement();
        DmsShopCategory exists = categoryDao.selectById(id);
        if (exists == null) {
            Asserts.fail("分类不存在");
        }
        assertTenantAccess(exists.getTenantId());
        category.setId(id);
        fillCategoryDefaults(category);
        category.setTenantId(exists.getTenantId());
        assertCategoryNameAvailable(category.getTenantId(), category.getCategoryName(), id);
        categoryDao.update(category);
        if (!exists.getCategoryName().equals(category.getCategoryName())) {
            // 商品表使用分类名称做前台筛选；分类改名必须在同一事务内同步商品，避免改名后分类变空。
            productDao.updateCategoryName(exists.getTenantId(), exists.getCategoryName(), category.getCategoryName());
        }
        catalogCache.invalidateAfterCommit(category.getTenantId());
        return categoryDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCategory(Long id) {
        assertPlatformCategoryManagement();
        DmsShopCategory category = categoryDao.selectById(id);
        if (category == null) {
            Asserts.fail("分类不存在或已被删除");
        }
        assertTenantAccess(category.getTenantId());
        int productCount = productDao.countByCategoryName(category.getTenantId(), category.getCategoryName());
        if (productCount > 0) {
            Asserts.fail("该分类下还有" + productCount + "个商品，请先修改这些商品的分类后再删除");
        }
        boolean deleted = categoryDao.deleteById(id) > 0;
        if (deleted) catalogCache.invalidateAfterCommit(category.getTenantId());
        return deleted;
    }

    @Override
    public boolean updateCategoryStatus(Long id, Integer status) {
        assertPlatformCategoryManagement();
        DmsShopCategory category = categoryDao.selectById(id);
        if (category == null) {
            Asserts.fail("分类不存在");
        }
        assertTenantAccess(category.getTenantId());
        boolean updated = categoryDao.updateStatus(id, status == null ? 1 : status) > 0;
        if (updated) catalogCache.invalidateAfterCommit(category.getTenantId());
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCategoryShowOnHome(Long id, Integer showOnHome) {
        assertPlatformCategoryManagement();
        DmsShopCategory category = categoryDao.selectById(id);
        if (category == null) {
            Asserts.fail("分类不存在");
        }
        assertTenantAccess(category.getTenantId());
        category.setShowOnHome(showOnHome == null ? 1 : showOnHome);
        boolean updated = categoryDao.update(category) > 0;
        if (updated) catalogCache.invalidateAfterCommit(category.getTenantId());
        return updated;
    }

    @Override
    public List<DmsShopBanner> listAdminBanners(Long tenantId, Integer status) {
        return bannerDao.selectList(resolveTenantId(tenantId), status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopBanner saveBanner(DmsShopBanner banner) {
        if (banner != null) banner.setTenantId(resolveTenantId(null));
        fillBannerDefaults(banner);
        assertTenantAccess(banner.getTenantId());
        bannerDao.insert(banner);
        catalogCache.invalidateAfterCommit(banner.getTenantId());
        return bannerDao.selectById(banner.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopBanner updateBanner(Long id, DmsShopBanner banner) {
        DmsShopBanner exists = bannerDao.selectById(id);
        if (exists == null) {
            Asserts.fail("轮播图不存在");
        }
        assertTenantAccess(exists.getTenantId());
        banner.setId(id);
        banner.setTenantId(exists.getTenantId());
        fillBannerDefaults(banner);
        bannerDao.update(banner);
        catalogCache.invalidateAfterCommit(banner.getTenantId());
        return bannerDao.selectById(id);
    }

    @Override
    public boolean updateBannerStatus(Long id, Integer status) {
        DmsShopBanner banner = bannerDao.selectById(id);
        if (banner == null) {
            Asserts.fail("轮播图不存在");
        }
        assertTenantAccess(banner.getTenantId());
        int nextStatus = status == null ? 1 : status;
        assertBrandCultureBannerCanBeEnabled(banner, nextStatus);
        boolean updated = bannerDao.updateStatus(id, nextStatus) > 0;
        if (updated) catalogCache.invalidateAfterCommit(banner.getTenantId());
        return updated;
    }

    @Override
    public List<DmsShopNotice> listAdminNotices(Long tenantId, Integer status) {
        return noticeDao.selectList(resolveTenantId(tenantId), status);
    }

    @Override
    public List<DmsShopNotice> listActiveNotices(Long tenantId) {
        return noticeDao.selectActive(resolveTenantId(tenantId));
    }

    @Override
    public DmsShopNotice getNotice(Long id) {
        DmsShopNotice notice = noticeDao.selectById(id);
        if (notice == null) {
            Asserts.fail("公告不存在");
        }
        // 验证公告状态和有效期
        if (!Integer.valueOf(1).equals(notice.getStatus())) {
            Asserts.fail("公告已禁用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (notice.getStartTime() != null && now.isBefore(notice.getStartTime())) {
            Asserts.fail("公告未开始");
        }
        if (notice.getEndTime() != null && now.isAfter(notice.getEndTime())) {
            Asserts.fail("公告已过期");
        }
        return notice;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopNotice saveNotice(DmsShopNotice notice) {
        fillNoticeDefaults(notice);
        assertTenantAccess(notice.getTenantId());
        noticeDao.insert(notice);
        catalogCache.invalidateAfterCommit(notice.getTenantId());
        return noticeDao.selectById(notice.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopNotice updateNotice(Long id, DmsShopNotice notice) {
        DmsShopNotice exists = noticeDao.selectById(id);
        if (exists == null) {
            Asserts.fail("公告不存在");
        }
        assertTenantAccess(exists.getTenantId());
        notice.setId(id);
        fillNoticeDefaults(notice);
        notice.setTenantId(exists.getTenantId());
        noticeDao.update(notice);
        catalogCache.invalidateAfterCommit(notice.getTenantId());
        return noticeDao.selectById(id);
    }

    @Override
    public boolean updateNoticeStatus(Long id, Integer status) {
        DmsShopNotice notice = noticeDao.selectById(id);
        if (notice == null) {
            Asserts.fail("公告不存在");
        }
        assertTenantAccess(notice.getTenantId());
        boolean updated = noticeDao.updateStatus(id, status == null ? 1 : status) > 0;
        if (updated) catalogCache.invalidateAfterCommit(notice.getTenantId());
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNotice(Long id) {
        DmsShopNotice notice = noticeDao.selectById(id);
        if (notice == null) {
            Asserts.fail("公告不存在或已删除");
        }
        assertTenantAccess(notice.getTenantId());
        boolean deleted = noticeDao.deleteById(id) > 0;
        if (deleted) catalogCache.invalidateAfterCommit(notice.getTenantId());
        return deleted;
    }

    @Override
    public DmsShopProduct getProduct(Long id) {
        DmsShopProduct product = productDao.selectById(id);
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(product.getTenantId());
        merchantProductReviewService.assertProductAccess(product);
        return product;
    }

    @Override
    public ShopProductDetailVO getProductDetail(Long id) {
        Long tenantId = resolveTenantId(null);
        return catalogCache.get(tenantId, "product:" + id, ShopProductDetailVO.class,
                productCacheTtlSeconds, () -> loadProductDetail(id));
    }

    private ShopProductDetailVO loadProductDetail(Long id) {
        DmsShopProduct product = getProduct(id);
        if (!Integer.valueOf(1).equals(product.getStatus()) || Integer.valueOf(0).equals(product.getNormalSaleEnabled())) {
            Asserts.fail("商品不存在或已下架");
        }
        requireActiveProductMerchant(product);
        // 兼容历史数据：PV 开关关闭时公开接口直接返回 0；开启时不返回高于售价的 PV。
        // SKU 的 PV 为 0 时继承商品默认 PV，避免旧后台把 SKU PV 强制保存为 0 后，
        // 前台显示有 PV、下单快照却记为 0。
        DmsTenantDisplayConfig displayConfig = getDisplayConfig(product.getTenantId());
        if (isEnabled(displayConfig.getShowPv())) {
            product.setPvValue(limitPvToSalePrice(product.getPvValue(), product.getSalePrice()));
        } else {
            product.setPvValue(ZERO);
        }
        List<DmsShopSku> skus = skuDao.selectByProductId(id, 1);
        for (DmsShopSku sku : skus) {
            sku.setPvValue(isEnabled(displayConfig.getShowPv())
                    ? resolveUnitPv(product, sku, sku.getSalePrice())
                    : ZERO);
        }
        product(product, false);
        skus.forEach(item -> sku(item, false));
        ShopProductDetailVO vo = new ShopProductDetailVO();
        vo.setProduct(product);
        vo.setSkus(skus);
        vo.setDisplayConfig(displayConfig);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopProduct saveProduct(DmsShopProduct product) {
        merchantProductReviewService.bindMerchantForWrite(product, null);
        applyServiceAddresses(product, true);
        fillProductDefaults(product);
        assertTenantAccess(product.getTenantId());
        merchantProductReviewService.prepareCreatedProduct(product);
        boolean settlementTermsChanged = product.getMerchantId() != null;
        requireSettlementCostAuthority(settlementTermsChanged, product.getSettlementCostChangeReason());
        productDao.insert(product);
        logSettlementCostChange(product.getId(), null, settlementSnapshot(product, Collections.emptyList()),
                product.getSettlementCostChangeReason(), settlementTermsChanged);
        catalogCache.invalidateAfterCommit(product.getTenantId());
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopProduct updateProduct(Long id, DmsShopProduct product) {
        DmsShopProduct exists = productDao.selectById(id);
        if (exists == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(exists.getTenantId());
        product.setId(id);
        product.setTenantId(exists.getTenantId());
        merchantProductReviewService.bindMerchantForWrite(product, exists);
        if (product.getShippingAddressId() == null) product.setShippingAddressId(exists.getShippingAddressId());
        if (product.getReturnAddressId() == null) product.setReturnAddressId(exists.getReturnAddressId());
        applyServiceAddresses(product, false);
        fillProductDefaults(product);
        merchantProductReviewService.prepareUpdatedProduct(exists, product);
        boolean settlementTermsChanged = settlementProductTermsChanged(exists, product);
        requireSettlementCostAuthority(settlementTermsChanged, product.getSettlementCostChangeReason());
        String beforePrice = productPriceSnapshot(exists, skuDao.selectByProductId(id, null));
        String beforeSettlement = settlementTermsChanged
                ? settlementSnapshot(exists, skuDao.selectByProductId(id, null)) : null;
        productDao.update(product);
        logSettlementCostChange(id, beforeSettlement,
                settlementSnapshot(product, skuDao.selectByProductId(id, null)),
                product.getSettlementCostChangeReason(), settlementTermsChanged);
        DmsShopProduct updated = productDao.selectById(id);
        logProductPriceChange(id, beforePrice, productPriceSnapshot(updated, skuDao.selectByProductId(id, null)));
        catalogCache.invalidateAfterCommit(product.getTenantId());
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopProduct publishProduct(Long id, ProductPublishDTO dto) {
        if (dto == null || dto.getProduct() == null) {
            Asserts.fail("商品信息不能为空");
        }
        DmsShopProduct product = dto.getProduct();
        DmsShopProduct existingProduct = id == null ? null : productDao.selectById(id);
        if (id != null && existingProduct == null) Asserts.fail("商品不存在");
        merchantProductReviewService.bindMerchantForWrite(product, existingProduct);
        List<DmsShopSku> existingSkus = id == null ? Collections.emptyList() : skuDao.selectByProductId(id, null);
        String beforePrice = existingProduct == null ? null : productPriceSnapshot(existingProduct, existingSkus);
        boolean settlementTermsChanged = settlementPublishTermsChanged(existingProduct, product, existingSkus, dto.getSkus());
        requireSettlementCostAuthority(settlementTermsChanged, product.getSettlementCostChangeReason());
        String beforeSettlement = settlementTermsChanged && existingProduct != null
                ? settlementSnapshot(existingProduct, existingSkus) : null;
        if (id == null) {
            applyServiceAddresses(product, true);
            fillProductDefaults(product);
            assertTenantAccess(product.getTenantId());
            merchantProductReviewService.prepareCreatedProduct(product);
            productDao.insert(product);
            id = product.getId();
        } else {
            DmsShopProduct existing = existingProduct;
            assertTenantAccess(existing.getTenantId());
            product.setId(id);
            product.setTenantId(existing.getTenantId());
            if (product.getShippingAddressId() == null) product.setShippingAddressId(existing.getShippingAddressId());
            if (product.getReturnAddressId() == null) product.setReturnAddressId(existing.getReturnAddressId());
            applyServiceAddresses(product, false);
            fillProductDefaults(product);
            merchantProductReviewService.prepareUpdatedProduct(existing, product);
            productDao.update(product);
        }

        for (ShopSkuDTO skuDTO : dto.getSkus() == null ? Collections.<ShopSkuDTO>emptyList() : dto.getSkus()) {
            skuDTO.setProductId(id);
            if (skuDTO.getId() == null) {
                skuDao.insert(toSku(skuDTO));
            } else {
                DmsShopSku existingSku = skuDao.selectById(skuDTO.getId());
                if (existingSku == null || !id.equals(existingSku.getProductId())) {
                    Asserts.fail("SKU不存在或不属于当前商品");
                }
                DmsShopSku sku = toSku(skuDTO);
                sku.setId(existingSku.getId());
                skuDao.update(sku);
            }
        }
        for (Long removedId : dto.getRemovedSkuIds() == null ? Collections.<Long>emptyList() : dto.getRemovedSkuIds()) {
            DmsShopSku removed = skuDao.selectById(removedId);
            if (removed != null && id.equals(removed.getProductId())) {
                skuDao.updateStatus(removedId, 0);
            }
        }
        if (dto.getSkus() != null && !dto.getSkus().isEmpty()) {
            List<DmsShopSku> activeSkus = skuDao.selectByProductId(id, 1);
            product.setSalePrice(activeSkus.stream().map(DmsShopSku::getSalePrice).filter(Objects::nonNull)
                    .min(BigDecimal::compareTo).orElse(ZERO));
            product.setMarketPrice(activeSkus.stream().map(DmsShopSku::getMarketPrice).filter(Objects::nonNull)
                    .filter(value -> value.compareTo(ZERO) > 0).min(BigDecimal::compareTo).orElse(ZERO));
            product.setCostAmount(activeSkus.stream().map(DmsShopSku::getCostAmount).filter(Objects::nonNull)
                    .min(BigDecimal::compareTo).orElse(ZERO));
            product.setStock(activeSkus.stream().map(DmsShopSku::getStock).filter(Objects::nonNull)
                    .mapToInt(value -> Math.max(0, value)).sum());
            if (isPvEnabled(product.getTenantId())) {
                validatePv(product.getPvValue(), product.getSalePrice(), "商品默认PV");
            } else {
                product.setPvValue(ZERO);
            }
            productDao.update(product);
        }
        logSettlementCostChange(id, beforeSettlement,
                settlementSnapshot(productDao.selectById(id), skuDao.selectByProductId(id, null)),
                product.getSettlementCostChangeReason(), settlementTermsChanged);
        DmsShopProduct published = productDao.selectById(id);
        logProductPriceChange(id, beforePrice, productPriceSnapshot(published, skuDao.selectByProductId(id, null)));
        catalogCache.invalidateAfterCommit(product.getTenantId());
        return published;
    }

    /** 校验商品只能使用所属商户自己的地址或平台明确共享地址；发货地址同时保存展示快照。 */
    private void applyServiceAddresses(DmsShopProduct product, boolean useDefaultWhenMissing) {
        Long tenantId = product.getTenantId() == null ? DEFAULT_TENANT_ID : product.getTenantId();
        DmsShopServiceAddress address = product.getShippingAddressId() == null && useDefaultWhenMissing
                ? serviceAddressDao.selectDefaultForMerchant(tenantId, product.getMerchantId(), 1)
                : product.getShippingAddressId() == null ? null : serviceAddressDao.selectById(product.getShippingAddressId());
        if (address != null) {
            requireAddressAccess(address, tenantId, product.getMerchantId(), 1, "发货");
            product.setShippingAddressId(address.getId());
            product.setDeliveryProvince(address.getProvince());
            product.setDeliveryCity(address.getCity());
            product.setDeliveryDistrict(address.getDistrict());
            product.setDeliveryAddress(joinServiceAddress(address));
        }
        DmsShopServiceAddress returnAddress = product.getReturnAddressId() == null && useDefaultWhenMissing
                ? serviceAddressDao.selectDefaultForMerchant(tenantId, product.getMerchantId(), 2)
                : product.getReturnAddressId() == null ? null : serviceAddressDao.selectById(product.getReturnAddressId());
        if (returnAddress != null) {
            requireAddressAccess(returnAddress, tenantId, product.getMerchantId(), 2, "退货");
            product.setReturnAddressId(returnAddress.getId());
        }
    }

    private void requireAddressAccess(DmsShopServiceAddress address, Long tenantId, Long merchantId,
                                      int addressType, String label) {
        boolean ownerMatches = Objects.equals(address.getMerchantId(), merchantId);
        boolean platformShared = merchantId != null && address.getMerchantId() == null
                && Integer.valueOf(1).equals(address.getSharedToMerchants());
        if (!tenantId.equals(address.getTenantId()) || !Integer.valueOf(addressType).equals(address.getAddressType())
                || !Integer.valueOf(1).equals(address.getStatus()) || (!ownerMatches && !platformShared)) {
            Asserts.fail(label + "地址不存在、已停用或不属于当前商户");
        }
    }

    private String joinServiceAddress(DmsShopServiceAddress address) {
        return java.util.stream.Stream.of(address.getProvince(), address.getCity(), address.getDistrict(), address.getDetailAddress())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProductStatus(Long id, Integer status) {
        DmsShopProduct product = productDao.selectByIdForUpdate(id);
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(product.getTenantId());
        merchantProductReviewService.assertProductAccess(product);
        int target = status == null ? 1 : status;
        if (target != 0 && target != 1) Asserts.fail("商品上下架状态不正确");
        if (product.getMerchantId() != null && target == 1
                && !"APPROVED".equals(product.getMerchantReviewStatus())) {
            Asserts.fail("商户商品必须审核通过后才能上架");
        }
        if (target == 1) requireActiveProductMerchant(product);
        if (Integer.valueOf(target).equals(product.getStatus())) return true;
        boolean updated = productDao.updateStatus(id, target) > 0;
        if (updated) catalogCache.invalidateAfterCommit(product.getTenantId());
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopProduct updateProductNewArrival(Long id, ProductNewArrivalDTO dto) {
        if (dto == null || dto.getEnabled() == null) Asserts.fail("请选择新品展示方式");
        DmsShopProduct product = productDao.selectByIdForUpdate(id);
        if (product == null) Asserts.fail("商品不存在");
        assertTenantAccess(product.getTenantId());
        merchantProductReviewService.assertProductAccess(product);

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        int enabled = Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0;
        if (enabled == 1) {
            if (!Integer.valueOf(1).equals(product.getStatus())
                    || !Integer.valueOf(1).equals(product.getNormalSaleEnabled())) {
                Asserts.fail("只有普通商城中已上架的商品才能额外加入新品");
            }
            Integer durationDays = dto.getDurationDays();
            if (durationDays == null || (durationDays != 0 && (durationDays < 30 || durationDays > 365))) {
                Asserts.fail("新品展示时间只能设置为30至365天，或选择永久");
            }
            startTime = LocalDateTime.now();
            endTime = durationDays == 0 ? null : startTime.plusDays(durationDays);
        }
        if (productDao.updateManualNewArrivalScoped(product.getTenantId(), id, enabled, startTime, endTime) != 1) {
            Asserts.fail("新品展示设置保存失败");
        }
        catalogCache.invalidateAfterCommit(product.getTenantId());
        operationLogService.log("SHOP_PRODUCT", "NEW_ARRIVAL_UPDATE", "SHOP_PRODUCT", String.valueOf(id),
                "enabled=" + product.getManualNewArrivalEnabled() + ";end=" + product.getManualNewArrivalEndTime(),
                "enabled=" + enabled + ";end=" + endTime,
                enabled == 1 ? "将商品加入新品速递" : "将商品移出运营新品");
        return productDao.selectById(id);
    }

    @Override
    public List<DmsShopSku> listSkus(Long productId, Integer status) {
        DmsShopProduct product = productDao.selectById(productId);
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(product.getTenantId());
        if (!Integer.valueOf(1).equals(product.getStatus()) || !Integer.valueOf(1).equals(product.getNormalSaleEnabled())) {
            Asserts.fail("商品不存在或已下架");
        }
        List<DmsShopSku> skus = skuDao.selectByProductId(productId, 1);
        skus.forEach(item -> sku(item, false));
        return skus;
    }

    @Override
    public List<DmsShopSku> listAdminSkus(Long productId, Integer status) {
        DmsShopProduct product = productDao.selectById(productId);
        if (product == null) Asserts.fail("商品不存在");
        assertTenantAccess(product.getTenantId());
        merchantProductReviewService.assertProductAccess(product);
        return skuDao.selectByProductId(productId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopSku saveSku(ShopSkuDTO dto) {
        DmsShopSku sku = toSku(dto);
        DmsShopProduct product = productDao.selectById(sku.getProductId());
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(product.getTenantId());
        requireAggregateMerchantSkuRoute(product);
        String beforePrice = productPriceSnapshot(product, skuDao.selectByProductId(product.getId(), null));
        skuDao.insert(sku);
        logProductPriceChange(product.getId(), beforePrice,
                productPriceSnapshot(product, skuDao.selectByProductId(product.getId(), null)));
        catalogCache.invalidateAfterCommit(product.getTenantId());
        return skuDao.selectById(sku.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopSku updateSku(Long id, ShopSkuDTO dto) {
        DmsShopSku exists = skuDao.selectById(id);
        if (exists == null) {
            Asserts.fail("SKU不存在");
        }
        DmsShopProduct product = productDao.selectById(exists.getProductId());
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(product.getTenantId());
        DmsShopSku sku = toSku(dto);
        sku.setId(id);
        sku.setProductId(exists.getProductId());
        requireAggregateMerchantSkuRoute(product);
        String beforePrice = productPriceSnapshot(product, skuDao.selectByProductId(product.getId(), null));
        skuDao.update(sku);
        logProductPriceChange(product.getId(), beforePrice,
                productPriceSnapshot(product, skuDao.selectByProductId(product.getId(), null)));
        catalogCache.invalidateAfterCommit(product.getTenantId());
        return skuDao.selectById(id);
    }

    @Override
    public boolean updateSkuStatus(Long id, Integer status) {
        DmsShopSku sku = skuDao.selectById(id);
        if (sku == null) {
            Asserts.fail("SKU不存在");
        }
        DmsShopProduct product = productDao.selectById(sku.getProductId());
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(product.getTenantId());
        requireAggregateMerchantSkuRoute(product);
        boolean updated = skuDao.updateStatus(id, status == null ? 1 : status) > 0;
        if (updated) catalogCache.invalidateAfterCommit(product.getTenantId());
        return updated;
    }

    @Override
    public List<DmsFreightTemplate> listFreightTemplates(Long tenantId, Integer status) {
        return freightTemplateDao.selectList(resolveTenantId(null), status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsFreightTemplate saveFreightTemplate(FreightTemplateSaveDTO dto) {
        DmsFreightTemplate template = toFreightTemplate(null, dto);
        freightTemplateDao.insert(template);
        return freightTemplateDao.selectById(template.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsFreightTemplate updateFreightTemplate(Long id, FreightTemplateSaveDTO dto) {
        DmsFreightTemplate existing = freightTemplateDao.selectById(id);
        if (existing == null) Asserts.fail("运费模板不存在");
        assertTenantAccess(existing.getTenantId());
        DmsFreightTemplate template = toFreightTemplate(id, dto);
        template.setTenantId(existing.getTenantId());
        if (Integer.valueOf(1).equals(existing.getStatus()) && Integer.valueOf(0).equals(template.getStatus())
                && productDao.countByFreightTemplateId(existing.getTenantId(), existing.getId()) > 0) {
            Asserts.fail("该运费模板仍被商品使用，请先将关联商品下架并切换配送方式后再停用");
        }
        freightTemplateDao.update(template);
        return freightTemplateDao.selectById(id);
    }

    @Override
    public FreightQuoteVO quoteFreight(ShopOrderSubmitDTO dto, DmsShopMember member) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            Asserts.fail("订单商品不能为空");
        }
        if (member != null) dto.setUserId(member.getUserId());
        String businessType = businessModeService.normalizeType(dto.getBusinessType());
        Long tenantId = resolveTenantId(null);
        businessModeService.requireEnabled(tenantId, businessType, member);
        DmsFlashSaleActivity flashActivity = null;
        if (ShopBusinessType.FLASH_SALE.equals(businessType)) {
            flashActivity = dto.getBusinessSourceId() == null ? null : flashSaleActivityDao.selectById(dto.getBusinessSourceId());
            if (flashActivity == null || !tenantId.equals(flashActivity.getTenantId())) Asserts.fail("秒杀活动不存在");
        }
        fillAddress(dto, member);
        Map<String, Map<Long, ProductShippingContext>> shippingByMerchant = new LinkedHashMap<>();
        Map<Long, Integer> requestedPurchaseQuantities = new HashMap<>();
        Map<Long, Integer> existingPurchaseQuantities = new HashMap<>();
        BigDecimal productAmount = ZERO;
        for (ShopOrderItemDTO item : dto.getItems()) {
            if (item.getProductId() == null) Asserts.fail("商品ID不能为空");
            int quantity = item.getQuantity() == null || item.getQuantity() <= 0 ? 1 : item.getQuantity();
            DmsShopProduct product = productDao.selectById(item.getProductId());
            if (product == null || !Integer.valueOf(1).equals(product.getStatus())) Asserts.fail("商品不存在或已下架");
            assertTenantAccess(product.getTenantId());
            int requestedQuantity = requestedPurchaseQuantities.merge(product.getId(), quantity, Integer::sum);
            validateBusinessProduct(businessType, product, dto.getUserId(), requestedQuantity,
                    existingPurchaseQuantities, flashActivity, item);
            requireSkuSelection(product, item.getSkuId());
            DmsShopSku sku = null;
            if (item.getSkuId() != null) {
                sku = skuDao.selectById(item.getSkuId());
                if (sku == null || !product.getId().equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                    Asserts.fail("SKU不存在或已下架：" + product.getProductName());
                }
            }
            BigDecimal price = resolveBusinessPrice(businessType, product, sku, flashActivity);
            BigDecimal lineAmount = price.multiply(BigDecimal.valueOf(quantity));
            productAmount = productAmount.add(lineAmount);
            String merchantKey = product.getMerchantId() == null ? "PLATFORM" : "MERCHANT:" + product.getMerchantId();
            Map<Long, ProductShippingContext> merchantProducts = shippingByMerchant.computeIfAbsent(
                    merchantKey, ignored -> new LinkedHashMap<>());
            mergeShippingProduct(merchantProducts, product, lineAmount);
        }
        BigDecimal freight = shippingByMerchant.values().stream()
                .map(group -> calculateFreight(group, dto))
                .reduce(ZERO, BigDecimal::add);
        return new FreightQuoteVO(productAmount, freight, productAmount.add(freight));
    }

    @Override
    public PurchaseLimitCheckVO checkPurchaseLimit(Long productId, Integer quantity, DmsShopMember member) {
        if (productId == null) {
            Asserts.fail("商品ID不能为空");
        }
        if (member == null || member.getUserId() == null) {
            Asserts.unauthorized("请先登录后再加入购物车");
        }
        DmsShopProduct product = productDao.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            Asserts.fail("商品不存在或已下架");
        }
        assertTenantAccess(product.getTenantId());

        int requestedQuantity = quantity == null || quantity <= 0 ? 1 : quantity;
        int limit = product.getPurchaseLimit() == null ? 0 : product.getPurchaseLimit();
        int purchasedQuantity = orderItemDao.sumQuantityByUserAndProduct(member.getUserId(), product.getId(),
                product.getTenantId() == null ? DEFAULT_TENANT_ID : product.getTenantId());
        if (limit <= 0) {
            return new PurchaseLimitCheckVO(true, 0, purchasedQuantity, null, product.getProductName(), null);
        }

        int remainingQuantity = Math.max(0, limit - purchasedQuantity);
        boolean allowed = requestedQuantity <= remainingQuantity;
        String message = allowed ? null : purchaseLimitMessage(product.getProductName(), limit, remainingQuantity);
        return new PurchaseLimitCheckVO(allowed, limit, purchasedQuantity, remainingQuantity,
                product.getProductName(), message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderVO submitOrder(ShopOrderSubmitDTO dto) {
        return submitOrder(dto, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderVO submitOrder(ShopOrderSubmitDTO dto, DmsShopMember member) {
        String businessType = businessModeService.normalizeType(dto == null ? null : dto.getBusinessType());
        if (ShopBusinessType.FLASH_SALE.equals(businessType)) {
            Asserts.fail("秒杀订单必须从秒杀活动入口提交");
        }
        member = prepareOrderSubmit(dto, member);
        return createCheckout(dto, member, businessType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderVO submitReservedFlashSaleOrder(ShopOrderSubmitDTO dto, DmsShopMember member) {
        String businessType = businessModeService.normalizeType(dto == null ? null : dto.getBusinessType());
        if (!ShopBusinessType.FLASH_SALE.equals(businessType)) Asserts.fail("秒杀订单业务类型不正确");
        member = prepareOrderSubmit(dto, member);
        return createOrder(dto, member, businessType, null, null, true);
    }

    private DmsShopMember prepareOrderSubmit(ShopOrderSubmitDTO dto, DmsShopMember member) {
        if (member != null) {
            DmsShopMember lockedMember = memberDao.selectByIdForUpdate(member.getId());
            if (lockedMember == null || !Integer.valueOf(1).equals(lockedMember.getStatus())) {
                Asserts.fail("登录账号不可用");
            }
            member = lockedMember;
            dto.setUserId(lockedMember.getUserId());
        }
        fillAddress(dto, member);
        normalizeManualAddress(dto);
        validateSubmit(dto);
        return member;
    }

    private ShopOrderVO createCheckout(ShopOrderSubmitDTO dto, DmsShopMember member, String businessType) {
        LinkedHashMap<String, List<ShopOrderItemDTO>> merchantGroups = groupSubmitItemsByMerchant(dto.getItems());
        if (merchantGroups.size() <= 1) {
            return createOrder(dto, member, businessType, null, null, true);
        }
        if (!ShopBusinessType.NORMAL.equals(businessType) && !ShopBusinessType.REPURCHASE.equals(businessType)) {
            Asserts.fail("当前活动订单不能跨商户合并支付");
        }

        LocalDateTime now = LocalDateTime.now();
        Long tradeId = IdUtil.getSnowflakeNextId();
        String tradeNo = generateTradeNo(tradeId, now);
        List<ShopOrderVO> children = new ArrayList<>();
        BigDecimal totalAmount = ZERO;
        BigDecimal freightAmount = ZERO;
        BigDecimal discountAmount = ZERO;
        BigDecimal payAmount = ZERO;
        for (List<ShopOrderItemDTO> group : merchantGroups.values()) {
            ShopOrderVO child = createOrder(copySubmit(dto, group), member, businessType, tradeId, tradeNo, false);
            children.add(child);
            totalAmount = totalAmount.add(money(child.getOrder().getTotalAmount()));
            freightAmount = freightAmount.add(money(child.getOrder().getFreightAmount()));
            discountAmount = discountAmount.add(money(child.getOrder().getDiscountAmount()));
            payAmount = payAmount.add(money(child.getOrder().getPayAmount()));
        }
        paymentVerificationService.verifyIfRequired(member, payAmount, dto.getSmsCode());

        DmsShopTrade trade = new DmsShopTrade();
        trade.setId(tradeId);
        trade.setTradeNo(tradeNo);
        trade.setTenantId(children.get(0).getOrder().getTenantId());
        trade.setUserId(children.get(0).getOrder().getUserId());
        trade.setPayType(children.get(0).getOrder().getPayType());
        trade.setTotalAmount(money(totalAmount));
        trade.setFreightAmount(money(freightAmount));
        trade.setDiscountAmount(money(discountAmount));
        trade.setPayAmount(money(payAmount));
        trade.setStatus(0);
        if (tradeDao.insert(trade) != 1) Asserts.fail("交易父单创建失败");

        ShopOrderVO result = children.get(0);
        result.setCheckoutId(tradeId);
        result.setCheckoutNo(tradeNo);
        result.setGroupedCheckout(true);
        result.setChildOrders(children);
        return result;
    }

    private ShopOrderVO createOrder(ShopOrderSubmitDTO dto, DmsShopMember member, String businessType,
                                    Long tradeId, String tradeNo, boolean verifyPayment) {
        DmsAgent ownerAgent = resolveOwnerAgent(dto);
        LocalDateTime now = LocalDateTime.now();
        Long orderId = IdUtil.getSnowflakeNextId();
        String orderNo = ShopOrderNoGenerator.generate(orderId, now);

        List<DmsShopOrderItem> orderItems = new ArrayList<>();
        Map<Long, ProductShippingContext> shippingProducts = new LinkedHashMap<>();
        BigDecimal totalAmount = ZERO;
        BigDecimal totalPv = ZERO;
        BigDecimal totalCost = ZERO;
        Long tenantId = resolveTenantId(null);
        businessModeService.requireEnabled(tenantId, businessType, member);
        DmsFlashSaleActivity flashActivity = null;
        if (ShopBusinessType.FLASH_SALE.equals(businessType)) {
            flashActivity = dto.getBusinessSourceId() == null ? null : flashSaleActivityDao.selectById(dto.getBusinessSourceId());
            if (flashActivity == null || !tenantId.equals(flashActivity.getTenantId())) Asserts.fail("秒杀活动不存在");
        }
        Map<Long, Integer> requestedPurchaseQuantities = new HashMap<>();
        Map<Long, Integer> existingPurchaseQuantities = new HashMap<>();
        boolean merchantResolved = false;
        Long orderMerchantId = null;
        String orderMerchantName = null;

        for (ShopOrderItemDTO itemDTO : dto.getItems()) {
            Integer quantity = itemDTO.getQuantity() == null || itemDTO.getQuantity() <= 0 ? 1 : itemDTO.getQuantity();
            DmsShopProduct product = productDao.selectById(itemDTO.getProductId());
            if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
                Asserts.fail("商品不存在或已下架");
            }
            assertTenantAccess(product.getTenantId());
            int requestedQuantity = requestedPurchaseQuantities.merge(product.getId(), quantity, Integer::sum);
            validateBusinessProduct(businessType, product, dto.getUserId(), requestedQuantity,
                    existingPurchaseQuantities, flashActivity, itemDTO);
            if ("CUSTOM".equals(normalizeTeamBonusMode(product))) {
                Asserts.fail("该商品使用客户定制奖金制度，制度未配置完成前不能下单");
            }
            if (!merchantResolved) {
                merchantResolved = true;
                orderMerchantId = product.getMerchantId();
                orderMerchantName = product.getMerchantName();
            } else if (!Objects.equals(orderMerchantId, product.getMerchantId())) {
                Asserts.fail("不同商户或平台自营商品请分开结算");
            }
            requireSkuSelection(product, itemDTO.getSkuId());
            DmsShopSku sku = null;
            if (itemDTO.getSkuId() != null) {
                sku = skuDao.selectById(itemDTO.getSkuId());
                if (sku == null || !product.getId().equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                    Asserts.fail("SKU不存在或已下架：" + product.getProductName());
                }
                if (sku.getStock() == null || sku.getStock() < quantity || skuDao.decreaseStock(sku.getId(), quantity) <= 0) {
                    Asserts.fail("SKU库存不足：" + sku.getSkuName());
                }
                if (productDao.decreaseStock(product.getId(), quantity) <= 0) {
                    skuDao.increaseStock(sku.getId(), quantity);
                    Asserts.fail("商品库存不足：" + product.getProductName());
                }
            } else {
                if (product.getStock() == null || product.getStock() < quantity) {
                    Asserts.fail("商品库存不足：" + product.getProductName());
                }
                if (productDao.decreaseStock(product.getId(), quantity) <= 0) {
                    Asserts.fail("商品库存不足：" + product.getProductName());
                }
            }
            tenantId = product.getTenantId() == null ? DEFAULT_TENANT_ID : product.getTenantId();

            BigDecimal price = resolveBusinessPrice(businessType, product, sku, flashActivity);
            // 金额、PV、库存全部以服务端实时商品数据为准，客户端传值不参与计算。
            // 旧数据即使存在 PV 大于售价，也会在这里被强制限制，不能进入订单快照。
            BigDecimal pv = resolveBusinessPv(businessType, product, sku, flashActivity, price);
            BigDecimal cost = sku == null ? money(product.getCostAmount()) : money(sku.getCostAmount());
            BigDecimal itemAmount = price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal itemPv = pv.multiply(BigDecimal.valueOf(quantity));
            BigDecimal itemCost = cost.multiply(BigDecimal.valueOf(quantity));
            totalAmount = totalAmount.add(itemAmount);
            totalPv = totalPv.add(itemPv);
            totalCost = totalCost.add(itemCost);
            mergeShippingProduct(shippingProducts, product, itemAmount);

            DmsShopOrderItem orderItem = new DmsShopOrderItem();
            orderItem.setOrderId(orderId);
            orderItem.setOrderNo(orderNo);
            orderItem.setMerchantId(product.getMerchantId());
            orderItem.setMerchantName(product.getMerchantName());
            orderItem.setProductId(product.getId());
            orderItem.setSkuId(sku == null ? null : sku.getId());
            orderItem.setProductName(product.getProductName());
            orderItem.setSkuName(sku == null ? null : sku.getSkuName());
            orderItem.setSkuAttrs(sku == null ? null : sku.getAttrsJson());
            orderItem.setProductCover(product.getCoverUrl());
            orderItem.setPrice(price);
            orderItem.setQuantity(quantity);
            orderItem.setTotalAmount(itemAmount);
            orderItem.setPvValue(pv);
            orderItem.setTotalPv(itemPv);
            orderItem.setCostAmount(cost);
            orderItem.setTotalCost(itemCost);
            orderItem.setSettlementDelayDays(resolveSettlementDelayDays(product));
            orderItem.setTeamBonusMode(normalizeTeamBonusMode(product));
            orderItems.add(orderItem);
        }

        BigDecimal freightAmount = calculateFreight(shippingProducts, dto);
        BigDecimal payAmount = totalAmount.add(freightAmount);
        if (verifyPayment) paymentVerificationService.verifyIfRequired(member, payAmount, dto.getSmsCode());

        DmsShopOrder order = new DmsShopOrder();
        order.setId(orderId);
        order.setOrderNo(orderNo);
        order.setTradeId(tradeId);
        order.setTradeNo(tradeNo);
        order.setPaymentOrderNo(tradeNo == null ? orderNo : tradeNo);
        order.setTenantId(tenantId);
        order.setMerchantId(orderMerchantId);
        order.setMerchantName(orderMerchantName);
        order.setUserId(resolveOrderUserId(dto, ownerAgent));
        order.setAgentId(ownerAgent == null ? null : ownerAgent.getId());
        order.setInviteCode(dto.getInviteCode());
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setReceiverProvince(dto.getReceiverProvince());
        order.setReceiverCity(dto.getReceiverCity());
        order.setReceiverDistrict(dto.getReceiverDistrict());
        order.setReceiverDetailAddress(dto.getReceiverDetailAddress());
        order.setTotalAmount(totalAmount);
        order.setFreightAmount(freightAmount);
        order.setDiscountAmount(ZERO);
        order.setPayAmount(payAmount);
        order.setTotalPv(totalPv);
        order.setTotalCost(totalCost);
        order.setBusinessType(businessType);
        order.setBusinessSourceId(dto.getBusinessSourceId());
        order.setSourceLiveRoomId(liveRoomService.resolveRecentAttribution(tenantId, order.getUserId(),
                orderItems.stream().map(DmsShopOrderItem::getProductId).distinct().toList()));
        order.setStatus(0); // 待支付，支付回调后改为1
        String payType = dto.getPayType() == null || dto.getPayType().isBlank()
                ? "ALIPAY" : dto.getPayType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("WECHAT", "ALIPAY", "BALANCE").contains(payType)) {
            Asserts.fail("支付方式不正确");
        }
        order.setPayType(payType);
        order.setRemark(dto.getRemark());
        order.setPayTime(null); // 支付回调后设置
        orderDao.insert(order);

        for (DmsShopOrderItem item : orderItems) {
            orderItemDao.insert(item);
            DmsOrderPvDetail pvDetail = new DmsOrderPvDetail();
            pvDetail.setTenantId(tenantId);
            pvDetail.setOrderId(orderId);
            pvDetail.setOrderNo(orderNo);
            pvDetail.setProductId(item.getProductId());
            pvDetail.setSkuId(item.getSkuId());
            pvDetail.setProductName(item.getProductName());
            pvDetail.setQuantity(item.getQuantity());
            pvDetail.setPayAmount(item.getTotalAmount());
            pvDetail.setPvValue(item.getPvValue());
            pvDetail.setTotalPv(item.getTotalPv());
            pvDetail.setBvValue(ZERO);
            pvDetail.setTotalBv(ZERO);
            pvDetail.setCostAmount(item.getCostAmount());
            pvDetail.setTotalCost(item.getTotalCost());
            orderPvDetailDao.insert(pvDetail);
        }

        OrderFinanceDTO financeDTO = new OrderFinanceDTO();
        financeDTO.setOrderId(orderId);
        financeDTO.setOrderNo(orderNo);
        financeDTO.setPayAmount(payAmount);
        financeDTO.setProductCost(totalCost);
        financeDTO.setRemark(switch (businessType) {
            case ShopBusinessType.FLASH_SALE -> "秒杀订单";
            case ShopBusinessType.REPURCHASE -> "复购商城订单";
            default -> "商城前台订单";
        });
        OrderFinanceVO finance = auditService.upsertOrderFinance(financeDTO);

        ShopOrderVO vo = new ShopOrderVO();
        vo.setOrder(orderDao.selectById(orderId));
        fillMemberAccount(vo, vo.getOrder());
        vo.setItems(orderItemDao.selectByOrderId(orderId));
        vo.setShipments(Collections.emptyList());
        vo.setFinance(finance);
        vo.setAfterSales(Collections.emptyList());
        vo.setPendingReviewCount(0);
        fillAfterSaleWindow(vo, vo.getOrder());
        vo.setDisplayConfig(getDisplayConfig(tenantId));
        catalogCache.invalidateAfterCommit(tenantId);
        return vo;
    }

    @Override
    public ShopOrderVO getOrder(Long orderId) {
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        assertMerchantOrderAccess(order, false);
        ShopOrderVO vo = new ShopOrderVO();
        vo.setOrder(order);
        fillMemberAccount(vo, order);
        vo.setItems(orderItemDao.selectByOrderId(orderId));
        fillShipments(vo, order);
        if (currentAdminMerchantId() == null) {
            vo.setFinance(auditService.getOrderFinanceDetail(orderId).getFinance());
        } else {
            vo.setMerchantFulfillmentAllowed(currentMerchantFulfillmentAllowed());
        }
        vo.setAfterSales(hydrateAfterSales(afterSaleDao.selectByOrderId(orderId)));
        vo.setPendingReviewCount(pendingReviewCount(order));
        fillAfterSaleWindow(vo, order);
        vo.setDisplayConfig(getDisplayConfig(order.getTenantId()));
        return vo;
    }

    @Override
    public List<ShopOrderVO> listOrders(Long userId, Long agentId) {
        return listOrders(userId, agentId, null);
    }

    @Override
    public List<ShopOrderVO> listOrders(Long userId, Long agentId, String orderState) {
        List<DmsShopOrder> orders;
        if (agentId != null) {
            orders = orderDao.selectByAgentId(agentId);
        } else if (userId != null) {
            orders = orderDao.selectByUserIdAndState(userId, normalizeFrontOrderState(orderState));
        } else {
            orders = orderDao.selectList(resolveTenantId(null), null, null, null, currentAdminMerchantId());
        }
        return orders.stream().filter(this::canAccessOrder).map(order -> {
            ShopOrderVO vo = new ShopOrderVO();
            vo.setOrder(order);
            fillMemberAccount(vo, order);
            vo.setItems(orderItemDao.selectByOrderId(order.getId()));
            fillShipments(vo, order);
            vo.setFinance(auditService.getOrderFinanceDetail(order.getId()).getFinance());
            vo.setAfterSales(hydrateAfterSales(afterSaleDao.selectByOrderId(order.getId())));
            vo.setPendingReviewCount(pendingReviewCount(order));
            fillAfterSaleWindow(vo, order);
            vo.setDisplayConfig(getDisplayConfig(order.getTenantId()));
            return vo;
        }).toList();
    }

    private String normalizeFrontOrderState(String orderState) {
        if (orderState == null || orderState.isBlank() || "ALL".equalsIgnoreCase(orderState)) return null;
        String normalized = orderState.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("PENDING_PAYMENT", "PENDING_SHIPMENT", "PENDING_RECEIPT", "PENDING_REVIEW", "AFTER_SALE")
                .contains(normalized)) {
            Asserts.fail("订单状态筛选条件不正确");
        }
        return normalized;
    }

    @Override
    public List<ShopOrderVO> listAdminOrders(String keyword, Integer status, String orderState) {
        boolean merchantWorkspace = currentAdminMerchantId() != null;
        boolean merchantFulfillmentAllowed = !merchantWorkspace || currentMerchantFulfillmentAllowed();
        return orderDao.selectList(resolveTenantId(null), keyword, status, normalizeAdminOrderState(orderState), currentAdminMerchantId()).stream().filter(this::canAccessOrder).map(order -> {
            ShopOrderVO vo = new ShopOrderVO();
            vo.setOrder(order);
            vo.setServiceRemark(order.getServiceRemark());
            fillMemberAccount(vo, order);
            vo.setItems(orderItemDao.selectByOrderId(order.getId()));
            fillShipments(vo, order);
            if (!merchantWorkspace) vo.setFinance(auditService.getOrderFinanceDetail(order.getId()).getFinance());
            if (merchantWorkspace) vo.setMerchantFulfillmentAllowed(merchantFulfillmentAllowed);
            vo.setAfterSales(hydrateAfterSales(afterSaleDao.selectByOrderId(order.getId())));
            vo.setPendingReviewCount(pendingReviewCount(order));
            fillAfterSaleWindow(vo, order);
            vo.setDisplayConfig(getDisplayConfig(order.getTenantId()));
            return vo;
        }).toList();
    }

    @Override
    public ShopOrderStatusSummaryVO getAdminOrderWorkSummary() {
        ShopOrderStatusSummaryVO summary = orderDao.selectAdminWorkSummary(resolveTenantId(null), currentAdminMerchantId());
        return summary == null ? new ShopOrderStatusSummaryVO() : summary;
    }

    @Override
    public ShopTradeDetailVO getAdminTrade(Long tradeId) {
        if (currentAdminMerchantId() != null) {
            Asserts.fail("商户只能查看自己的履约子订单，不能查看平台联合支付汇总");
        }
        if (tradeId == null) Asserts.fail("联合支付ID不能为空");
        DmsShopTrade trade = tradeDao.selectById(tradeId);
        if (trade == null) Asserts.fail("联合支付不存在");
        assertTenantAccess(trade.getTenantId());
        List<ShopOrderVO> children = orderDao.selectByTradeId(tradeId).stream()
                .map(order -> getOrder(order.getId()))
                .toList();
        if (children.isEmpty()) Asserts.fail("联合支付没有履约子订单");

        ShopTradeDetailVO detail = new ShopTradeDetailVO();
        detail.setTrade(trade);
        detail.setChildOrders(children);
        detail.setChildCount(children.size());
        detail.setChildPayAmount(children.stream()
                .map(ShopOrderVO::getOrder)
                .filter(Objects::nonNull)
                .map(DmsShopOrder::getPayAmount)
                .map(this::money)
                .reduce(ZERO, BigDecimal::add));
        detail.setRefundedAmount(children.stream()
                .flatMap(child -> child.getAfterSales() == null ? java.util.stream.Stream.empty() : child.getAfterSales().stream())
                .filter(sale -> Integer.valueOf(1).equals(sale.getStatus()))
                .map(DmsShopAfterSale::getRefundAmount)
                .map(this::money)
                .reduce(ZERO, BigDecimal::add));
        return detail;
    }

    private String normalizeAdminOrderState(String orderState) {
        if (orderState == null || orderState.isBlank()) return null;
        String normalized = orderState.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("PENDING_PAYMENT", "PENDING_SHIPMENT", "AFTER_SALE", "COMPLETED", "REFUNDED")
                .contains(normalized)) {
            Asserts.fail("订单状态筛选条件不正确");
        }
        return normalized;
    }

    private List<DmsShopAfterSale> hydrateAfterSales(List<DmsShopAfterSale> afterSales) {
        for (DmsShopAfterSale afterSale : afterSales) {
            afterSale.setItems(afterSaleItemDao.selectByAfterSaleId(afterSale.getId()));
            DmsShopMember member = memberDao.selectByUserId(afterSale.getUserId());
            afterSale.setMemberAccount(MemberAccountUtils.display(member));
        }
        return afterSales;
    }

    private void fillMemberAccount(ShopOrderVO vo, DmsShopOrder order) {
        if (vo == null || order == null) return;
        DmsShopMember member = memberDao.selectByUserId(order.getUserId());
        vo.setMemberAccount(MemberAccountUtils.display(member));
    }

    private void fillAfterSaleWindow(ShopOrderVO vo, DmsShopOrder order) {
        if (vo == null || order == null) return;
        ShopAfterSaleWindowPolicy.Window window = afterSaleWindowPolicy.resolve(order.getTenantId());
        vo.setAfterSaleWindowMode(window.mode());
        vo.setAfterSaleWindowDays(window.days());
        vo.setAfterSaleWindowLabel(afterSaleWindowPolicy.label(window));
        vo.setAfterSaleDeadline(afterSaleWindowPolicy.deadline(order, window));
        vo.setAfterSaleSelfServiceEnabled(window.days() > 0);
    }

    /**
     * 新订单读取包裹明细；历史订单在迁移数据尚未回填时仍从订单旧字段生成一条兼容记录。
     */
    private void fillShipments(ShopOrderVO vo, DmsShopOrder order) {
        if (vo == null || order == null || order.getId() == null) return;
        List<DmsShopOrderShipment> shipments = orderShipmentDao.selectByOrderId(order.getId());
        if ((shipments == null || shipments.isEmpty())
                && order.getDeliveryNo() != null && !order.getDeliveryNo().isBlank()) {
            DmsShopOrderShipment legacy = new DmsShopOrderShipment();
            legacy.setTenantId(order.getTenantId());
            legacy.setOrderId(order.getId());
            legacy.setOrderNo(order.getOrderNo());
            legacy.setDeliveryCompany(order.getDeliveryCompany());
            legacy.setDeliveryNo(order.getDeliveryNo());
            legacy.setShipmentQuantity(vo.getItems() == null ? 0 : vo.getItems().stream()
                    .filter(Objects::nonNull)
                    .map(DmsShopOrderItem::getQuantity)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum());
            legacy.setDeliveryTime(order.getDeliveryTime());
            legacy.setSource("LEGACY");
            shipments = List.of(legacy);
        }
        vo.setShipments(shipments == null ? Collections.emptyList() : shipments);
    }

    private int pendingReviewCount(DmsShopOrder order) {
        if (order == null || !Integer.valueOf(3).equals(order.getStatus()) || order.getUserId() == null) {
            return 0;
        }
        return productReviewDao.countUnreviewedByOrderId(order.getUserId(), order.getId(), order.getTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderVO markOrderPaid(Long orderId, String payType) {
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        if (Integer.valueOf(1).equals(order.getStatus())
                || Integer.valueOf(2).equals(order.getStatus())
                || Integer.valueOf(3).equals(order.getStatus())) {
            return getOrder(orderId);
        }
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            Asserts.fail("当前订单状态不能支付");
        }
        merchantService.assertOrderCanBePaid(orderId);

        int updated = orderDao.markPaid(orderId, payType);
        DmsTenant tenant = tenantDao.selectById(order.getTenantId());
        // 兼容少量只构造旧依赖集合的单元测试；生产环境由Spring完整注入。
        List<DmsShopOrderItem> paidItems = orderItemDao.selectByOrderId(order.getId());
        boolean inheritedBonus = paidItems.isEmpty() || paidItems.stream().anyMatch(item ->
                item.getTeamBonusMode() == null || item.getTeamBonusMode().isBlank()
                        || "INHERIT".equals(item.getTeamBonusMode()));
        boolean explicitStandardBonus = paidItems.stream().anyMatch(item -> "STANDARD".equals(item.getTeamBonusMode()));
        boolean standardBonus = explicitStandardBonus || (inheritedBonus && (businessModeService == null
                ? order.getBusinessType() == null || ShopBusinessType.NORMAL.equals(order.getBusinessType())
                : businessModeService.usesStandardBonus(tenant, order.getBusinessType())));
        DmsShopMember payingMember = order.getUserId() == null ? null : memberDao.selectByUserId(order.getUserId());
        // 旧数据字段为空时继续兼容原一体化商城；新公开商城账号明确写0，不进入团队奖金链路。
        boolean teamParticipant = payingMember == null || !Integer.valueOf(0).equals(payingMember.getTeamOptIn());
        boolean teamBonusEligible = standardBonus && teamParticipant;
        if (updated > 0) {
            // 只有已在团队H5主动加入团队业务的账号，首笔有效支付后才进入奖金体系。
            if (teamBonusEligible && order.getUserId() != null) {
                com.macro.mall.distribution.vo.AgentInfoVO activated = authService.activateMember(
                        order.getUserId(), 1, "完成首笔有效支付订单，成为会员，订单：" + order.getOrderNo());
                order.setAgentId(activated.getId());
                orderDao.updateAgentId(orderId, activated.getId());
            }
            if (teamBonusEligible) {
                // 只有采用标准奖金规则的订单才冻结关系并进入业绩奖金链路。
                relationSnapshotService.capture(order);
            }
        }
        if (updated > 0 && teamBonusEligible) {
            DmsAgent agent = agentDao.selectByUserId(order.getUserId());
            if (agent != null) {
                LocalDateTime paidTime = LocalDateTime.now();
                // 运费只计入订单实付和财务，不计入业绩、累计单量金额或奖金基数。
                BigDecimal bonusBaseAmount = productBonusBase(order, paidItems);
                int bonusQuantity = paidItems.isEmpty() ? 1 : paidItems.stream()
                        .filter(this::isBonusEligibleItem)
                        .map(DmsShopOrderItem::getQuantity).filter(java.util.Objects::nonNull)
                        .mapToInt(Integer::intValue).sum();
                performanceService.recordOrderPerformance(
                        order.getId(), order.getOrderNo(), bonusBaseAmount, Math.max(1, bonusQuantity),
                        agent.getUserId(), paidTime);
                commissionService.calculateAndRecordCommission(
                        order.getTenantId(), order.getId(), order.getOrderNo(), bonusBaseAmount,
                        agent.getUserId(), agent.getAgentName());
            }
        }
        if (updated > 0) {
            // 所有支付订单都生成财务口径；商户成本进入商户货款账户，平台只归集剩余商品款。
            auditService.refreshOrderFinance(order.getId(), order.getOrderNo(), order.getPayAmount());
            orderBalanceAllocationService.prepareForOrder(order.getId());
        }
        if (updated > 0) {
            merchantService.createOrderSettlements(order.getId());
            if (ShopBusinessType.FLASH_SALE.equals(order.getBusinessType())) {
                flashSaleReservationDao.updateStatusByOrder(orderId, "PAID");
            }
            erpIntegrationService.queueOrderPush(orderDao.selectById(orderId));
            notifyOrderChanged(order, "ORDER_PAID");
        }
        return getOrder(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopOrderVO markCheckoutPaid(Long checkoutId, String payType) {
        DmsShopTrade trade = tradeDao.selectByIdForUpdate(checkoutId);
        if (trade == null) Asserts.fail("支付交易不存在");
        assertTenantAccess(trade.getTenantId());
        List<DmsShopOrder> children = orderDao.selectByTradeIdForUpdate(checkoutId);
        if (children.isEmpty()) Asserts.fail("支付交易没有履约子订单");
        if (Integer.valueOf(1).equals(trade.getStatus())) return checkoutResult(trade);
        if (!Integer.valueOf(0).equals(trade.getStatus())) Asserts.fail("当前交易状态不能支付");
        String normalizedPayType = payType == null ? "" : payType.trim().toUpperCase(Locale.ROOT);
        if (!normalizedPayType.equalsIgnoreCase(trade.getPayType())) {
            Asserts.fail("支付方式与交易父单不一致，已停止支付");
        }

        BigDecimal childPayTotal = children.stream().map(DmsShopOrder::getPayAmount)
                .map(this::money).reduce(ZERO, BigDecimal::add);
        if (money(trade.getPayAmount()).compareTo(money(childPayTotal)) != 0) {
            Asserts.fail("交易父单与子订单金额不一致，已停止支付");
        }
        for (DmsShopOrder child : children) {
            if (!Integer.valueOf(0).equals(child.getStatus())) Asserts.fail("存在非待支付子订单，已停止整单支付");
            if (!Objects.equals(trade.getTradeNo(), child.getPaymentOrderNo())) {
                Asserts.fail("子订单支付单号与交易父单不一致，已停止支付");
            }
            merchantService.assertOrderCanBePaid(child.getId());
        }
        // 必须先验证完全部子单，再开始任何入账、奖金、结算和履约副作用。
        // 即使后续代码仍由同一数据库事务兜底，也不能让前一个子单先进入业务处理后才发现后一个子单异常。
        for (DmsShopOrder child : children) {
            markOrderPaid(child.getId(), normalizedPayType);
        }
        if (tradeDao.markPaid(checkoutId, normalizedPayType) != 1) Asserts.fail("交易父单支付状态更新失败");
        return checkoutResult(tradeDao.selectById(checkoutId));
    }

    private ShopOrderVO checkoutResult(DmsShopTrade trade) {
        if (trade == null) Asserts.fail("支付交易不存在");
        List<ShopOrderVO> children = orderDao.selectByTradeId(trade.getId()).stream().map(order -> getOrder(order.getId())).toList();
        if (children.isEmpty()) Asserts.fail("支付交易没有履约子订单");
        ShopOrderVO result = children.get(0);
        result.setCheckoutId(trade.getId());
        result.setCheckoutNo(trade.getTradeNo());
        result.setGroupedCheckout(true);
        result.setChildOrders(children);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelCheckout(Long checkoutId, DmsShopMember member) {
        DmsShopTrade trade = tradeDao.selectByIdForUpdate(checkoutId);
        if (trade == null) Asserts.fail("支付交易不存在");
        assertTenantAccess(trade.getTenantId());
        if (member != null && !member.getUserId().equals(trade.getUserId())) Asserts.fail("不能取消他人的订单");
        if (Integer.valueOf(4).equals(trade.getStatus())) return true;
        if (!Integer.valueOf(0).equals(trade.getStatus())) Asserts.fail("当前交易状态不能取消");
        List<DmsShopOrder> children = orderDao.selectByTradeIdForUpdate(checkoutId);
        if (children.isEmpty()) Asserts.fail("支付交易没有履约子订单");
        for (DmsShopOrder child : children) {
            if (!Integer.valueOf(0).equals(child.getStatus())) Asserts.fail("存在非待支付子订单，不能取消整笔交易");
        }
        for (DmsShopOrder child : children) {
            if (orderDao.cancel(child.getId()) != 1) Asserts.fail("子订单取消失败");
            restockOrder(child.getId());
            notifyOrderChanged(child, "ORDER_CANCELLED");
        }
        if (tradeDao.closePending(checkoutId) != 1) Asserts.fail("交易父单关闭失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId, DmsShopMember member) {
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        if (member != null && !member.getUserId().equals(order.getUserId())) {
            Asserts.fail("不能取消他人的订单");
        }
        if (order.getTradeId() != null) return cancelCheckout(order.getTradeId(), member);
        if (Integer.valueOf(4).equals(order.getStatus())) return true;
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            Asserts.fail("当前订单状态不能取消");
        }
        int updated = orderDao.cancel(orderId);
        if (updated > 0) {
            restockOrder(orderId);
            notifyOrderChanged(order, "ORDER_CANCELLED");
        }
        return updated > 0;
    }

    @Override
    public int closeExpiredPendingOrders(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, pendingOrderTimeoutMinutes));
        int closed = 0;
        for (Long orderId : orderDao.selectExpiredPendingIds(cutoff, safeLimit)) {
            try {
                Integer itemClosed = transactionTemplate.execute(status -> closeExpiredPendingOrder(orderId, cutoff));
                closed += itemClosed == null ? 0 : itemClosed;
            } catch (Exception ex) {
                // 每笔订单独立提交。一笔脏数据或库存异常不能撤销本批次已经成功关闭的其他订单。
                log.error("超时订单自动关闭失败，保留该订单等待下轮重试: orderId={}", orderId, ex);
            }
        }
        return closed;
    }

    private int closeExpiredPendingOrder(Long orderId, LocalDateTime cutoff) {
        DmsShopOrder candidate = orderDao.selectById(orderId);
        if (candidate != null && candidate.getTradeId() != null && Integer.valueOf(0).equals(candidate.getStatus())) {
            return closeExpiredCheckout(candidate.getTradeId(), cutoff);
        }
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order != null && Integer.valueOf(0).equals(order.getStatus())
                && order.getCreateTime() != null && !order.getCreateTime().isAfter(cutoff)
                && orderDao.closePending(orderId) > 0) {
            restockOrder(orderId);
            notifyOrderChanged(order, "ORDER_TIMEOUT_CLOSED");
            return 1;
        }
        return 0;
    }

    private int closeExpiredCheckout(Long checkoutId, LocalDateTime cutoff) {
        DmsShopTrade trade = tradeDao.selectByIdForUpdate(checkoutId);
        if (trade == null || !Integer.valueOf(0).equals(trade.getStatus())
                || trade.getCreateTime() == null || trade.getCreateTime().isAfter(cutoff)) return 0;
        List<DmsShopOrder> children = orderDao.selectByTradeIdForUpdate(checkoutId);
        if (children.isEmpty() || children.stream().anyMatch(order -> !Integer.valueOf(0).equals(order.getStatus()))) return 0;
        int closed = 0;
        for (DmsShopOrder child : children) {
            if (orderDao.closePending(child.getId()) != 1) Asserts.fail("超时子订单关闭失败");
            restockOrder(child.getId());
            notifyOrderChanged(child, "ORDER_TIMEOUT_CLOSED");
            closed++;
        }
        if (tradeDao.closePending(checkoutId) != 1) Asserts.fail("超时交易父单关闭失败");
        return closed;
    }

    @Override
    public boolean shipOrder(Long orderId, ShopOrderShipDTO dto) {
        return orderShipmentService.shipOrder(orderId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrderServiceRemark(Long orderId, String serviceRemark) {
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null) Asserts.fail("订单不存在");
        assertTenantAccess(order.getTenantId());
        assertMerchantOrderAccess(order, true);
        String normalized = serviceRemark == null || serviceRemark.isBlank() ? null : serviceRemark.trim();
        if (normalized != null && normalized.length() > 500) Asserts.fail("客服备注不能超过500个字");
        if (Objects.equals(order.getServiceRemark(), normalized)) return true;
        if (orderDao.updateServiceRemark(orderId, normalized) <= 0) Asserts.fail("客服备注保存失败，请稍后重试");
        operationLogService.log("SHOP_ORDER", "SERVICE_REMARK_UPDATE", "SHOP_ORDER", String.valueOf(orderId),
                order.getServiceRemark(), normalized, "更新订单客服内部备注：" + order.getOrderNo());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmReceive(Long orderId, DmsShopMember member) {
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        if (member != null && !member.getUserId().equals(order.getUserId())) {
            Asserts.fail("不能确认他人的订单");
        }
        if (Integer.valueOf(3).equals(order.getStatus())) return true;
        if (!Integer.valueOf(2).equals(order.getStatus())) return false;
        boolean confirmed = orderDao.confirmReceive(orderId) > 0;
        if (confirmed) {
            merchantService.lockOrderSettlementEligibility(orderId);
            notifyOrderChanged(order, "ORDER_RECEIVED");
        }
        return confirmed;
    }

    @Override
    public ShopProfileVO getProfile(Long userId, Long agentId) {
        DmsAgent agent = agentId != null ? agentDao.selectById(agentId) : agentDao.selectByUserId(userId);
        if (agent == null) {
            ShopProfileVO vo = new ShopProfileVO();
            vo.setCanViewTeamPerformance(false);
            vo.setAssetAccounts(userId == null
                    ? Collections.emptyList()
                    : memberAssetService.listAccounts(null, userId));
            vo.setDisplayConfig(getDisplayConfig(resolveTenantId(null)));
            return vo;
        }

        LocalDate now = LocalDate.now();
        DmsTenantDisplayConfig displayConfig = getDisplayConfig(resolveTenantId(null));
        boolean showTeam = auditService.canViewTeamPerformance(agent.getId(), agent.getUserId());
        ShopProfileVO vo = new ShopProfileVO();
        vo.setAgent(agent);
        vo.setAccount(accountDao.selectByAgentId(agent.getId()));
        vo.setCanViewTeamPerformance(showTeam);
        vo.setPerformance(showTeam ? performanceService.getPerformanceOverview(agent.getId(), now.withDayOfMonth(1), now) : null);
        vo.setAssetAccounts(memberAssetService.listAccounts(agent.getId(), agent.getUserId()));
        vo.setDisplayConfig(displayConfig);
        vo.setMigrationBaseline(migrationBaselineDao.selectByAgentId(agent.getId()));
        return vo;
    }

    @Override
    public ShopProfileVO getProfile(DmsShopMember member, Long agentId) {
        if (member == null) {
            return getProfile((Long) null, agentId);
        }
        DmsAgent agent = agentId != null ? agentDao.selectById(agentId) : agentDao.selectByUserId(member.getUserId());
        ShopProfileVO vo = new ShopProfileVO();
        vo.setMember(member);
        vo.setAgent(agent);
        vo.setOrderSummary(getOrderStatusSummary(member));
        return vo;
    }

    @Override
    public ShopProfileVO getProfilePerformance(DmsShopMember member) {
        ShopProfileVO vo = new ShopProfileVO();
        if (member == null) {
            vo.setCanViewTeamPerformance(false);
            return vo;
        }
        DmsAgent agent = agentDao.selectByUserId(member.getUserId());
        vo.setMember(member);
        vo.setAgent(agent);
        if (agent == null) {
            vo.setCanViewTeamPerformance(false);
            return vo;
        }
        boolean canView = auditService.canViewTeamPerformance(agent.getId(), agent.getUserId());
        vo.setCanViewTeamPerformance(canView);
        if (canView) {
            vo.setPerformance(performanceService.getProfilePerformanceSummary(agent.getId(), LocalDate.now()));
        }
        return vo;
    }

    @Override
    public ShopOrderStatusSummaryVO getOrderStatusSummary(DmsShopMember member) {
        if (member == null || member.getUserId() == null) {
            return new ShopOrderStatusSummaryVO();
        }
        ShopOrderStatusSummaryVO summary = orderDao.selectStatusSummary(member.getUserId());
        return summary == null ? new ShopOrderStatusSummaryVO() : summary;
    }

    @Override
    public ShopProfileVO getAdminProfile(DmsShopMember member) {
        if (member == null || member.getId() == null) {
            Asserts.fail("会员不存在");
        }
        DmsAgent agent = agentDao.selectByUserId(member.getUserId());
        LocalDate now = LocalDate.now();
        ShopProfileVO vo = new ShopProfileVO();
        vo.setMember(member);
        vo.setAgent(agent);
        vo.setAddresses(addressDao.selectByMemberId(member.getId()));
        vo.setOrders(orderDao.selectPaidProfileOrdersByUserId(member.getUserId()).stream().map(order -> {
            ShopOrderVO orderVO = new ShopOrderVO();
            orderVO.setOrder(order);
            fillMemberAccount(orderVO, order);
            orderVO.setItems(orderItemDao.selectByOrderId(order.getId()));
            fillShipments(orderVO, order);
            orderVO.setFinance(auditService.getOrderFinanceDetail(order.getId()).getFinance());
            orderVO.setAfterSales(hydrateAfterSales(afterSaleDao.selectByOrderId(order.getId())));
            orderVO.setPendingReviewCount(pendingReviewCount(order));
            fillAfterSaleWindow(orderVO, order);
            orderVO.setDisplayConfig(getDisplayConfig(order.getTenantId()));
            return orderVO;
        }).toList());
        vo.setDisplayConfig(getDisplayConfig(resolveTenantId(null)));
        if (agent == null) {
            vo.setCanViewTeamPerformance(false);
            vo.setAssetAccounts(memberAssetService.listAccounts(null, member.getUserId()));
            return vo;
        }
        vo.setAccount(accountDao.selectByAgentId(agent.getId()));
        vo.setMigrationBaseline(migrationBaselineDao.selectByAgentId(agent.getId()));
        vo.setCanViewTeamPerformance(true);
        vo.setPerformance(performanceService.getPerformanceOverview(agent.getId(), now.withDayOfMonth(1), now));
        vo.setAssetAccounts(memberAssetService.listAccounts(agent.getId(), agent.getUserId()));
        return vo;
    }

    private void validateSubmit(ShopOrderSubmitDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            Asserts.fail("订单商品不能为空");
        }
        if (dto.getReceiverName() == null || dto.getReceiverName().isBlank()) {
            Asserts.fail("收货人不能为空");
        }
        if (!PhoneNumberUtils.isValidMainlandMobile(dto.getReceiverPhone())) {
            Asserts.fail("请填写正确的11位手机号");
        }
        dto.setReceiverPhone(PhoneNumberUtils.normalize(dto.getReceiverPhone()));
        if (dto.getReceiverAddress() == null || dto.getReceiverAddress().isBlank()) {
            Asserts.fail("收货地址不能为空");
        }
        for (ShopOrderItemDTO item : dto.getItems()) {
            if (item.getProductId() == null) {
                Asserts.fail("商品ID不能为空");
            }
        }
    }

    private LinkedHashMap<String, List<ShopOrderItemDTO>> groupSubmitItemsByMerchant(List<ShopOrderItemDTO> items) {
        LinkedHashMap<String, List<ShopOrderItemDTO>> groups = new LinkedHashMap<>();
        for (ShopOrderItemDTO item : items) {
            DmsShopProduct product = item == null || item.getProductId() == null
                    ? null : productDao.selectById(item.getProductId());
            if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
                Asserts.fail("商品不存在或已下架");
            }
            assertTenantAccess(product.getTenantId());
            String key = product.getMerchantId() == null ? "PLATFORM" : "MERCHANT:" + product.getMerchantId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    private ShopOrderSubmitDTO copySubmit(ShopOrderSubmitDTO source, List<ShopOrderItemDTO> items) {
        ShopOrderSubmitDTO copy = new ShopOrderSubmitDTO();
        copy.setUserId(source.getUserId()); copy.setAgentId(source.getAgentId()); copy.setInviteCode(source.getInviteCode());
        copy.setAddressId(source.getAddressId()); copy.setReceiverName(source.getReceiverName()); copy.setReceiverPhone(source.getReceiverPhone());
        copy.setReceiverAddress(source.getReceiverAddress()); copy.setReceiverProvince(source.getReceiverProvince()); copy.setReceiverCity(source.getReceiverCity());
        copy.setReceiverDistrict(source.getReceiverDistrict()); copy.setReceiverDetailAddress(source.getReceiverDetailAddress());
        copy.setPayType(source.getPayType()); copy.setRemark(source.getRemark()); copy.setBusinessType(source.getBusinessType());
        copy.setBusinessSourceId(source.getBusinessSourceId()); copy.setSmsCode(source.getSmsCode()); copy.setItems(new ArrayList<>(items));
        return copy;
    }

    private String generateTradeNo(Long tradeId, LocalDateTime time) {
        String date = (time == null ? LocalDateTime.now() : time).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 保留完整雪花 ID，避免取模后在同一秒内产生交易号碰撞。
        return "T" + date + Long.toUnsignedString(tradeId == null ? 0L : tradeId);
    }

    private DmsAgent resolveOwnerAgent(ShopOrderSubmitDTO dto) {
        if (dto.getAgentId() != null) {
            DmsAgent agent = agentDao.selectById(dto.getAgentId());
            if (agent == null) {
                Asserts.fail("推荐代理不存在");
            }
            return agent;
        }
        if (dto.getInviteCode() != null && !dto.getInviteCode().isBlank()) {
            DmsAgent agent = agentDao.selectByInviteCode(dto.getInviteCode());
            if (agent == null) {
                Asserts.fail("邀请码无效");
            }
            if (!AgentStatusEnum.NORMAL.getValue().equals(agent.getStatus())) {
                Asserts.fail("邀请人当前不可关联新订单");
            }
            return agent;
        }
        return dto.getUserId() == null ? null : agentDao.selectByUserId(dto.getUserId());
    }

    private Long resolveOrderUserId(ShopOrderSubmitDTO dto, DmsAgent ownerAgent) {
        if (dto.getUserId() != null) {
            return dto.getUserId();
        }
        return ownerAgent == null ? 0L : ownerAgent.getUserId();
    }

    private void fillAddress(ShopOrderSubmitDTO dto, DmsShopMember member) {
        if (dto == null || dto.getAddressId() == null) {
            return;
        }
        DmsShopAddress address = addressDao.selectById(dto.getAddressId());
        if (address == null) {
            Asserts.fail("收货地址不存在");
        }
        if (member != null && !member.getId().equals(address.getMemberId())) {
            Asserts.fail("不能使用他人的收货地址");
        }
        if (dto.getUserId() != null && !dto.getUserId().equals(address.getUserId())) {
            Asserts.fail("收货地址与下单用户不匹配");
        }
        dto.setReceiverName(address.getReceiverName());
        dto.setReceiverPhone(address.getReceiverPhone());
        dto.setReceiverProvince(address.getProvince());
        dto.setReceiverCity(address.getCity());
        dto.setReceiverDistrict(address.getDistrict());
        dto.setReceiverDetailAddress(address.getDetailAddress());
        dto.setReceiverAddress(joinAddress(address));
    }

    private void normalizeManualAddress(ShopOrderSubmitDTO dto) {
        if (dto == null) return;
        boolean hasStructuredAddress = !blank(dto.getReceiverProvince()) || !blank(dto.getReceiverCity())
                || !blank(dto.getReceiverDistrict()) || !blank(dto.getReceiverDetailAddress());
        if (hasStructuredAddress) {
            if (blank(dto.getReceiverProvince()) || blank(dto.getReceiverCity())
                    || blank(dto.getReceiverDistrict()) || blank(dto.getReceiverDetailAddress())) {
                Asserts.fail("请完整选择省、市、区/县并填写详细地址");
            }
            dto.setReceiverAddress(String.join(" ", dto.getReceiverProvince().trim(), dto.getReceiverCity().trim(),
                    dto.getReceiverDistrict().trim(), dto.getReceiverDetailAddress().trim()));
        }
    }

    private String joinAddress(DmsShopAddress address) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, address.getProvince());
        appendPart(builder, address.getCity());
        appendPart(builder, address.getDistrict());
        appendPart(builder, address.getDetailAddress());
        return builder.toString();
    }

    private void appendPart(StringBuilder builder, String part) {
        if (part != null && !part.isBlank()) {
            builder.append(part);
        }
    }

    /**
     * 校验会员在同一商品上的累计限购数量。待付款订单也会占用额度，避免会员通过
     * 连续创建多个待付款订单绕过限购；订单关闭（包括整单退款）后额度会释放。
     */
    private void validatePurchaseLimit(DmsShopProduct product,
                                       Long userId,
                                       int requestedQuantity,
                                       Map<Long, Integer> existingPurchaseQuantities) {
        int limit = product.getPurchaseLimit() == null ? 0 : product.getPurchaseLimit();
        if (limit <= 0 || userId == null) {
            return;
        }
        int existingQuantity = existingPurchaseQuantities.computeIfAbsent(product.getId(), id ->
                orderItemDao.sumQuantityByUserAndProduct(userId, id,
                        product.getTenantId() == null ? DEFAULT_TENANT_ID : product.getTenantId()));
        if ((long) existingQuantity + requestedQuantity > limit) {
            int remaining = Math.max(0, limit - existingQuantity);
            Asserts.fail(purchaseLimitMessage(product.getProductName(), limit, remaining));
        }
    }

    private void validateBusinessProduct(String businessType,
                                         DmsShopProduct product,
                                         Long userId,
                                         int requestedQuantity,
                                         Map<Long, Integer> existingPurchaseQuantities,
                                         DmsFlashSaleActivity flashActivity,
                                         ShopOrderItemDTO item) {
        requireActiveProductMerchant(product);
        if (ShopBusinessType.NORMAL.equals(businessType)) {
            if (Integer.valueOf(0).equals(product.getNormalSaleEnabled())) Asserts.fail("该商品不在普通商城销售");
            validatePurchaseLimit(product, userId, requestedQuantity, existingPurchaseQuantities);
            return;
        }
        if (ShopBusinessType.REPURCHASE.equals(businessType)) {
            if (!Integer.valueOf(1).equals(product.getRepurchaseSaleEnabled())
                    || money(product.getRepurchasePrice()).compareTo(ZERO) <= 0) {
                Asserts.fail("该商品不在复购商城销售");
            }
            int limit = product.getRepurchasePurchaseLimit() == null ? 0 : product.getRepurchasePurchaseLimit();
            if (limit > 0 && userId != null) {
                int existing = existingPurchaseQuantities.computeIfAbsent(product.getId(), id ->
                        orderItemDao.sumQuantityByUserProductAndBusinessType(userId, id,
                                product.getTenantId(), ShopBusinessType.REPURCHASE));
                if ((long) existing + requestedQuantity > limit) {
                    Asserts.fail(purchaseLimitMessage(product.getProductName(), limit, Math.max(0, limit - existing)));
                }
            }
            return;
        }
        if (flashActivity == null || !flashActivity.getProductId().equals(product.getId())
                || !Objects.equals(flashActivity.getSkuId(), item.getSkuId())) {
            Asserts.fail("秒杀商品与活动不一致");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!Integer.valueOf(1).equals(flashActivity.getStatus()) || now.isBefore(flashActivity.getStartTime())
                || !now.isBefore(flashActivity.getEndTime())) Asserts.fail("秒杀活动当前不可下单");
        if (requestedQuantity > flashActivity.getPerUserLimit()) {
            Asserts.fail("本活动每人限购 " + flashActivity.getPerUserLimit() + " 件");
        }
    }

    private BigDecimal resolveBusinessPrice(String businessType, DmsShopProduct product,
                                            DmsShopSku sku, DmsFlashSaleActivity flashActivity) {
        if (ShopBusinessType.FLASH_SALE.equals(businessType)) return money(flashActivity.getFlashPrice());
        if (ShopBusinessType.REPURCHASE.equals(businessType)) {
            BigDecimal skuPrice = sku == null ? null : sku.getRepurchasePrice();
            return skuPrice != null && skuPrice.compareTo(ZERO) > 0 ? skuPrice : money(product.getRepurchasePrice());
        }
        return sku == null ? money(product.getSalePrice()) : money(sku.getSalePrice());
    }

    private BigDecimal resolveBusinessPv(String businessType, DmsShopProduct product,
                                         DmsShopSku sku, DmsFlashSaleActivity flashActivity, BigDecimal price) {
        if (!isPvEnabled(product.getTenantId())) return ZERO;
        if (ShopBusinessType.FLASH_SALE.equals(businessType)) return limitPvToSalePrice(flashActivity.getFlashPv(), price);
        if (ShopBusinessType.REPURCHASE.equals(businessType)) {
            BigDecimal skuPv = sku == null ? null : sku.getRepurchasePv();
            BigDecimal configured = skuPv != null && skuPv.compareTo(ZERO) > 0 ? skuPv : product.getRepurchasePv();
            return limitPvToSalePrice(configured, price);
        }
        return resolveUnitPv(product, sku, price);
    }

    private String purchaseLimitMessage(String productName, int limit, int remainingQuantity) {
        String name = productName == null || productName.isBlank() ? "当前商品" : productName.trim();
        if (remainingQuantity <= 0) {
            return name + "每位会员限购 " + limit + " 件，您已达到限购数量，无法继续加购";
        }
        return name + "每位会员限购 " + limit + " 件，您还可购买 " + remainingQuantity + " 件";
    }

    private void fillProductDefaults(DmsShopProduct product) {
        product.setTenantId(resolveTenantId(product.getTenantId()));
        if (product.getProductName() == null || product.getProductName().isBlank()) {
            Asserts.fail("商品名称不能为空");
        }
        product.setCoverUrl(normalizeProductImageUrl(product.getCoverUrl(), "商品主图"));
        product.setGalleryUrls(normalizeProductImageList(product.getGalleryUrls(), "商品轮播图"));
        product.setDetailImages(normalizeProductImageList(product.getDetailImages(), "商品详情图"));
        product.setProductNo(product.getProductNo() == null || product.getProductNo().isBlank()
                ? "SPU" + IdUtil.getSnowflakeNextId()
                : product.getProductNo());
        product.setSalePrice(money(product.getSalePrice()));
        product.setMarketPrice(money(product.getMarketPrice()));
        product.setCostAmount(money(product.getCostAmount()));
        if (product.getSettlementDelayDaysOverride() != null
                && (product.getSettlementDelayDaysOverride() < 0 || product.getSettlementDelayDaysOverride() > 365)) {
            Asserts.fail("商品结算等待天数必须在0到365天之间");
        }
        product.setPvValue(money(product.getPvValue()));
        if (isPvEnabled(product.getTenantId())) {
            validatePv(product.getPvValue(), product.getSalePrice(), "商品PV");
        } else {
            product.setPvValue(ZERO);
        }
        product.setBvValue(money(product.getBvValue()));
        product.setStock(product.getStock() == null ? 0 : product.getStock());
        product.setSafetyStock(product.getSafetyStock() == null ? 0 : Math.max(0, product.getSafetyStock()));
        product.setPurchaseLimit(product.getPurchaseLimit() == null ? 0 : Math.max(0, product.getPurchaseLimit()));
        product.setNormalSaleEnabled(Integer.valueOf(0).equals(product.getNormalSaleEnabled()) ? 0 : 1);
        product.setRepurchaseSaleEnabled(Integer.valueOf(1).equals(product.getRepurchaseSaleEnabled()) ? 1 : 0);
        product.setEnrollmentSaleEnabled(Integer.valueOf(1).equals(product.getEnrollmentSaleEnabled()) ? 1 : 0);
        product.setRepurchasePrice(money(product.getRepurchasePrice()));
        product.setRepurchasePv(money(product.getRepurchasePv()));
        product.setRepurchasePurchaseLimit(product.getRepurchasePurchaseLimit() == null
                ? 0 : Math.max(0, product.getRepurchasePurchaseLimit()));
        if (Integer.valueOf(1).equals(product.getRepurchaseSaleEnabled())) {
            if (product.getRepurchasePrice().compareTo(ZERO) <= 0) Asserts.fail("启用复购销售时复购价必须大于0");
            validatePv(product.getRepurchasePv(), product.getRepurchasePrice(), "复购PV");
        }
        if (product.getNormalSaleEnabled() == 0 && product.getRepurchaseSaleEnabled() == 0
                && product.getEnrollmentSaleEnabled() == 0) {
            Asserts.fail("普通商城、复购区、报单区至少启用一个");
        }
        String bonusMode = normalizeTeamBonusMode(product);
        product.setTeamBonusMode(bonusMode);
        if (product.getMerchantId() == null) {
            product.setMerchantName(null);
            product.setSettlementDelayDaysOverride(null);
            if (!"INHERIT".equals(bonusMode) && !"NONE".equals(bonusMode)) {
                Asserts.fail("平台自营商品请使用继承商城规则或不参与团队奖金");
            }
        } else {
            DmsMerchant merchant = merchantDao.selectById(product.getMerchantId());
            if (merchant == null || !product.getTenantId().equals(merchant.getTenantId())
                    || !Integer.valueOf(1).equals(merchant.getStatus())) {
                Asserts.fail("所选商户不存在或已停用");
            }
            product.setMerchantName(merchant.getMerchantName());
            if (product.getCostAmount().compareTo(ZERO) <= 0) Asserts.fail("商户商品必须填写大于0的结算价");
            if (Integer.valueOf(1).equals(product.getNormalSaleEnabled())
                    && product.getCostAmount().compareTo(product.getSalePrice()) > 0) {
                Asserts.fail("商户商品结算价不能高于普通售价");
            }
            if (Integer.valueOf(1).equals(product.getRepurchaseSaleEnabled())
                    && product.getCostAmount().compareTo(product.getRepurchasePrice()) > 0) {
                Asserts.fail("商户商品结算价不能高于复购价");
            }
            if ("INHERIT".equals(bonusMode)) Asserts.fail("商户商品必须明确选择是否参与团队奖金");
            if ("STANDARD".equals(bonusMode) && Integer.valueOf(1).equals(product.getNormalSaleEnabled())) {
                Asserts.fail("参与团队奖金的商户商品只能进入复购区或报单区，不能同时在普通商城销售");
            }
        }
        product.setSalesCount(product.getSalesCount() == null ? 0 : product.getSalesCount());
        product.setSort(product.getSort() == null ? 0 : product.getSort());
        product.setStatus(product.getStatus() == null ? 1 : product.getStatus());
        product.setFreightType(product.getFreightType() == null ? 0 : product.getFreightType());
        product.setFreightAmount(money(product.getFreightAmount()));
        product.setFreeShippingAmount(product.getFreeShippingAmount() == null ? null : money(product.getFreeShippingAmount()));
        if (blank(product.getDeliveryProvince()) || blank(product.getDeliveryCity()) || blank(product.getDeliveryDistrict())) {
            Asserts.fail("请完整选择发货地的省、市、区/县");
        }
        if (blank(product.getDeliveryAddress())) {
            product.setDeliveryAddress(String.join(" ", product.getDeliveryProvince().trim(),
                    product.getDeliveryCity().trim(), product.getDeliveryDistrict().trim()));
        }
        switch (product.getFreightType()) {
            case 0 -> {
                product.setFreightAmount(ZERO);
                product.setFreeShippingAmount(null);
                product.setFreightTemplateId(null);
                product.setFreightTemplateName(null);
            }
            case 1 -> {
                if (product.getFreightAmount().compareTo(ZERO) <= 0) Asserts.fail("固定运费必须大于0");
                product.setFreeShippingAmount(null);
                product.setFreightTemplateId(null);
                product.setFreightTemplateName(null);
            }
            case 2 -> {
                if (product.getFreightAmount().compareTo(ZERO) <= 0) Asserts.fail("未满额运费必须大于0");
                if (product.getFreeShippingAmount() == null || product.getFreeShippingAmount().compareTo(ZERO) <= 0) {
                    Asserts.fail("请填写满额包邮门槛");
                }
                product.setFreightTemplateId(null);
                product.setFreightTemplateName(null);
            }
            case 3 -> {
                DmsFreightTemplate template = product.getFreightTemplateId() == null
                        ? null : freightTemplateDao.selectByIdScoped(product.getTenantId(), product.getFreightTemplateId());
                if (template == null || !Integer.valueOf(1).equals(template.getStatus())
                        || !product.getTenantId().equals(template.getTenantId())) {
                    Asserts.fail("请选择已启用的运费模板");
                }
                product.setFreightTemplateName(template.getTemplateName());
                product.setFreightAmount(ZERO);
                product.setFreeShippingAmount(null);
            }
            default -> Asserts.fail("不支持的配送方式");
        }
    }

    private DmsFreightTemplate toFreightTemplate(Long id, FreightTemplateSaveDTO dto) {
        if (dto == null || blank(dto.getTemplateName())) Asserts.fail("运费模板名称不能为空");
        String defaultMode = normalizeFreightMode(dto.getDefaultMode());
        BigDecimal defaultAmount = money(dto.getDefaultFreightAmount());
        if ("FIXED".equals(defaultMode) && defaultAmount.compareTo(ZERO) <= 0) {
            Asserts.fail("默认固定运费必须大于0");
        }
        List<FreightTemplateRuleDTO> rules = dto.getRules() == null ? Collections.emptyList() : dto.getRules();
        Set<String> assignedRegions = new HashSet<>();
        for (FreightTemplateRuleDTO rule : rules) {
            validateFreightRule(rule);
            for (List<String> path : rule.getRegionPaths()) {
                String regionKey = path.stream().map(this::normalizeRegionName).collect(java.util.stream.Collectors.joining("/"));
                if (!assignedRegions.add(regionKey)) {
                    Asserts.fail("同一地区不能重复配置多条运费规则：" + String.join("/", path));
                }
            }
        }
        DmsFreightTemplate template = new DmsFreightTemplate();
        template.setId(id);
        template.setTenantId(resolveTenantId(null));
        assertTenantAccess(template.getTenantId());
        template.setTemplateName(dto.getTemplateName().trim());
        template.setDefaultMode(defaultMode);
        template.setDefaultFreightAmount("FIXED".equals(defaultMode) ? defaultAmount : ZERO);
        try {
            template.setRulesJson(objectMapper.writeValueAsString(rules));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("运费模板地区规则格式错误", e);
        }
        template.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        if (!Integer.valueOf(0).equals(template.getStatus()) && !Integer.valueOf(1).equals(template.getStatus())) {
            Asserts.fail("运费模板状态不正确");
        }
        return template;
    }

    private void validateFreightRule(FreightTemplateRuleDTO rule) {
        if (rule == null || rule.getRegionPaths() == null || rule.getRegionPaths().isEmpty()) {
            Asserts.fail("每条运费特例至少选择一个省/市/区");
        }
        for (List<String> path : rule.getRegionPaths()) {
            if (path == null || path.isEmpty() || path.size() > 3 || path.stream().anyMatch(this::blank)) {
                Asserts.fail("运费模板地区路径不完整");
            }
        }
        String mode = normalizeFreightMode(rule.getMode());
        rule.setMode(mode);
        rule.setFreightAmount("FIXED".equals(mode) ? money(rule.getFreightAmount()) : ZERO);
        if ("FIXED".equals(mode) && rule.getFreightAmount().compareTo(ZERO) <= 0) {
            Asserts.fail("地区额外运费必须大于0");
        }
    }

    private String normalizeFreightMode(String mode) {
        String normalized = mode == null ? "FREE" : mode.trim().toUpperCase(Locale.ROOT);
        if (!List.of("FREE", "FIXED", "UNAVAILABLE").contains(normalized)) {
            Asserts.fail("运费规则必须是包邮、固定运费或不发货");
        }
        return normalized;
    }

    private void fillCategoryDefaults(DmsShopCategory category) {
        if (category == null || category.getCategoryName() == null || category.getCategoryName().isBlank()) {
            Asserts.fail("分类名称不能为空");
        }
        category.setTenantId(resolveTenantId(category.getTenantId()));
        category.setCategoryName(category.getCategoryName().trim());
        category.setSort(category.getSort() == null ? 0 : category.getSort());
        category.setStatus(category.getStatus() == null ? 1 : category.getStatus());
    }

    private void assertCategoryNameAvailable(Long tenantId, String categoryName, Long excludeId) {
        if (categoryDao.countByNameExcludingId(tenantId, categoryName, excludeId) > 0) {
            Asserts.fail("分类名称已存在，请勿重复添加");
        }
    }

    private void fillBannerDefaults(DmsShopBanner banner) {
        if (banner == null || banner.getTitle() == null || banner.getTitle().isBlank()) {
            Asserts.fail("轮播图标题不能为空");
        }
        if (banner.getImageUrl() == null || banner.getImageUrl().isBlank()) {
            Asserts.fail("轮播图图片不能为空");
        }
        banner.setTenantId(resolveTenantId(banner.getTenantId()));
        banner.setTitle(banner.getTitle().trim());
        validateBannerLink(banner);
        if ("BRAND_CULTURE".equals(banner.getLinkType())) {
            banner.setImageUrl(brandCultureImagePolicy.validateBanner(banner.getTenantId(), banner.getImageUrl()));
        }
        banner.setSort(banner.getSort() == null ? 0 : banner.getSort());
        banner.setStatus(banner.getStatus() == null ? 1 : banner.getStatus());
        assertBrandCultureBannerCanBeEnabled(banner, banner.getStatus());
    }

    private void assertBrandCultureBannerCanBeEnabled(DmsShopBanner banner, Integer status) {
        if (!"BRAND_CULTURE".equalsIgnoreCase(banner.getLinkType()) || !Integer.valueOf(1).equals(status)) return;
        DmsTenant tenant = tenantDao.selectById(banner.getTenantId());
        if (tenant == null || !Integer.valueOf(1).equals(tenant.getBrandCultureEnabled())) {
            Asserts.fail("品牌文化页当前已关闭，请先在商城视觉装修工作台中开启并保存，再启用此横幅");
        }
    }

    static void validateBannerLink(DmsShopBanner banner) {
        String type = banner.getLinkType() == null || banner.getLinkType().isBlank()
                ? "NONE" : banner.getLinkType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("NONE", "PRODUCT", "CATEGORY", "URL", "BRAND_CULTURE").contains(type)) Asserts.fail("轮播图跳转类型不支持");
        banner.setLinkType(type);
        if ("NONE".equals(type) || "BRAND_CULTURE".equals(type)) {
            banner.setLinkValue(null);
            return;
        }
        if (banner.getLinkValue() == null || banner.getLinkValue().isBlank()) Asserts.fail("请填写轮播图跳转内容");
        String value = banner.getLinkValue().trim();
        if ("URL".equals(type)) {
            try {
                URI uri = URI.create(value);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                    Asserts.fail("轮播图外部链接仅支持完整的HTTPS地址");
                }
            } catch (IllegalArgumentException exception) {
                Asserts.fail("轮播图外部链接格式不正确");
            }
        }
        banner.setLinkValue(value);
    }

    static List<DmsShopBanner> filterPublicBanners(List<DmsShopBanner> banners, boolean brandCultureEnabled) {
        if (banners == null || banners.isEmpty()) return List.of();
        return banners.stream()
                .filter(banner -> brandCultureEnabled || !"BRAND_CULTURE".equalsIgnoreCase(banner.getLinkType()))
                .toList();
    }

    private void fillNoticeDefaults(DmsShopNotice notice) {
        if (notice == null || notice.getTitle() == null || notice.getTitle().isBlank()) {
            Asserts.fail("公告标题不能为空");
        }
        if (notice.getTitle().trim().length() > 128) {
            Asserts.fail("公告标题不能超过128个字");
        }
        if (notice.getContent() == null || notice.getContent().isBlank()) {
            Asserts.fail("公告内容不能为空");
        }
        if (notice.getContent().trim().length() > 1000) {
            Asserts.fail("公告内容不能超过1000个字");
        }
        contentModerationService.assertAllowed("公告标题", notice.getTitle());
        contentModerationService.assertAllowed("公告内容", notice.getContent());
        notice.setTenantId(resolveTenantId(notice.getTenantId()));
        notice.setTitle(notice.getTitle().trim());
        notice.setContent(notice.getContent().trim());
        notice.setNoticeType(notice.getNoticeType() == null ? 1 : notice.getNoticeType());
        notice.setSort(notice.getSort() == null ? 0 : notice.getSort());
        notice.setStatus(notice.getStatus() == null ? 1 : notice.getStatus());
    }

    private DmsShopSku toSku(ShopSkuDTO dto) {
        if (dto == null || dto.getProductId() == null) {
            Asserts.fail("商品ID不能为空");
        }
        if (dto.getSkuName() == null || dto.getSkuName().isBlank()) {
            Asserts.fail("SKU名称不能为空");
        }
        DmsShopSku sku = new DmsShopSku();
        sku.setProductId(dto.getProductId());
        sku.setSkuNo(dto.getSkuNo() == null || dto.getSkuNo().isBlank()
                ? "SKU" + IdUtil.getSnowflakeNextId()
                : dto.getSkuNo());
        sku.setSkuName(dto.getSkuName());
        sku.setAttrsJson(normalizeJsonObject(dto.getAttrsJson()));
        sku.setImageUrl(normalizeProductImageUrl(dto.getImageUrl(), "SKU图片"));
        sku.setSalePrice(money(dto.getSalePrice()));
        sku.setMarketPrice(money(dto.getMarketPrice()));
        sku.setCostAmount(money(dto.getCostAmount()));
        sku.setPvValue(money(dto.getPvValue()));
        sku.setRepurchasePrice(dto.getRepurchasePrice() == null ? null : money(dto.getRepurchasePrice()));
        sku.setRepurchasePv(dto.getRepurchasePv() == null ? null : money(dto.getRepurchasePv()));
        DmsShopProduct product = productDao.selectById(dto.getProductId());
        if (product != null && isPvEnabled(product.getTenantId())) {
            validatePv(sku.getPvValue(), sku.getSalePrice(), "SKU PV");
        } else {
            sku.setPvValue(ZERO);
        }
        if (sku.getRepurchasePrice() != null && sku.getRepurchasePrice().compareTo(ZERO) > 0) {
            validatePv(sku.getRepurchasePv(), sku.getRepurchasePrice(), "SKU复购PV");
        }
        if (product != null && product.getMerchantId() != null) {
            if (sku.getCostAmount().compareTo(ZERO) <= 0) Asserts.fail("商户商品SKU必须填写大于0的结算价");
            if (sku.getCostAmount().compareTo(sku.getSalePrice()) > 0) Asserts.fail("商户商品SKU结算价不能高于普通售价");
            BigDecimal effectiveRepurchasePrice = sku.getRepurchasePrice() != null
                    && sku.getRepurchasePrice().compareTo(ZERO) > 0
                    ? sku.getRepurchasePrice() : money(product.getRepurchasePrice());
            if (Integer.valueOf(1).equals(product.getRepurchaseSaleEnabled())
                    && sku.getCostAmount().compareTo(effectiveRepurchasePrice) > 0) {
                Asserts.fail("商户商品SKU结算价不能高于复购价");
            }
        }
        sku.setBvValue(money(dto.getBvValue()));
        sku.setStock(dto.getStock() == null ? 0 : dto.getStock());
        sku.setSafetyStock(dto.getSafetyStock() == null ? 0 : Math.max(0, dto.getSafetyStock()));
        sku.setSalesCount(0);
        sku.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        return sku;
    }

    private String normalizeJsonObject(String raw) {
        String value = blank(raw) ? "{}" : raw.trim();
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || !node.isObject()) Asserts.fail("SKU规格属性必须是JSON对象，例如 {\"颜色\":\"红色\"}");
            if (node.size() > 20) Asserts.fail("单个SKU最多维护20项规格属性");
            node.fields().forEachRemaining(entry -> {
                if (entry.getKey().length() > 64) Asserts.fail("SKU规格属性名称不能超过64个字符");
                if (!entry.getValue().isValueNode()) Asserts.fail("SKU规格属性必须使用简单键值，不能嵌套对象或数组");
                if (entry.getValue().asText().length() > 128) Asserts.fail("SKU规格属性值不能超过128个字符");
            });
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            Asserts.fail("SKU规格属性JSON格式错误");
            return "{}";
        }
    }

    static String normalizeProductImageUrl(String raw, String label) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.chars().anyMatch(character -> Character.isISOControl(character))) {
            Asserts.fail(label + "地址包含非法字符");
        }
        try {
            URI uri = URI.create(value);
            if (uri.isAbsolute()) {
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                    Asserts.fail(label + "仅支持HTTPS地址或商城内上传地址");
                }
            } else if (!value.startsWith("/") || value.startsWith("//") || value.contains("\\")) {
                Asserts.fail(label + "仅支持HTTPS地址或商城内上传地址");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            Asserts.fail(label + "地址格式不正确");
            return null;
        }
    }

    private String normalizeProductImageList(String raw, String label) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(raw.trim());
            if (node == null || !node.isArray()) Asserts.fail(label + "必须是图片地址数组");
            for (int index = 0; index < node.size(); index++) {
                JsonNode image = node.get(index);
                if (!image.isTextual()) Asserts.fail(label + "只能包含图片地址");
                String normalized = normalizeProductImageUrl(image.asText(), label);
                if (normalized == null) Asserts.fail(label + "不能包含空地址");
                ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(index,
                        objectMapper.getNodeFactory().textNode(normalized));
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            Asserts.fail(label + "格式不正确");
            return null;
        }
    }

    private void assertPlatformCategoryManagement() {
        DmsAdminUser admin = AdminContext.get();
        if (admin != null && admin.getMerchantId() != null) {
            Asserts.fail("商品分类由平台统一维护，商户账号不能新增、修改或删除分类");
        }
    }

    private void mergeShippingProduct(Map<Long, ProductShippingContext> contexts,
                                      DmsShopProduct product, BigDecimal lineAmount) {
        ProductShippingContext context = contexts.computeIfAbsent(product.getId(), ignored -> new ProductShippingContext(product));
        context.productAmount = context.productAmount.add(lineAmount);
    }

    private BigDecimal calculateFreight(Map<Long, ProductShippingContext> contexts, ShopOrderSubmitDTO dto) {
        BigDecimal freight = ZERO;
        Map<Long, BigDecimal> templateAmounts = new LinkedHashMap<>();
        for (ProductShippingContext context : contexts.values()) {
            DmsShopProduct product = context.product;
            int freightType = product.getFreightType() == null ? 0 : product.getFreightType();
            switch (freightType) {
                case 0 -> { }
                case 1 -> freight = freight.add(money(product.getFreightAmount()));
                case 2 -> {
                    BigDecimal threshold = money(product.getFreeShippingAmount());
                    if (threshold.compareTo(ZERO) <= 0 || context.productAmount.compareTo(threshold) < 0) {
                        freight = freight.add(money(product.getFreightAmount()));
                    }
                }
                case 3 -> {
                    if (product.getFreightTemplateId() == null) Asserts.fail("商品未配置运费模板：" + product.getProductName());
                    templateAmounts.merge(product.getFreightTemplateId(), context.productAmount, BigDecimal::add);
                }
                default -> Asserts.fail("商品配送方式异常：" + product.getProductName());
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : templateAmounts.entrySet()) {
            DmsFreightTemplate template = freightTemplateDao.selectByIdScoped(resolveTenantId(null), entry.getKey());
            if (template == null || !Integer.valueOf(1).equals(template.getStatus())) {
                Asserts.fail("运费模板已停用，请联系客服");
            }
            FreightDecision decision = resolveFreightDecision(template, dto);
            if ("UNAVAILABLE".equals(decision.mode)) {
                Asserts.fail(dto.getReceiverProvince() + dto.getReceiverCity() + dto.getReceiverDistrict()
                        + " 暂不发货（模板：" + template.getTemplateName() + "）");
            }
            if ("FIXED".equals(decision.mode)) freight = freight.add(decision.amount);
        }
        return freight.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 只把允许发团队奖的商品计入奖金基数；整单优惠按商品金额比例分摊。
     * 历史订单缺少商品快照时继续使用原“商品金额减优惠”口径。
     */
    private BigDecimal productBonusBase(DmsShopOrder order, List<DmsShopOrderItem> items) {
        if (items == null || items.isEmpty()) {
            BigDecimal productAmount = order.getTotalAmount() == null
                    ? money(order.getPayAmount()).subtract(money(order.getFreightAmount()))
                    : money(order.getTotalAmount());
            return productAmount.subtract(money(order.getDiscountAmount())).max(ZERO);
        }
        BigDecimal gross = items.stream()
                .map(DmsShopOrderItem::getTotalAmount).filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal eligible = items.stream()
                .filter(this::isBonusEligibleItem)
                .map(DmsShopOrderItem::getTotalAmount).filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
        if (eligible.compareTo(ZERO) <= 0 || gross.compareTo(ZERO) <= 0) return ZERO;
        BigDecimal eligibleDiscount = money(order.getDiscountAmount())
                .multiply(eligible).divide(gross, 2, RoundingMode.HALF_UP);
        return eligible.subtract(eligibleDiscount).max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isBonusEligibleItem(DmsShopOrderItem item) {
        String mode = item == null ? null : item.getTeamBonusMode();
        return mode == null || mode.isBlank() || "INHERIT".equals(mode) || "STANDARD".equals(mode);
    }

    /**
     * 商户结算价是未来应付货款的结算依据，不再视为普通商品资料。
     * 平台人员直接修改时必须同时具备商品权限（由拦截器校验）和财务管理权限并留下原因；
     * 绑定商户账号填写的结算价由商品审核流程确认，不授予平台财务权限。
     */
    private void requireSettlementCostAuthority(boolean changed, String reason) {
        if (!changed || AdminContext.get() == null) return;
        DmsAdminUser current = AdminContext.get();
        // 商户本人填写的结算价由后续平台审核确认，不要求商户拥有平台财务权限。
        if (current.getMerchantId() != null) return;
        adminAuthService.requirePermission(current, "finance:manage");
        if (blank(reason)) Asserts.fail("修改商户结算价必须填写原因");
    }

    private boolean settlementProductTermsChanged(DmsShopProduct before, DmsShopProduct after) {
        if (before == null) return after != null && after.getMerchantId() != null;
        boolean merchantChanged = !Objects.equals(before.getMerchantId(), after.getMerchantId());
        boolean merchantProduct = before.getMerchantId() != null || after.getMerchantId() != null;
        return merchantChanged || (merchantProduct && (moneyChanged(before.getCostAmount(), after.getCostAmount())
                || !Objects.equals(before.getSettlementDelayDaysOverride(), after.getSettlementDelayDaysOverride())));
    }

    private boolean settlementPublishTermsChanged(DmsShopProduct before, DmsShopProduct after,
                                                   List<DmsShopSku> beforeSkus, List<ShopSkuDTO> afterSkus) {
        if (settlementProductTermsChanged(before, after)) return true;
        Long merchantId = after == null ? null : after.getMerchantId();
        if (merchantId == null) return false;
        Map<Long, DmsShopSku> existingById = new HashMap<>();
        for (DmsShopSku sku : beforeSkus == null ? Collections.<DmsShopSku>emptyList() : beforeSkus) {
            existingById.put(sku.getId(), sku);
        }
        for (ShopSkuDTO sku : afterSkus == null ? Collections.<ShopSkuDTO>emptyList() : afterSkus) {
            DmsShopSku existing = sku.getId() == null ? null : existingById.get(sku.getId());
            if (existing == null || moneyChanged(existing.getCostAmount(), sku.getCostAmount())) return true;
        }
        return false;
    }

    private boolean moneyChanged(BigDecimal before, BigDecimal after) {
        return money(before).compareTo(money(after)) != 0;
    }

    private void requireAggregateMerchantSkuRoute(DmsShopProduct product) {
        merchantProductReviewService.assertProductAccess(product);
        if (product.getMerchantId() != null) {
            Asserts.fail("商户SKU必须通过商品整体编辑并重新提交审核");
        }
    }

    private String settlementSnapshot(DmsShopProduct product, List<DmsShopSku> skus) {
        if (product == null) return null;
        StringBuilder snapshot = new StringBuilder()
                .append("merchantId=").append(product.getMerchantId())
                .append(";productCost=").append(money(product.getCostAmount()).setScale(2, RoundingMode.HALF_UP))
                .append(";settlementDelayDaysOverride=").append(product.getSettlementDelayDaysOverride());
        List<DmsShopSku> ordered = new ArrayList<>(skus == null ? Collections.emptyList() : skus);
        ordered.sort(java.util.Comparator.comparing(DmsShopSku::getId,
                java.util.Comparator.nullsLast(Long::compareTo)));
        if (!ordered.isEmpty()) {
            snapshot.append(";skuCosts=");
            for (int i = 0; i < ordered.size(); i++) {
                if (i > 0) snapshot.append(',');
                DmsShopSku sku = ordered.get(i);
                snapshot.append(sku.getId()).append(':')
                        .append(money(sku.getCostAmount()).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return snapshot.toString();
    }

    private void logSettlementCostChange(Long productId, String before, String after,
                                         String reason, boolean changed) {
        if (!changed) return;
        operationLogService.log("MERCHANT_SETTLEMENT", "COST_CHANGE", "SHOP_PRODUCT",
                String.valueOf(productId), before, after,
                blank(reason) ? "初始化商户结算价" : reason.trim());
    }

    private String productPriceSnapshot(DmsShopProduct product, List<DmsShopSku> skus) {
        if (product == null) return null;
        StringBuilder snapshot = new StringBuilder()
                .append("salePrice=").append(money(product.getSalePrice()).setScale(2, RoundingMode.HALF_UP))
                .append(";marketPrice=").append(money(product.getMarketPrice()).setScale(2, RoundingMode.HALF_UP));
        List<DmsShopSku> ordered = new ArrayList<>(skus == null ? Collections.emptyList() : skus);
        ordered.sort(java.util.Comparator.comparing(DmsShopSku::getId,
                java.util.Comparator.nullsLast(Long::compareTo)));
        if (!ordered.isEmpty()) {
            snapshot.append(";skuPrices=");
            for (int index = 0; index < ordered.size(); index++) {
                if (index > 0) snapshot.append(',');
                DmsShopSku sku = ordered.get(index);
                snapshot.append(sku.getId()).append(':')
                        .append(money(sku.getSalePrice()).setScale(2, RoundingMode.HALF_UP)).append('/')
                        .append(money(sku.getMarketPrice()).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return snapshot.toString();
    }

    private void logProductPriceChange(Long productId, String before, String after) {
        if (before == null || Objects.equals(before, after)) return;
        operationLogService.log("SHOP_PRODUCT", "PRICE_CHANGE", "SHOP_PRODUCT",
                String.valueOf(productId), before, after, "商品售价或市场价变更");
    }

    private String normalizeTeamBonusMode(DmsShopProduct product) {
        String mode = product.getTeamBonusMode();
        if (mode == null || mode.isBlank()) return product.getMerchantId() == null ? "INHERIT" : "NONE";
        mode = mode.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("INHERIT", "NONE", "STANDARD", "CUSTOM").contains(mode)) Asserts.fail("团队奖金模式不正确");
        return mode;
    }

    private int resolveSettlementDelayDays(DmsShopProduct product) {
        if (product == null || product.getMerchantId() == null) return 0;
        Integer override = product.getSettlementDelayDaysOverride();
        if (override != null) return Math.max(0, Math.min(365, override));
        DmsMerchant merchant = merchantDao.selectById(product.getMerchantId());
        Integer defaults = merchant == null ? null : merchant.getDefaultSettlementDays();
        return defaults == null ? 0 : Math.max(0, Math.min(365, defaults));
    }

    private void requireActiveProductMerchant(DmsShopProduct product) {
        if (product == null || product.getMerchantId() == null) return;
        DmsMerchant merchant = merchantDao.selectById(product.getMerchantId());
        if (merchant == null || !Objects.equals(product.getTenantId(), merchant.getTenantId())
                || !Integer.valueOf(1).equals(merchant.getStatus())) {
            Asserts.fail("商品所属商户已停用，当前商品不能展示、上架或下单");
        }
    }

    private FreightDecision resolveFreightDecision(DmsFreightTemplate template, ShopOrderSubmitDTO dto) {
        if (blank(dto.getReceiverProvince()) || blank(dto.getReceiverCity()) || blank(dto.getReceiverDistrict())) {
            Asserts.fail("使用运费模板的商品必须选择完整的收货省、市、区/县");
        }
        FreightDecision selected = new FreightDecision(normalizeFreightMode(template.getDefaultMode()),
                money(template.getDefaultFreightAmount()), 0);
        for (FreightTemplateRuleDTO rule : parseFreightRules(template.getRulesJson())) {
            for (List<String> path : rule.getRegionPaths() == null ? Collections.<List<String>>emptyList() : rule.getRegionPaths()) {
                int score = regionMatchScore(path, dto);
                if (score > selected.specificity) {
                    selected = new FreightDecision(normalizeFreightMode(rule.getMode()), money(rule.getFreightAmount()), score);
                }
            }
        }
        return selected;
    }

    private List<FreightTemplateRuleDTO> parseFreightRules(String rulesJson) {
        if (blank(rulesJson)) return Collections.emptyList();
        try {
            return objectMapper.readValue(rulesJson, new TypeReference<List<FreightTemplateRuleDTO>>() { });
        } catch (JsonProcessingException e) {
            Asserts.fail("运费模板规则损坏，请联系管理员");
            return Collections.emptyList();
        }
    }

    private int regionMatchScore(List<String> path, ShopOrderSubmitDTO dto) {
        if (path == null || path.isEmpty() || !sameRegion(path.get(0), dto.getReceiverProvince())) return 0;
        if (path.size() == 1) return 1;
        if (!sameRegion(path.get(1), dto.getReceiverCity())) return 0;
        if (path.size() == 2) return 2;
        return sameRegion(path.get(2), dto.getReceiverDistrict()) ? 3 : 0;
    }

    private boolean sameRegion(String configured, String actual) {
        return normalizeRegionName(configured).equals(normalizeRegionName(actual));
    }

    private String normalizeRegionName(String value) {
        if (value == null) return "";
        String normalized = value.trim().replaceAll("\\s+", "");
        for (String suffix : List.of("维吾尔自治区", "壮族自治区", "回族自治区", "特别行政区",
                "自治州", "自治区", "地区", "省", "市", "盟", "区", "县", "旗")) {
            if (normalized.length() > suffix.length() && normalized.endsWith(suffix)) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }
        return normalized;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class ProductShippingContext {
        private final DmsShopProduct product;
        private BigDecimal productAmount = ZERO;

        private ProductShippingContext(DmsShopProduct product) {
            this.product = product;
        }
    }

    private static final class FreightDecision {
        private final String mode;
        private final BigDecimal amount;
        private final int specificity;

        private FreightDecision(String mode, BigDecimal amount, int specificity) {
            this.mode = mode;
            this.amount = amount;
            this.specificity = specificity;
        }
    }

    private void restockOrder(Long orderId) {
        for (DmsShopOrderItem item : orderItemDao.selectByOrderId(orderId)) {
            if (item.getSkuId() != null) {
                skuDao.increaseStock(item.getSkuId(), item.getQuantity());
            }
            productDao.increaseStock(item.getProductId(), item.getQuantity());
        }
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order != null && ShopBusinessType.FLASH_SALE.equals(order.getBusinessType())) {
            DmsFlashSaleReservation reservation = flashSaleReservationDao.selectByOrderId(orderId);
            if (reservation != null && flashSaleReservationDao.releaseByOrder(orderId) > 0) {
                flashSaleActivityDao.increaseStock(reservation.getActivityId(), reservation.getQuantity());
                DmsFlashSaleActivity activity = flashSaleActivityDao.selectById(reservation.getActivityId());
                flashSaleStockGate.release(activity, reservation.getUserId(), reservation.getQuantity());
                operationLogService.log("FLASH_SALE_STOCK", "RELEASE", "FLASH_SALE_ACTIVITY",
                        String.valueOf(reservation.getActivityId()), null,
                        "availableStock=" + (activity == null ? "unknown" : activity.getAvailableStock()),
                        "未付款秒杀订单释放 " + reservation.getQuantity() + " 件；订单号=" + order.getOrderNo());
            }
        }
        catalogCache.invalidateAfterCommit(order == null ? DEFAULT_TENANT_ID : order.getTenantId());
    }

    private Long resolveTenantId(Long tenantId) {
        return tenantId == null ? TenantContext.getTenantId() : tenantId;
    }

    private boolean canAccessOrder(DmsShopOrder order) {
        if (order == null || !resolveTenantId(null).equals(resolveTenantId(order.getTenantId()))) return false;
        Long merchantId = currentAdminMerchantId();
        return merchantId == null || merchantId.equals(order.getMerchantId());
    }

    private Long currentAdminMerchantId() {
        return AdminContext.get() == null ? null : AdminContext.get().getMerchantId();
    }

    private boolean currentMerchantFulfillmentAllowed() {
        Long merchantId = currentAdminMerchantId();
        if (merchantId == null) return true;
        DmsMerchant merchant = merchantDao.selectById(merchantId);
        return merchant != null && "ENABLED".equals(merchant.getFulfillmentStatus());
    }

    private void assertMerchantOrderAccess(DmsShopOrder order, boolean requireFulfillment) {
        Long merchantId = currentAdminMerchantId();
        if (merchantId == null) return;
        if (order == null || !merchantId.equals(order.getMerchantId())) Asserts.fail("不能访问其他商户的订单");
        if (requireFulfillment) {
            DmsMerchant merchant = merchantDao.selectById(merchantId);
            if (merchant == null || !"ENABLED".equals(merchant.getFulfillmentStatus())) {
                Asserts.fail("商户履约权限已由平台接管或冻结，不能处理订单");
            }
        }
    }

    private void assertTenantAccess(Long tenantId) {
        Long currentTenantId = resolveTenantId(null);
        Long dataTenantId = resolveTenantId(tenantId);
        if (!currentTenantId.equals(dataTenantId)) {
            Asserts.fail("无权访问当前租户数据");
        }
    }

    private DmsTenantDisplayConfig getDisplayConfig(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        DmsTenantDisplayConfig config = displayConfigDao.selectByTenantId(resolvedTenantId);
        ObjectMapper configMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        return new TenantDisplayConfigSupport(configMapper).prepareForRead(config, resolvedTenantId);
    }

    private boolean isEnabled(Integer value) {
        return Integer.valueOf(1).equals(value);
    }

    private boolean isPvEnabled(Long tenantId) {
        DmsTenantDisplayConfig config = displayConfigDao.selectByTenantId(resolveTenantId(tenantId));
        return config == null || !Integer.valueOf(0).equals(config.getShowPv());
    }

    private BigDecimal money(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    /** SKU 配置大于 0 时覆盖商品默认 PV；否则继承默认值，并始终受当前售价上限保护。 */
    private BigDecimal resolveUnitPv(DmsShopProduct product, DmsShopSku sku, BigDecimal salePrice) {
        if (product != null && !isPvEnabled(product.getTenantId())) {
            return ZERO;
        }
        BigDecimal productPv = money(product == null ? null : product.getPvValue());
        BigDecimal skuPv = money(sku == null ? null : sku.getPvValue());
        BigDecimal configuredPv = sku != null && skuPv.compareTo(ZERO) > 0 ? skuPv : productPv;
        return limitPvToSalePrice(configuredPv, salePrice);
    }

    private BigDecimal limitPvToSalePrice(BigDecimal pv, BigDecimal salePrice) {
        BigDecimal safePrice = money(salePrice).max(ZERO);
        return money(pv).max(ZERO).min(safePrice);
    }

    private void validatePv(BigDecimal pv, BigDecimal salePrice, String fieldName) {
        BigDecimal safePv = money(pv);
        BigDecimal safePrice = money(salePrice);
        if (safePv.compareTo(ZERO) < 0) {
            Asserts.fail(fieldName + "不能小于0");
        }
        if (safePv.compareTo(safePrice.max(ZERO)) > 0) {
            Asserts.fail(fieldName + "不能超过销售价（当前销售价：" + safePrice.toPlainString() + "）");
        }
    }

    private void requireSkuSelection(DmsShopProduct product, Long skuId) {
        if (skuId == null && product != null && !skuDao.selectByProductId(product.getId(), 1).isEmpty()) {
            Asserts.fail("商品“" + product.getProductName() + "”包含规格，请先选择具体规格");
        }
    }

    @Override
    public Long resolveAgentId(Long userId) {
        if (userId == null) {
            return null;
        }
        DmsAgent agent = agentDao.selectByUserId(userId);
        return agent == null ? null : agent.getId();
    }

    @Override
    public java.util.Map<String, Object> getInviteInfo(DmsShopMember member) {
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        // 已经进入会员关系体系的老账号曾经生成过第二套邀请码。继续展示关系体系中的
        // 历史邀请码，避免已经发出的二维码和注册链接失效；新账号激活后两处邀请码一致。
        DmsAgent selfAgent = agentDao.selectByUserId(member.getUserId());
        String publicInviteCode = selfAgent != null && selfAgent.getInviteCode() != null
                && !selfAgent.getInviteCode().isBlank()
                ? selfAgent.getInviteCode() : member.getInviteCode();
        info.put("inviteCode", publicInviteCode);
        info.put("userId", member.getUserId());

        List<DmsShopMember> directAccounts = memberDao.selectByInviterId(member.getUserId());
        if (directAccounts == null) directAccounts = List.of();
        long directMembers = directAccounts.stream()
                .map(item -> agentDao.selectByUserId(item.getUserId()))
                .filter(java.util.Objects::nonNull)
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .count();
        // inviteCount 保留给旧前端；两项都严格只统计本人直接邀请的一代。
        info.put("inviteCount", directMembers);
        info.put("directMemberCount", directMembers);
        info.put("directAccountCount", directAccounts.size());

        return info;
    }

    @Override
    public Map<String, Object> getInviterPreview(String inviteCode) {
        Map<String, Object> preview = new java.util.HashMap<>();
        if (inviteCode == null || inviteCode.isBlank()) {
            preview.put("valid", false);
            preview.put("message", "请输入邀请码");
            return preview;
        }
        String normalizedCode = inviteCode.trim().toUpperCase(Locale.ROOT);
        DmsShopMember inviter = memberDao.selectByInviteCode(normalizedCode);
        if (inviter == null) {
            // 兼容已经进入会员关系体系的历史账号：旧版本曾在 dms_agent 中重新生成邀请码。
            DmsAgent legacyAgent = agentDao.selectByInviteCode(normalizedCode);
            if (legacyAgent != null && Integer.valueOf(1).equals(legacyAgent.getStatus())) {
                inviter = memberDao.selectByUserId(legacyAgent.getUserId());
            }
        }
        if (inviter == null || !Integer.valueOf(1).equals(inviter.getStatus())) {
            preview.put("valid", false);
            preview.put("inviteCode", normalizedCode);
            preview.put("message", "未找到该邀请码，请向邀请人核对");
            return preview;
        }
        String displayName = inviter.getNickname();
        if (displayName == null || displayName.isBlank()) displayName = inviter.getUsername();
        if (displayName == null || displayName.isBlank()) displayName = "商城会员";
        preview.put("valid", true);
        preview.put("inviteCode", normalizedCode);
        preview.put("nickname", maskInviterName(displayName));
        return preview;
    }

    private String maskInviterName(String name) {
        String value = name.trim();
        if (value.length() <= 1) return "*";
        if (value.length() == 2) return value.substring(0, 1) + "*";
        return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
    }

    private void notifyOrderChanged(DmsShopOrder order, String changeType) {
        if (orderRealtimeService != null) orderRealtimeService.orderChanged(order, changeType);
    }
}
