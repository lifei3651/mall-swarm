package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchantProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsMerchantProductReviewDao {
    DmsMerchantProductReview selectById(@Param("id") Long id);
    DmsMerchantProductReview selectByIdForUpdate(@Param("id") Long id);
    List<DmsMerchantProductReview> selectList(@Param("tenantId") Long tenantId,
                                               @Param("merchantId") Long merchantId,
                                               @Param("status") String status,
                                               @Param("keyword") String keyword);
    int insert(DmsMerchantProductReview review);
    int updateDecision(DmsMerchantProductReview review);
}
