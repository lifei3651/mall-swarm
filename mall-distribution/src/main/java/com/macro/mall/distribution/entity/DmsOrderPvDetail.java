package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单 PV 明细快照
 */
@Data
public class DmsOrderPvDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private Long orderId;

    private String orderNo;

    private Long productId;

    private Long skuId;

    private String productName;

    private Integer quantity;

    private BigDecimal payAmount;

    private BigDecimal pvValue;

    private BigDecimal totalPv;

    private BigDecimal bvValue;

    private BigDecimal totalBv;

    private BigDecimal costAmount;

    private BigDecimal totalCost;

    private LocalDateTime createTime;
}
