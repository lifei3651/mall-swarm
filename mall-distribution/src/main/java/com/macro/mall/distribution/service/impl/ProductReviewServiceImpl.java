package com.macro.mall.distribution.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dto.ProductReviewReplyDTO;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopProductReviewDao;
import com.macro.mall.distribution.dto.ProductReviewStatusDTO;
import com.macro.mall.distribution.dto.ProductReviewSubmitDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopProductReview;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.ProductReviewService;
import com.macro.mall.distribution.service.ContentModerationService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.vo.ProductReviewPageVO;
import com.macro.mall.distribution.vo.ProductReviewSummaryVO;
import com.macro.mall.distribution.vo.ProductReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private final DmsShopProductReviewDao reviewDao;
    private final DmsShopProductDao productDao;
    private final ContentModerationService contentModerationService;
    private final OperationLogService operationLogService;

    @Override
    public ProductReviewPageVO listProductReviews(Long productId, DmsShopMember member, Long orderItemId,
                                                  Integer pageNum, Integer pageSize) {
        DmsShopProduct product = requireProduct(productId);
        ProductReviewSummaryVO summary = reviewDao.selectSummary(productId);
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null ? 10 : Math.max(1, Math.min(pageSize, 50));
        PageHelper.startPage(safePageNum, safePageSize);
        List<DmsShopProductReview> reviewRows = reviewDao.selectVisibleByProductId(productId);
        PageInfo<DmsShopProductReview> pageInfo = new PageInfo<>(reviewRows);
        CommonPage<ProductReviewVO> reviewPage = new CommonPage<>();
        reviewPage.setPageNum(pageInfo.getPageNum());
        reviewPage.setPageSize(pageInfo.getPageSize());
        reviewPage.setTotalPage(pageInfo.getPages());
        reviewPage.setTotal(pageInfo.getTotal());
        reviewPage.setList(reviewRows.stream().map(this::toPublicView).toList());

        ProductReviewPageVO result = new ProductReviewPageVO();
        result.setPage(reviewPage);
        result.setReviewCount(summary == null || summary.getReviewCount() == null ? 0L : summary.getReviewCount());
        result.setStar5Count(summary == null || summary.getStar5Count() == null ? 0L : summary.getStar5Count());
        result.setStar4Count(summary == null || summary.getStar4Count() == null ? 0L : summary.getStar4Count());
        result.setStar3Count(summary == null || summary.getStar3Count() == null ? 0L : summary.getStar3Count());
        result.setStar2Count(summary == null || summary.getStar2Count() == null ? 0L : summary.getStar2Count());
        result.setStar1Count(summary == null || summary.getStar1Count() == null ? 0L : summary.getStar1Count());
        BigDecimal average = summary == null || summary.getAverageRating() == null
                ? BigDecimal.ZERO : summary.getAverageRating();
        result.setAverageRating(average.setScale(1, RoundingMode.HALF_UP));
        Long tenantId = product.getTenantId() == null ? 1L : product.getTenantId();
        boolean canReview = member != null && eligibleOrderItem(member.getUserId(), productId, tenantId, orderItemId) != null;
        result.setCanReview(canReview);
        if (member == null) {
            result.setReviewHint("登录后，已确认收货的买家可以评价");
        } else if (!canReview) {
            result.setReviewHint("购买并确认收货后可以评价");
        } else {
            result.setReviewHint("您有一笔已确认收货的订单可以评价");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopProductReview submitReview(Long productId, DmsShopMember member, ProductReviewSubmitDTO dto) {
        DmsShopProduct product = requireProduct(productId);
        if (member == null) {
            Asserts.unauthorized("请先登录");
        }
        if (dto == null || dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            Asserts.fail("评分必须是1到5星");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            Asserts.fail("请填写评价内容");
        }
        if (content.length() > 1000) {
            Asserts.fail("评价内容不能超过1000字");
        }
        contentModerationService.assertAllowed("评价内容", content);
        Long tenantId = product.getTenantId() == null ? 1L : product.getTenantId();
        DmsShopOrderItem eligibleItem = eligibleOrderItem(member.getUserId(), productId, tenantId, dto.getOrderItemId());
        if (eligibleItem == null) {
            Asserts.fail("只有购买并确认收货后才能评价，或该笔订单已经评价过");
        }

        DmsShopProductReview review = new DmsShopProductReview();
        review.setTenantId(product.getTenantId() == null ? 1L : product.getTenantId());
        review.setProductId(productId);
        review.setProductName(product.getProductName());
        review.setOrderId(eligibleItem.getOrderId());
        review.setOrderNo(eligibleItem.getOrderNo());
        review.setOrderItemId(eligibleItem.getId());
        review.setUserId(member.getUserId());
        review.setReviewerName(maskReviewerName(member));
        review.setReviewerAvatar(publicAvatar(member.getAvatarUrl()));
        review.setRating(dto.getRating());
        review.setContent(content);
        review.setStatus(1);
        try {
            reviewDao.insert(review);
        } catch (DuplicateKeyException e) {
            Asserts.fail("该笔订单已经评价过，请勿重复提交");
        }
        return reviewDao.selectById(review.getId());
    }

    private DmsShopOrderItem eligibleOrderItem(Long userId, Long productId, Long tenantId, Long orderItemId) {
        return orderItemId == null
                ? reviewDao.selectEligibleOrderItem(userId, productId, tenantId)
                : reviewDao.selectEligibleOrderItemById(userId, productId, tenantId, orderItemId);
    }

    @Override
    public CommonPage<DmsShopProductReview> listAdminReviews(String keyword, Long productId, Integer rating,
                                                             Integer status, Integer pageNum, Integer pageSize) {
        DmsAdminUser admin = requireAdmin();
        if (rating != null && (rating < 1 || rating > 5)) {
            Asserts.fail("评分筛选必须是1到5星");
        }
        if (status != null && status != 0 && status != 1) {
            Asserts.fail("评价状态不正确");
        }
        if (keyword != null && keyword.trim().length() > 100) {
            Asserts.fail("评价搜索关键字不能超过100个字符");
        }
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null ? 10 : Math.max(1, Math.min(pageSize, 100));
        PageHelper.startPage(safePageNum, safePageSize);
        return CommonPage.restPage(reviewDao.selectAdminList(normalize(keyword), productId, rating, status,
                TenantContext.getTenantId(), admin.getMerchantId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateReviewStatus(Long id, ProductReviewStatusDTO dto) {
        DmsAdminUser admin = requireAdmin();
        if (admin.getMerchantId() != null) Asserts.fail("仅平台可以隐藏或恢复评价");
        DmsShopProductReview existing = reviewDao.selectAdminById(id, TenantContext.getTenantId(), null);
        if (existing == null) {
            Asserts.fail("评价不存在");
        }
        Integer status = dto == null ? null : dto.getStatus();
        if (status == null || (status != 0 && status != 1)) {
            Asserts.fail("评价状态只能为隐藏或展示");
        }
        String reason = dto.getReason() == null ? "" : dto.getReason().trim();
        if (status == 0 && reason.isEmpty()) {
            Asserts.fail("隐藏评价时必须填写原因");
        }
        if (reason.length() > 255) {
            Asserts.fail("原因不能超过255字");
        }
        Long adminId = admin.getId();
        String adminName = firstNotBlank(admin.getNickname(), admin.getUsername());
        return reviewDao.updateStatus(id, status,
                status == 0 ? reason : null,
                status == 0 ? adminId : null,
                status == 0 ? adminName : null,
                status == 0 ? LocalDateTime.now() : null) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replyReview(Long id, ProductReviewReplyDTO dto) {
        DmsAdminUser admin = requireAdmin();
        Long tenantId = TenantContext.getTenantId();
        DmsShopProductReview review = reviewDao.selectAdminById(id, tenantId, admin.getMerchantId());
        if (review == null) Asserts.fail("评价不存在或无权回复");
        if (!Integer.valueOf(1).equals(review.getStatus())) Asserts.fail("已隐藏的评价不能回复，请由平台先恢复展示");
        String content = dto == null ? null : normalize(dto.getContent());
        if (content == null) Asserts.fail("请填写回复内容");
        if (content.length() > 500) Asserts.fail("回复内容不能超过500字");
        contentModerationService.assertAllowed("评价回复", content);
        String current = admin.getMerchantId() == null ? review.getPlatformReply() : review.getMerchantReply();
        if (content.equals(current)) return true; // 网络重试不重复写入，也不改变回复时间。
        int expected = dto.getExpectedVersion() == null ? 0 : dto.getExpectedVersion();
        if (expected < 0) Asserts.fail("回复版本不正确，请刷新后重试");
        if (reviewDao.updateReply(id, tenantId, admin.getMerchantId(), admin.getId(), content,
                expected) != 1) {
            Asserts.fail("评价或回复已变化，请刷新后再回复");
        }
        operationLogService.log("PRODUCT_REVIEW", "REPLY", "PRODUCT_REVIEW", String.valueOf(id), current, content,
                admin.getMerchantId() == null ? "平台保存评价回复" : "商家保存评价回复");
        return true;
    }

    private DmsAdminUser requireAdmin() {
        DmsAdminUser admin = AdminContext.get();
        if (admin == null || admin.getId() == null) Asserts.unauthorized("请先登录管理后台");
        if (!Integer.valueOf(1).equals(admin.getStatus())) Asserts.fail("后台账号不可用");
        return admin;
    }

    private DmsShopProduct requireProduct(Long productId) {
        if (productId == null) {
            Asserts.fail("商品ID不能为空");
        }
        DmsShopProduct product = productDao.selectById(productId);
        if (product == null) {
            Asserts.fail("商品不存在");
        }
        return product;
    }

    private ProductReviewVO toPublicView(DmsShopProductReview review) {
        ProductReviewVO vo = new ProductReviewVO();
        vo.setId(review.getId());
        vo.setReviewerName(review.getReviewerName());
        vo.setReviewerAvatar(publicAvatar(review.getReviewerAvatar()));
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setMerchantReply(review.getMerchantReply());
        vo.setMerchantReplyTime(review.getMerchantReplyTime());
        vo.setPlatformReply(review.getPlatformReply());
        vo.setPlatformReplyTime(review.getPlatformReplyTime());
        vo.setCreateTime(review.getCreateTime());
        return vo;
    }

    private String publicAvatar(String url) {
        // Account avatars are private, including when a historical review is re-read.
        return url != null && url.contains("/shop/media/member-avatar/") ? null : url;
    }

    private String maskReviewerName(DmsShopMember member) {
        String nickname = firstNotBlank(member.getNickname(), member.getUsername());
        if (nickname != null) {
            if (nickname.length() == 1) return nickname + "***";
            return nickname.substring(0, 1) + "***" + nickname.substring(nickname.length() - 1);
        }
        String phone = member.getPhone();
        if (phone != null && phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return "匿名买家";
    }

    private String firstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
