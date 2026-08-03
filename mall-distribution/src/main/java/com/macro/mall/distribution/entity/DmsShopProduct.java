package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsShopProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private String productNo;

    private String productName;

    private String subtitle;

    private String categoryName;

    private String coverUrl;

    private String galleryUrls;

    private BigDecimal salePrice;

    private BigDecimal marketPrice;

    private BigDecimal costAmount;

    private BigDecimal pvValue;

    private BigDecimal bvValue;

    private Integer stock;

    private Integer salesCount;

    private Integer sort;

    private Integer status;

    private String detail;

    private String detailImages;

    private String deliveryAddress;

    private String deliveryProvince;

    private String deliveryCity;

    private String deliveryDistrict;

    private Integer freightType;

    private BigDecimal freightAmount;

    private BigDecimal freeShippingAmount;

    private String freightTemplateName;

    private Long freightTemplateId;

    private String deliveryTime;

    private String afterSalePolicy;

    private String serviceTags;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
