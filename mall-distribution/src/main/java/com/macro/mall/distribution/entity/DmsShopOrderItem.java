package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsShopOrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private String orderNo;

    private Long productId;

    private Long skuId;

    private String productName;

    private String skuName;

    private String skuAttrs;

    private String productCover;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal totalAmount;

    private BigDecimal pvValue;

    private BigDecimal totalPv;

    private BigDecimal costAmount;

    private BigDecimal totalCost;

    private LocalDateTime createTime;
}
