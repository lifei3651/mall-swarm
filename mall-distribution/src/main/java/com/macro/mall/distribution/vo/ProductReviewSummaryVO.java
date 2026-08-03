package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ProductReviewSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long reviewCount;
    private BigDecimal averageRating;
}
