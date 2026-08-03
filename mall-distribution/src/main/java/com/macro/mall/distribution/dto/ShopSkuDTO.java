package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ShopSkuDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    private String skuNo;

    private String skuName;

    private String attrsJson;

    private String imageUrl;

    private BigDecimal salePrice;

    private BigDecimal marketPrice;

    private BigDecimal costAmount;

    private BigDecimal pvValue;

    private BigDecimal bvValue;

    private Integer stock;

    private Integer status;
}
