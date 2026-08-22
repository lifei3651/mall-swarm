package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsShopProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopProductDao {

    DmsShopProduct selectByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsShopProduct selectById(Long id) { return selectByIdScoped(TenantContext.getTenantId(), id); }

    DmsShopProduct selectByIdForUpdateScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsShopProduct selectByIdForUpdate(Long id) {
        return selectByIdForUpdateScoped(TenantContext.getTenantId(), id);
    }

    List<DmsShopProduct> selectList(@Param("tenantId") Long tenantId,
                                    @Param("keyword") String keyword,
                                    @Param("categoryName") String categoryName,
                                    @Param("status") Integer status,
                                    @Param("stockStatus") String stockStatus,
                                    @Param("merchantId") Long merchantId);

    List<DmsShopProduct> selectFrontList(@Param("tenantId") Long tenantId,
                                         @Param("keyword") String keyword,
                                         @Param("categoryName") String categoryName,
                                         @Param("status") Integer status,
                                         @Param("stockStatus") String stockStatus);

    List<DmsShopProduct> selectRepurchaseList(@Param("tenantId") Long tenantId,
                                              @Param("keyword") String keyword);

    List<String> selectCategories(@Param("tenantId") Long tenantId);

    int insertScoped(@Param("tenantId") Long tenantId, @Param("product") DmsShopProduct product);
    default int insert(DmsShopProduct product) {
        Long tenantId = TenantContext.getTenantId();
        if (product == null) throw new IllegalArgumentException("商品不能为空");
        if (product.getTenantId() == null) product.setTenantId(tenantId);
        if (!tenantId.equals(product.getTenantId())) throw new IllegalArgumentException("不能写入其他租户的商品");
        return insertScoped(tenantId, product);
    }

    int updateScoped(@Param("tenantId") Long tenantId, @Param("product") DmsShopProduct product);
    default int update(DmsShopProduct product) { return updateScoped(TenantContext.getTenantId(), product); }

    int updateStatusScoped(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") Integer status);
    default int updateStatus(Long id, Integer status) {
        return updateStatusScoped(TenantContext.getTenantId(), id, status);
    }

    int disableByMerchantId(@Param("tenantId") Long tenantId, @Param("merchantId") Long merchantId);

    int resetDefaultSettlementProductsForReview(@Param("tenantId") Long tenantId,
                                                @Param("merchantId") Long merchantId,
                                                @Param("remark") String remark);

    int markReviewSubmittedScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                                  @Param("version") Integer version,
                                  @Param("submittedAt") java.time.LocalDateTime submittedAt);
    default int markReviewSubmitted(Long id, Integer version, java.time.LocalDateTime submittedAt) {
        return markReviewSubmittedScoped(TenantContext.getTenantId(), id, version, submittedAt);
    }

    int markReviewDecisionScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                                 @Param("version") Integer version,
                                 @Param("reviewStatus") String reviewStatus,
                                 @Param("status") Integer status,
                                 @Param("remark") String remark,
                                 @Param("reviewerId") Long reviewerId,
                                 @Param("reviewerName") String reviewerName,
                                 @Param("reviewedAt") java.time.LocalDateTime reviewedAt);
    default int markReviewDecision(Long id, Integer version, String reviewStatus, Integer status, String remark,
                                   Long reviewerId, String reviewerName, java.time.LocalDateTime reviewedAt) {
        return markReviewDecisionScoped(TenantContext.getTenantId(), id, version, reviewStatus, status, remark,
                reviewerId, reviewerName, reviewedAt);
    }

    int updateCategoryName(@Param("tenantId") Long tenantId,
                           @Param("oldName") String oldName,
                           @Param("newName") String newName);

    int countByCategoryName(@Param("tenantId") Long tenantId,
                            @Param("categoryName") String categoryName);

    int countByFreightTemplateId(@Param("tenantId") Long tenantId,
                                 @Param("freightTemplateId") Long freightTemplateId);

    int decreaseStockScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                            @Param("quantity") Integer quantity);
    default int decreaseStock(Long id, Integer quantity) {
        return decreaseStockScoped(TenantContext.getTenantId(), id, quantity);
    }

    int increaseStockScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                            @Param("quantity") Integer quantity);
    default int increaseStock(Long id, Integer quantity) {
        return increaseStockScoped(TenantContext.getTenantId(), id, quantity);
    }
}
