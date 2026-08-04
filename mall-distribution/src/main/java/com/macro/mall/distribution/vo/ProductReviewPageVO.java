package com.macro.mall.distribution.vo;

import com.macro.mall.common.api.CommonPage;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ProductReviewPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private CommonPage<ProductReviewVO> page;
    private Long reviewCount;
    private BigDecimal averageRating;
    private Long star5Count;
    private Long star4Count;
    private Long star3Count;
    private Long star2Count;
    private Long star1Count;
    private Boolean canReview;
    private String reviewHint;
}
