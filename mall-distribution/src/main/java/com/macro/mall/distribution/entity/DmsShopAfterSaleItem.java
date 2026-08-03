package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 售后实际退回的订单商品明细。 */
@Data
public class DmsShopAfterSaleItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long afterSaleId;
    private Long orderId;
    private Long orderItemId;
    private Long productId;
    private Long skuId;
    private String productName;
    private String skuName;
    private Integer refundQuantity;
    private BigDecimal refundAmount;
    private LocalDateTime createTime;
}
