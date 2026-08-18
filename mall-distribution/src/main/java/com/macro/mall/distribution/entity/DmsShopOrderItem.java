package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    private Long merchantId;

    private String merchantName;

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

    /** 下单时锁定的商户结算等待天数。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer settlementDelayDays;

    private String teamBonusMode;

    private LocalDateTime createTime;
}
