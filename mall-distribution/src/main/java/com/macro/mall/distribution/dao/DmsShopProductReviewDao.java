package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopProductReview;
import com.macro.mall.distribution.vo.ProductReviewSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsShopProductReviewDao {

    DmsShopProductReview selectById(@Param("id") Long id);

    List<DmsShopProductReview> selectVisibleByProductId(@Param("productId") Long productId);

    List<DmsShopProductReview> selectAdminList(@Param("keyword") String keyword,
                                               @Param("productId") Long productId,
                                               @Param("rating") Integer rating,
                                               @Param("status") Integer status);

    ProductReviewSummaryVO selectSummary(@Param("productId") Long productId);

    DmsShopOrderItem selectEligibleOrderItem(@Param("userId") Long userId,
                                             @Param("productId") Long productId,
                                             @Param("tenantId") Long tenantId);

    int countUnreviewedByOrderId(@Param("userId") Long userId,
                                 @Param("orderId") Long orderId,
                                 @Param("tenantId") Long tenantId);

    int insert(DmsShopProductReview review);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("hiddenReason") String hiddenReason,
                     @Param("hiddenBy") Long hiddenBy,
                     @Param("hiddenByName") String hiddenByName,
                     @Param("hiddenTime") LocalDateTime hiddenTime);
}
