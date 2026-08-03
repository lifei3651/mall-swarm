package com.macro.mall.distribution.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceRefundDTO {

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
}
