package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 工作台商品销售排行榜。 */
@Data
public class DashboardProductRankingVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer ranking;
    private Long productId;
    private String productName;
    private String productCover;
    private Long orderCount;
    private Long salesQuantity;
    private BigDecimal salesAmount;
}
