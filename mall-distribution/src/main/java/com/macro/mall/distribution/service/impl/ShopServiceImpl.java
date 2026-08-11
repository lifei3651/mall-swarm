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
import com.macro.mall.distribution.dto.OrderFinanceDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopSkuDTO;
import com.macro.mall.distribution.dto.ProductPublishDTO;
import com.macro.mall.distribution.dto.FreightTemplateRuleDTO;
import com.macro.mall.distribution.dto.FreightTemplateSaveDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.service.PaymentVerificationService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.ShopCatalogCacheService;
import com.macro.mall.distribution.util.ShopOrderNoGenerator;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.OrderRelationSnapshotService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.vo.OrderFinanceVO;
import com.macro.mall.distribution.vo.ShopHomeVO;
import com.macro.mall.distribution.vo.ShopLegalConfigVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.ShopOrderStatusSummaryVO;
import com.macro.mall.distribution.vo.ShopProductDetailVO;
import com.macro.mall.distribution.vo.ShopProfileVO;
import com.macro.mall.distribution.vo.FreightQuoteVO;
import com.macro.mall.distribution.vo.PurchaseLimitCheckVO;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.github.pagehelper.PageHelper;
import cn.hutool.crypto.digest.DigestUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final DmsShopProductDao productDao;
    private final DmsShopCategoryDao categoryDao;
    private final DmsShopBannerDao bannerDao;
    private final DmsShopNoticeDao noticeDao;
    private final DmsShopSkuDao skuDao;
    private final DmsFreightTemplateDao freightTemplateDao;
    private final DmsShopOrderDao orderDao;
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
    private final DmsMigrationBaselineDao migrationBaselineDao;
    private final DistributionAuditService auditService;
    private final PerformanceService performanceService;
    private final CommissionService commissionService;
    private final MemberAssetService memberAssetService;
    private final OrderRelationSnapshotService relationSnapshotService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;
    private final ShopAuthService authService;
    private final PaymentVerificationService paymentVerificationService;
    private final com.macro.mall.distribution.service.ErpIntegrationService erpIntegrationService;
    private final com.macro.mall.distribution.service.OrderShipmentService orderShipmentService;
    private final ObjectMapper objectMapper;
    private final ShopCatalogCacheService catalogCache;

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
        List<DmsShopCategory> categories = categoryDao.selectHomeCategories(resolvedTenantId);
        vo.setCategoryList(categories);
        vo.setCategories(categories.isEmpty()
                ? productDao.selectCategories(resolvedTenantId)
                : categories.stream().map(DmsShopCategory::getCategoryName).toList());
        vo.setBanners(bannerDao.selectActive(resolvedTenantId));
        vo.setNotices(noticeDao.selectActive(resolvedTenantId));
        List<DmsShopProduct> featuredProducts = productDao.selectList(resolvedTenantId, null, null, 1, null);
        DmsTenantDisplayConfig displayConfig = getDisplayConfig(resolvedTenantId);
        if (!isEnabled(displayConfig.getShowPv())) {
            featuredProducts.forEach(product -> product.setPvValue(ZERO));
        }
        vo.setFeaturedProducts(featuredProducts);
        vo.setDistributionSettings(auditService.getSettings());
        vo.setDisplayConfig(displayConfig);
        vo.setLegalConfig(ShopLegalConfigVO.from(tenant));
        return vo;
    }

    @Override
    public List<DmsShopProduct> listProducts(Long tenantId, String keyword, String categoryName, Integer status, String stockStatus) {
        return productDao.selectList(resolveTenantId(tenantId), keyword, categoryName, status, stockStatus);
    }

    @Override
    public CommonPage<DmsShopProduct> listProductPage(Long tenantId, String keyword, String categoryName,
                                                      Integer status, String stockStatus,
                                                      Integer pageNum, Integer pageSize) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        int resolvedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 12 : Math.min(pageSize, 100);
        String parameters = String.join("|",
                String.valueOf(keyword), String.valueOf(categoryName), String.valueOf(status),
                String.valueOf(stockStatus), String.valueOf(resolvedPageNum), String.valueOf(resolvedPageSize));
        String cacheKey = "products:" + DigestUtil.sha256Hex(parameters);
        return catalogCache.get(resolvedTenantId, cacheKey, CommonPage.class, productCacheTtlSeconds, () -> {
            PageHelper.startPage(resolvedPageNum, resolvedPageSize);
            return CommonPage.restPage(productDao.selectList(
                    resolvedTenantId, keyword, categoryName, status, stockStatus));
        });
    }

    @Override
    public List<String> listCategories(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        return catalogCache.get(resolvedTenantId, "category-names", List.class, categoryCacheTtlSeconds, () -> {
            List<DmsShopCategory> categories = categoryDao.selectList(resolvedTenantId, 1);
            return categories.isEmpty()
                    ? productDao.selectCategories(resolvedTenantId)
                    : categories.stream().map(DmsShopCategory::getCategoryName).toList();
        });
    }

    @Override
    public List<DmsShopCategory> listFrontCategories(Long tenantId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        return catalogCache.get(resolvedTenantId, "front-categories", List.class, categoryCacheTtlSeconds,
                () -> categoryDao.selectList(resolvedTenantId, 1));
    }

    @Override
    public List<DmsShopCategory> listAdminCategories(Long tenantId, Integer status) {
        return categoryDao.selectList(resolveTenantId(tenantId), status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopCategory saveCategory(DmsShopCategory category) {
        fillCategoryDefaults(category);
        assertTenantAccess(category.getTenantId());
        assertCategoryNameAvailable(category.getTenantId(), category.getCategoryName(), null);
        categoryDao.insert(category);
        catalogCache.invalidateAfterCommit(category.getTenantId());
        return categoryDao.selectById(category.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopCategory updateCategory(Long id, DmsShopCategory category) {
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
        fillBannerDefaults(banner);
        banner.setTenantId(exists.getTenantId());
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
        boolean updated = bannerDao.updateStatus(id, status == null ? 1 : status) > 0;
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
        ShopProductDetailVO vo = new ShopProductDetailVO();
        vo.setProduct(product);
        vo.setSkus(skus);
        vo.setDisplayConfig(displayConfig);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopProduct saveProduct(DmsShopProduct product) {
        applyShippingAddress(product, true);
        fillProductDefaults(product);
        assertTenantAccess(product.getTenantId());
        productDao.insert(product);
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
        if (product.getShippingAddressId() == null) product.setShippingAddressId(exists.getShippingAddressId());
        if (product.getReturnAddressId() == null) product.setReturnAddressId(exists.getReturnAddressId());
        applyShippingAddress(product, false);
        fillProductDefaults(product);
        productDao.update(product);
        catalogCache.invalidateAfterCommit(product.getTenantId());
        return productDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopProduct publishProduct(Long id, ProductPublishDTO dto) {
        if (dto == null || dto.getProduct() == null) {
            Asserts.fail("商品信息不能为空");
        }
        DmsShopProduct product = dto.getProduct();
        if (id == null) {
            applyShippingAddress(product, true);
            fillProductDefaults(product);
            assertTenantAccess(product.getTenantId());
            productDao.insert(product);
            id = product.getId();
        } else {
            DmsShopProduct existing = productDao.selectById(id);
            if (existing == null) Asserts.fail("商品不存在");
            assertTenantAccess(existing.getTenantId());
            product.setId(id);
            product.setTenantId(existing.getTenantId());
            if (product.getShippingAddressId() == null) product.setShippingAddressId(existing.getShippingAddressId());
            if (product.getReturnAddressId() == null) product.setReturnAddressId(existing.getReturnAddressId());
            applyShippingAddress(product, false);
            fillProductDefaults(product);
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
        catalogCache.invalidateAfterCommit(product.getTenantId());
        return productDao.selectById(id);
    }

    /** 将地址簿中的发货地址同步为商品快照；地址簿后续变更不会改写历史商品快照。 */
    private void applyShippingAddress(DmsShopProduct product, boolean useDefaultWhenMissing) {
        Long tenantId = product.getTenantId() == null ? DEFAULT_TENANT_ID : product.getTenantId();
        DmsShopServiceAddress address = product.getShippingAddressId() == null && useDefaultWhenMissing
                ? serviceAddressDao.selectDefault(tenantId, 1)
                : product.getShippingAddressId() == null ? null : serviceAddressDao.selectById(product.getShippingAddressId());
        if (address == null) return;
        if (!tenantId.equals(address.getTenantId()) || !Integer.valueOf(1).equals(address.getAddressType())
                || !Integer.valueOf(1).equals(address.getStatus())) {
            Asserts.fail("发货地址不存在或已停用");
        }
        product.setShippingAddressId(address.getId());
        product.setDeliveryProvince(address.getProvince());
        product.setDeliveryCity(address.getCity());
        product.setDeliveryDistrict(address.getDistrict());
        product.setDeliveryAddress(joinServiceAddress(address));
    }

    private String joinServiceAddress(DmsShopServiceAddress address) {
        return java.util.stream.Stream.of(address.getProvince(), address.getCity(), address.getDistrict(), address.getDetailAddress())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    @Override
    public boolean updateProductStatus(Long id, Integer status) {
        DmsShopProduct product = productDao.selectById(id);
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(product.getTenantId());
        boolean updated = productDao.updateStatus(id, status == null ? 1 : status) > 0;
        if (updated) catalogCache.invalidateAfterCommit(product.getTenantId());
        return updated;
    }

    @Override
    public List<DmsShopSku> listSkus(Long productId, Integer status) {
        DmsShopProduct product = productDao.selectById(productId);
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        assertTenantAccess(product.getTenantId());
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
        skuDao.insert(sku);
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
        skuDao.update(sku);
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
        boolean updated = skuDao.updateStatus(id, status == null ? 1 : status) > 0;
        if (updated) catalogCache.invalidateAfterCommit(product.getTenantId());
        return updated;
    }

    @Override
    public List<DmsFreightTemplate> listFreightTemplates(Long tenantId, Integer status) {
        return freightTemplateDao.selectList(resolveTenantId(tenantId), status);
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
        freightTemplateDao.update(template);
        return freightTemplateDao.selectById(id);
    }

    @Override
    public FreightQuoteVO quoteFreight(ShopOrderSubmitDTO dto, DmsShopMember member) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            Asserts.fail("订单商品不能为空");
        }
        if (member != null) dto.setUserId(member.getUserId());
        fillAddress(dto, member);
        Map<Long, ProductShippingContext> shippingProducts = new LinkedHashMap<>();
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
            validatePurchaseLimit(product, dto.getUserId(), requestedQuantity, existingPurchaseQuantities);
            requireSkuSelection(product, item.getSkuId());
            BigDecimal price = money(product.getSalePrice());
            if (item.getSkuId() != null) {
                DmsShopSku sku = skuDao.selectById(item.getSkuId());
                if (sku == null || !product.getId().equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                    Asserts.fail("SKU不存在或已下架：" + product.getProductName());
                }
                price = money(sku.getSalePrice());
            }
            BigDecimal lineAmount = price.multiply(BigDecimal.valueOf(quantity));
            productAmount = productAmount.add(lineAmount);
            mergeShippingProduct(shippingProducts, product, lineAmount);
        }
        BigDecimal freight = calculateFreight(shippingProducts, dto);
        return new FreightQuoteVO(productAmount, freight, productAmount.add(freight));
    }

    @Override
    public PurchaseLimitCheckVO checkPurchaseLimit(Long productId, Integer quantity, DmsShopMember member) {
        if (productId == null) {
            Asserts.fail("商品ID不能为空");
        }
        if (member == null || member.getUserId() == null) {
            Asserts.fail("请先登录后再加入购物车");
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
        if (member != null) {
            dto.setUserId(member.getUserId());
        }
        fillAddress(dto, member);
        normalizeManualAddress(dto);
        validateSubmit(dto);
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
        Map<Long, Integer> requestedPurchaseQuantities = new HashMap<>();
        Map<Long, Integer> existingPurchaseQuantities = new HashMap<>();

        for (ShopOrderItemDTO itemDTO : dto.getItems()) {
            Integer quantity = itemDTO.getQuantity() == null || itemDTO.getQuantity() <= 0 ? 1 : itemDTO.getQuantity();
            DmsShopProduct product = productDao.selectById(itemDTO.getProductId());
            if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
                Asserts.fail("商品不存在或已下架");
            }
            assertTenantAccess(product.getTenantId());
            int requestedQuantity = requestedPurchaseQuantities.merge(product.getId(), quantity, Integer::sum);
            validatePurchaseLimit(product, dto.getUserId(), requestedQuantity, existingPurchaseQuantities);
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

            BigDecimal price = sku == null ? money(product.getSalePrice()) : money(sku.getSalePrice());
            // 金额、PV、库存全部以服务端实时商品数据为准，客户端传值不参与计算。
            // 旧数据即使存在 PV 大于售价，也会在这里被强制限制，不能进入订单快照。
            BigDecimal pv = resolveUnitPv(product, sku, price);
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
            orderItems.add(orderItem);
        }

        BigDecimal freightAmount = calculateFreight(shippingProducts, dto);
        BigDecimal payAmount = totalAmount.add(freightAmount);
        paymentVerificationService.verifyIfRequired(member, payAmount, dto.getSmsCode());

        DmsShopOrder order = new DmsShopOrder();
        order.setId(orderId);
        order.setOrderNo(orderNo);
        order.setTenantId(tenantId);
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
        financeDTO.setRemark("商城前台订单");
        OrderFinanceVO finance = auditService.upsertOrderFinance(financeDTO);

        ShopOrderVO vo = new ShopOrderVO();
        vo.setOrder(orderDao.selectById(orderId));
        fillMemberAccount(vo, vo.getOrder());
        vo.setItems(orderItemDao.selectByOrderId(orderId));
        vo.setShipments(Collections.emptyList());
        vo.setFinance(finance);
        vo.setAfterSales(Collections.emptyList());
        vo.setPendingReviewCount(0);
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
        ShopOrderVO vo = new ShopOrderVO();
        vo.setOrder(order);
        fillMemberAccount(vo, order);
        vo.setItems(orderItemDao.selectByOrderId(orderId));
        fillShipments(vo, order);
        vo.setFinance(auditService.getOrderFinanceDetail(orderId).getFinance());
        vo.setAfterSales(hydrateAfterSales(afterSaleDao.selectByOrderId(orderId)));
        vo.setPendingReviewCount(pendingReviewCount(order));
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
            orders = orderDao.selectList(null, null, null);
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
        return orderDao.selectList(keyword, status, normalizeAdminOrderState(orderState)).stream().filter(this::canAccessOrder).map(order -> {
            ShopOrderVO vo = new ShopOrderVO();
            vo.setOrder(order);
            fillMemberAccount(vo, order);
            vo.setItems(orderItemDao.selectByOrderId(order.getId()));
            fillShipments(vo, order);
            vo.setFinance(auditService.getOrderFinanceDetail(order.getId()).getFinance());
            vo.setAfterSales(hydrateAfterSales(afterSaleDao.selectByOrderId(order.getId())));
            vo.setPendingReviewCount(pendingReviewCount(order));
            vo.setDisplayConfig(getDisplayConfig(order.getTenantId()));
            return vo;
        }).toList();
    }

    @Override
    public ShopOrderStatusSummaryVO getAdminOrderWorkSummary() {
        ShopOrderStatusSummaryVO summary = orderDao.selectAdminWorkSummary(resolveTenantId(null));
        return summary == null ? new ShopOrderStatusSummaryVO() : summary;
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
        DmsShopOrder order = orderDao.selectById(orderId);
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

        int updated = orderDao.markPaid(orderId, payType);
        if (updated > 0) {
            // 所有商品一视同仁：注册账号完成首笔有效支付后成为一级会员。
            // 后续卡级只由本人及无限层团队累计有效商品件数和直推/部门条件自动判断，商品不能指定卡级。
            if (order.getUserId() != null) {
                com.macro.mall.distribution.vo.AgentInfoVO activated = authService.activateMember(
                        order.getUserId(), 1, "完成首笔有效支付订单，成为会员，订单：" + order.getOrderNo());
                order.setAgentId(activated.getId());
                orderDao.updateAgentId(orderId, activated.getId());
            }
            // 必须先冻结支付瞬间的完整关系链，后续移线不能改变该订单归属。
            relationSnapshotService.capture(order);
        }
        if (updated > 0) {
            DmsAgent agent = agentDao.selectByUserId(order.getUserId());
            if (agent != null) {
                LocalDateTime paidTime = LocalDateTime.now();
                // 运费只计入订单实付和财务，不计入业绩、累计单量金额或奖金基数。
                BigDecimal bonusBaseAmount = productBonusBase(order);
                performanceService.recordOrderPerformance(
                        order.getId(), order.getOrderNo(), bonusBaseAmount, Math.max(1,
                                orderItemDao.selectByOrderId(order.getId()).stream()
                                        .map(DmsShopOrderItem::getQuantity).filter(java.util.Objects::nonNull)
                                        .mapToInt(Integer::intValue).sum()), agent.getUserId(), paidTime);
                commissionService.calculateAndRecordCommission(
                        order.getTenantId(), order.getId(), order.getOrderNo(), bonusBaseAmount,
                        agent.getUserId(), agent.getAgentName());
                // 奖金生成后立即回写订单财务，实时计算单笔奖金拨出率和利润风险。
                auditService.refreshOrderFinance(order.getId(), order.getOrderNo(), order.getPayAmount());
                // 只建立待结算凭证；真实余额仍需确认收货满7天且无待处理售后后才到账。
                orderBalanceAllocationService.prepareForOrder(order.getId());
            }
        }
        if (updated > 0) {
            erpIntegrationService.queueOrderPush(orderDao.selectById(orderId));
        }
        return getOrder(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId, DmsShopMember member) {
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        if (member != null && !member.getUserId().equals(order.getUserId())) {
            Asserts.fail("不能取消他人的订单");
        }
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            Asserts.fail("当前订单状态不能取消");
        }
        int updated = orderDao.cancel(orderId);
        if (updated > 0) {
            restockOrder(orderId);
        }
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int closeExpiredPendingOrders(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, pendingOrderTimeoutMinutes));
        int closed = 0;
        for (Long orderId : orderDao.selectExpiredPendingIds(cutoff, safeLimit)) {
            DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
            if (order != null && Integer.valueOf(0).equals(order.getStatus())
                    && order.getCreateTime() != null && !order.getCreateTime().isAfter(cutoff)
                    && orderDao.closePending(orderId) > 0) {
                restockOrder(orderId);
                closed++;
            }
        }
        return closed;
    }

    @Override
    public boolean shipOrder(Long orderId, ShopOrderShipDTO dto) {
        return orderShipmentService.shipOrder(orderId, dto);
    }

    @Override
    public boolean confirmReceive(Long orderId, DmsShopMember member) {
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null) {
            Asserts.fail("订单不存在");
        }
        assertTenantAccess(order.getTenantId());
        if (member != null && !member.getUserId().equals(order.getUserId())) {
            Asserts.fail("不能确认他人的订单");
        }
        return orderDao.confirmReceive(orderId) > 0;
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
        product.setProductNo(product.getProductNo() == null || product.getProductNo().isBlank()
                ? "SPU" + IdUtil.getSnowflakeNextId()
                : product.getProductNo());
        product.setSalePrice(money(product.getSalePrice()));
        product.setMarketPrice(money(product.getMarketPrice()));
        product.setCostAmount(money(product.getCostAmount()));
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
                if (product.getFreightAmount().compareTo(ZERO) < 0) Asserts.fail("未满额运费不能小于0");
                if (product.getFreeShippingAmount() == null || product.getFreeShippingAmount().compareTo(ZERO) <= 0) {
                    Asserts.fail("请填写满额包邮门槛");
                }
                product.setFreightTemplateId(null);
                product.setFreightTemplateName(null);
            }
            case 3 -> {
                DmsFreightTemplate template = product.getFreightTemplateId() == null
                        ? null : freightTemplateDao.selectById(product.getFreightTemplateId());
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
        for (FreightTemplateRuleDTO rule : rules) validateFreightRule(rule);
        DmsFreightTemplate template = new DmsFreightTemplate();
        template.setId(id);
        template.setTenantId(resolveTenantId(dto.getTenantId()));
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
        boolean duplicated = categoryDao.selectList(tenantId, null).stream()
                .anyMatch(item -> !java.util.Objects.equals(item.getId(), excludeId)
                        && item.getCategoryName() != null
                        && item.getCategoryName().equalsIgnoreCase(categoryName));
        if (duplicated) Asserts.fail("分类名称已存在，请勿重复添加");
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
        banner.setLinkType(banner.getLinkType() == null || banner.getLinkType().isBlank() ? "NONE" : banner.getLinkType().trim());
        banner.setSort(banner.getSort() == null ? 0 : banner.getSort());
        banner.setStatus(banner.getStatus() == null ? 1 : banner.getStatus());
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
        sku.setImageUrl(dto.getImageUrl());
        sku.setSalePrice(money(dto.getSalePrice()));
        sku.setMarketPrice(money(dto.getMarketPrice()));
        sku.setCostAmount(money(dto.getCostAmount()));
        sku.setPvValue(money(dto.getPvValue()));
        DmsShopProduct product = productDao.selectById(dto.getProductId());
        if (product != null && isPvEnabled(product.getTenantId())) {
            validatePv(sku.getPvValue(), sku.getSalePrice(), "SKU PV");
        } else {
            sku.setPvValue(ZERO);
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
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            Asserts.fail("SKU规格属性JSON格式错误");
            return "{}";
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
            DmsFreightTemplate template = freightTemplateDao.selectById(entry.getKey());
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

    /** 新订单直接使用商品合计；兼容旧订单时由实付减掉运费，避免运费进入奖金。 */
    private BigDecimal productBonusBase(DmsShopOrder order) {
        BigDecimal productAmount = order.getTotalAmount() == null
                ? money(order.getPayAmount()).subtract(money(order.getFreightAmount()))
                : money(order.getTotalAmount());
        return productAmount.subtract(money(order.getDiscountAmount())).max(ZERO);
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
        if (path == null || path.isEmpty() || !path.get(0).equals(dto.getReceiverProvince())) return 0;
        if (path.size() == 1) return 1;
        if (!path.get(1).equals(dto.getReceiverCity())) return 0;
        if (path.size() == 2) return 2;
        return path.get(2).equals(dto.getReceiverDistrict()) ? 3 : 0;
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
        catalogCache.invalidateAfterCommit(order == null ? DEFAULT_TENANT_ID : order.getTenantId());
    }

    private Long resolveTenantId(Long tenantId) {
        return tenantId == null ? TenantContext.getTenantId() : tenantId;
    }

    private boolean canAccessOrder(DmsShopOrder order) {
        return order != null && resolveTenantId(null).equals(resolveTenantId(order.getTenantId()));
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
}
