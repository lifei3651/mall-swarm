package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopProductDao {

    DmsShopProduct selectById(@Param("id") Long id);

    DmsShopProduct selectByIdForUpdate(@Param("id") Long id);

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

    int insert(DmsShopProduct product);

    int update(DmsShopProduct product);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int disableByMerchantId(@Param("tenantId") Long tenantId, @Param("merchantId") Long merchantId);

    int resetDefaultSettlementProductsForReview(@Param("tenantId") Long tenantId,
                                                @Param("merchantId") Long merchantId,
                                                @Param("remark") String remark);

    int markReviewSubmitted(@Param("id") Long id, @Param("version") Integer version,
                            @Param("submittedAt") java.time.LocalDateTime submittedAt);

    int markReviewDecision(@Param("id") Long id, @Param("version") Integer version,
                           @Param("reviewStatus") String reviewStatus,
                           @Param("status") Integer status,
                           @Param("remark") String remark,
                           @Param("reviewerId") Long reviewerId,
                           @Param("reviewerName") String reviewerName,
                           @Param("reviewedAt") java.time.LocalDateTime reviewedAt);

    int updateCategoryName(@Param("tenantId") Long tenantId,
                           @Param("oldName") String oldName,
                           @Param("newName") String newName);

    int countByCategoryName(@Param("tenantId") Long tenantId,
                            @Param("categoryName") String categoryName);

    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
