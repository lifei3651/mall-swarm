package com.macro.mall.distribution.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.distribution.dto.MerchantProductReviewDecisionDTO;
import com.macro.mall.distribution.entity.DmsMerchantProductReview;
import com.macro.mall.distribution.entity.DmsShopProduct;

public interface MerchantProductReviewService {
    Long currentMerchantId();
    void bindMerchantForWrite(DmsShopProduct product, DmsShopProduct existing);
    void prepareCreatedProduct(DmsShopProduct product);
    void prepareUpdatedProduct(DmsShopProduct existing, DmsShopProduct product);
    void assertProductAccess(DmsShopProduct product);
    boolean currentMerchantOwns(Long merchantId);
    CommonPage<DmsMerchantProductReview> list(String status, String keyword, Integer pageNum, Integer pageSize);
    DmsMerchantProductReview submit(Long productId);
    DmsMerchantProductReview decide(Long reviewId, MerchantProductReviewDecisionDTO dto);
}
