package com.macro.mall.distribution.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.distribution.dto.ProductReviewStatusDTO;
import com.macro.mall.distribution.dto.ProductReviewSubmitDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopProductReview;
import com.macro.mall.distribution.vo.ProductReviewPageVO;

public interface ProductReviewService {

    ProductReviewPageVO listProductReviews(Long productId, DmsShopMember member, Long orderItemId,
                                           Integer pageNum, Integer pageSize);

    default ProductReviewPageVO listProductReviews(Long productId, DmsShopMember member,
                                                   Integer pageNum, Integer pageSize) {
        return listProductReviews(productId, member, null, pageNum, pageSize);
    }

    DmsShopProductReview submitReview(Long productId, DmsShopMember member, ProductReviewSubmitDTO dto);

    CommonPage<DmsShopProductReview> listAdminReviews(String keyword, Long productId, Integer rating,
                                                       Integer status, Integer pageNum, Integer pageSize);

    boolean updateReviewStatus(Long id, ProductReviewStatusDTO dto);
}
