package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.dao.DmsMerchantProductReviewDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopSkuDao;
import com.macro.mall.distribution.dto.MerchantProductReviewDecisionDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsMerchantProductReview;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopSku;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.MerchantProductReviewService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.ShopCatalogCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MerchantProductReviewServiceImpl implements MerchantProductReviewService {
    private static final Set<String> REVIEW_STATES = Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED");
    private final DmsMerchantProductReviewDao reviewDao;
    private final DmsShopProductDao productDao;
    private final DmsShopSkuDao skuDao;
    private final DmsMerchantDao merchantDao;
    private final AdminAuthService adminAuthService;
    private final OperationLogService operationLogService;
    private final ShopCatalogCacheService catalogCache;
    private final ObjectMapper objectMapper;

    @Override
    public Long currentMerchantId() {
        DmsAdminUser admin = AdminContext.get();
        return admin == null ? null : admin.getMerchantId();
    }

    @Override
    public boolean currentMerchantOwns(Long merchantId) {
        Long current = currentMerchantId();
        return current != null && Objects.equals(current, merchantId);
    }

    @Override
    public void bindMerchantForWrite(DmsShopProduct product, DmsShopProduct existing) {
        Long current = currentMerchantId();
        if (current == null) return;
        if (existing != null && !Objects.equals(current, existing.getMerchantId())) Asserts.fail("不能访问其他商户的商品");
        if (product.getMerchantId() != null && !Objects.equals(current, product.getMerchantId())) Asserts.fail("不能把商品绑定到其他商户");
        DmsMerchant merchant = merchantDao.selectById(current);
        if (merchant == null || !Integer.valueOf(1).equals(merchant.getStatus())) Asserts.fail("绑定商户不存在或已停用");
        product.setMerchantId(current);
        product.setMerchantName(merchant.getMerchantName());
    }

    @Override
    public void prepareCreatedProduct(DmsShopProduct product) {
        product.setMerchantReviewVersion(0);
        if (product.getMerchantId() == null) return;
        product.setStatus(0);
        product.setMerchantReviewStatus("DRAFT");
        clearDecision(product);
    }

    @Override
    public void prepareUpdatedProduct(DmsShopProduct existing, DmsShopProduct product) {
        assertProductAccess(existing);
        if (existing.getMerchantId() == null && product.getMerchantId() == null) {
            product.setMerchantReviewStatus(existing.getMerchantReviewStatus());
            product.setMerchantReviewVersion(existing.getMerchantReviewVersion() == null ? 0 : existing.getMerchantReviewVersion());
            product.setMerchantReviewRemark(existing.getMerchantReviewRemark());
            product.setMerchantReviewSubmittedAt(existing.getMerchantReviewSubmittedAt());
            product.setMerchantReviewedAt(existing.getMerchantReviewedAt());
            product.setMerchantReviewerId(existing.getMerchantReviewerId());
            product.setMerchantReviewerName(existing.getMerchantReviewerName());
            return;
        }
        if (!Objects.equals(existing.getMerchantId(), product.getMerchantId())) {
            DmsAdminUser admin = AdminContext.get();
            if (admin != null) adminAuthService.requirePermission(admin, "finance:manage");
        }
        if (Integer.valueOf(1).equals(existing.getStatus())) {
            Asserts.fail("商户商品已上架，请先下架后再修改商品资料或价格");
        }
        if ("PENDING".equals(existing.getMerchantReviewStatus())) Asserts.fail("商品正在审核，不能修改；如需调整请先由审核人员驳回");
        product.setStatus(0);
        product.setMerchantReviewStatus("DRAFT");
        product.setMerchantReviewVersion(existing.getMerchantReviewVersion() == null ? 0 : existing.getMerchantReviewVersion());
        clearDecision(product);
    }

    @Override
    public void assertProductAccess(DmsShopProduct product) {
        Long current = currentMerchantId();
        if (current != null && (product == null || !Objects.equals(current, product.getMerchantId()))) {
            Asserts.fail("不能访问其他商户的商品");
        }
    }

    @Override
    public CommonPage<DmsMerchantProductReview> list(String status, String keyword, Integer pageNum, Integer pageSize) {
        String normalized = normalizeStatus(status, true);
        int page = pageNum == null ? 1 : Math.max(1, pageNum);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
        PageHelper.startPage(page, size);
        return CommonPage.restPage(reviewDao.selectList(TenantContext.getTenantId(), currentMerchantId(), normalized, trim(keyword)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantProductReview submit(Long productId) {
        DmsShopProduct product = productDao.selectByIdForUpdate(productId);
        if (product == null || product.getMerchantId() == null) Asserts.fail("只有商户商品需要提交审核");
        assertProductAccess(product);
        requireActiveMerchant(product.getMerchantId());
        if (Integer.valueOf(1).equals(product.getStatus())) Asserts.fail("请先下架商品再提交审核");
        if ("PENDING".equals(product.getMerchantReviewStatus())) Asserts.fail("商品已经在审核中，请勿重复提交");
        if (money(product.getSalePrice()).compareTo(BigDecimal.ZERO) <= 0) Asserts.fail("销售价必须大于0");
        if (money(product.getCostAmount()).compareTo(BigDecimal.ZERO) <= 0) Asserts.fail("结算价必须大于0");
        if (money(product.getCostAmount()).compareTo(money(product.getSalePrice())) > 0) {
            Asserts.fail("结算价不能高于销售价");
        }
        List<DmsShopSku> skus = skuDao.selectByProductId(productId, null);
        for (DmsShopSku sku : skus) {
            if (!Integer.valueOf(1).equals(sku.getStatus())) continue;
            if (money(sku.getSalePrice()).compareTo(BigDecimal.ZERO) <= 0 || money(sku.getCostAmount()).compareTo(BigDecimal.ZERO) <= 0) {
                Asserts.fail("启用中的SKU必须填写大于0的销售价和结算价");
            }
            if (money(sku.getCostAmount()).compareTo(money(sku.getSalePrice())) > 0) {
                Asserts.fail("SKU结算价不能高于对应销售价");
            }
        }
        int version = (product.getMerchantReviewVersion() == null ? 0 : product.getMerchantReviewVersion()) + 1;
        LocalDateTime now = LocalDateTime.now();
        DmsAdminUser admin = AdminContext.get();
        DmsMerchantProductReview review = new DmsMerchantProductReview();
        review.setTenantId(product.getTenantId());
        review.setMerchantId(product.getMerchantId());
        review.setMerchantName(product.getMerchantName());
        review.setProductId(product.getId());
        review.setReviewVersion(version);
        review.setReviewType(version == 1 ? "INITIAL" : "CHANGE");
        review.setStatus("PENDING");
        review.setProductNo(product.getProductNo());
        review.setProductName(product.getProductName());
        review.setSalePrice(money(product.getSalePrice()));
        review.setSettlementPrice(money(product.getCostAmount()));
        review.setSkuCount((int) skus.stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).count());
        review.setProductSnapshot(snapshot(product, skus));
        review.setSubmitterId(admin == null ? null : admin.getId());
        review.setSubmitterName(adminName(admin));
        review.setSubmittedAt(now);
        if (productDao.markReviewSubmitted(productId, version, now) != 1) Asserts.fail("商品状态已变化，请刷新后重试");
        reviewDao.insert(review);
        operationLogService.log("MERCHANT_PRODUCT_REVIEW", "SUBMIT", "SHOP_PRODUCT", String.valueOf(productId),
                null, "version=" + version, "提交商户商品审核");
        return reviewDao.selectById(review.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantProductReview decide(Long reviewId, MerchantProductReviewDecisionDTO dto) {
        DmsAdminUser admin = AdminContext.get();
        if (admin == null) Asserts.fail("后台登录已失效，请重新登录");
        adminAuthService.requirePermission(admin, "shop:product-review");
        if (admin.getMerchantId() != null) Asserts.fail("商户工作台账号不能审核商品");
        DmsMerchantProductReview review = reviewDao.selectByIdForUpdate(reviewId);
        if (review == null || !TenantContext.getTenantId().equals(review.getTenantId())) Asserts.fail("商品审核记录不存在");
        if (!"PENDING".equals(review.getStatus())) Asserts.fail("该商品审核已经处理，请勿重复操作");
        boolean approved = dto != null && Boolean.TRUE.equals(dto.getApproved());
        String remark = trim(dto == null ? null : dto.getRemark());
        if (!approved && remark == null) Asserts.fail("驳回时必须填写原因");
        DmsShopProduct product = productDao.selectByIdForUpdate(review.getProductId());
        if (product == null || !Objects.equals(product.getMerchantReviewVersion(), review.getReviewVersion())
                || !"PENDING".equals(product.getMerchantReviewStatus())) Asserts.fail("商品资料已变化，本次审核不能继续");
        if (approved) requireActiveMerchant(product.getMerchantId());
        LocalDateTime now = LocalDateTime.now();
        String result = approved ? "APPROVED" : "REJECTED";
        review.setStatus(result);
        review.setReviewerId(admin.getId());
        review.setReviewerName(adminName(admin));
        review.setReviewRemark(remark);
        review.setReviewedAt(now);
        if (reviewDao.updateDecision(review) != 1) Asserts.fail("该审核已被其他人员处理");
        if (productDao.markReviewDecision(product.getId(), review.getReviewVersion(), result,
                approved ? 1 : 0, remark, admin.getId(), adminName(admin), now) != 1) {
            Asserts.fail("商品状态已变化，本次审核不能继续");
        }
        operationLogService.log("MERCHANT_PRODUCT_REVIEW", approved ? "APPROVE" : "REJECT", "SHOP_PRODUCT",
                String.valueOf(product.getId()), "version=" + review.getReviewVersion(), result,
                approved ? "审核通过并自动上架；结算价=" + review.getSettlementPrice() : "审核驳回：" + remark);
        catalogCache.invalidateAfterCommit(product.getTenantId());
        return reviewDao.selectById(reviewId);
    }

    private void clearDecision(DmsShopProduct product) {
        product.setMerchantReviewRemark(null);
        product.setMerchantReviewSubmittedAt(null);
        product.setMerchantReviewedAt(null);
        product.setMerchantReviewerId(null);
        product.setMerchantReviewerName(null);
    }

    private void requireActiveMerchant(Long merchantId) {
        DmsMerchant merchant = merchantId == null ? null : merchantDao.selectById(merchantId);
        if (merchant == null || !Integer.valueOf(1).equals(merchant.getStatus())) {
            Asserts.fail("商品所属商户不存在或已停用，不能提交或通过审核");
        }
    }

    private String snapshot(DmsShopProduct product, List<DmsShopSku> skus) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("product", product);
        value.put("skus", skus);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            Asserts.fail("商品审核快照生成失败");
            return "{}";
        }
    }

    private String normalizeStatus(String value, boolean nullable) {
        String normalized = trim(value);
        if (normalized == null) return nullable ? null : "DRAFT";
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!REVIEW_STATES.contains(normalized)) Asserts.fail("商品审核状态不正确");
        return normalized;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String adminName(DmsAdminUser admin) {
        if (admin == null) return null;
        return trim(admin.getNickname()) == null ? admin.getUsername() : admin.getNickname().trim();
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
