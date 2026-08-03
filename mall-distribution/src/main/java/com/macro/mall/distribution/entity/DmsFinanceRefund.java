package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsFinanceRefund implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private String orderNo;

    private String refundNo;

    private BigDecimal refundAmount;

    private BigDecimal productRefundAmount;

    private BigDecimal freightRefundAmount;

    private Integer refundQuantity;

    private Integer clawbackBonus;

    private String reason;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime refundTime;

    private LocalDateTime createTime;
}
