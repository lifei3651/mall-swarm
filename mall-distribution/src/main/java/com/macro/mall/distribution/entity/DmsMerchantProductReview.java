package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商户商品每次提交审核时的不可变价格与商品快照。 */
@Data
public class DmsMerchantProductReview implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    private Long merchantId;
    private String merchantName;
    private Long productId;
    private Integer reviewVersion;
    private String reviewType;
    private String status;
    private String productNo;
    private String productName;
    private BigDecimal salePrice;
    private BigDecimal settlementPrice;
    private Integer skuCount;
    private String productSnapshot;
    private Long submitterId;
    private String submitterName;
    private LocalDateTime submittedAt;
    private Long reviewerId;
    private String reviewerName;
    private String reviewRemark;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
