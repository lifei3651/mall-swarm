package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsShopSku implements Serializable {

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

    private Integer salesCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
