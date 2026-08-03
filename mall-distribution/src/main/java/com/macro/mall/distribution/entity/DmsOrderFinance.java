package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsOrderFinance implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private String orderNo;

    private BigDecimal payAmount;

    private BigDecimal refundAmount;

    private BigDecimal netPayAmount;

    private BigDecimal productCost;

    private BigDecimal bonusAmount;

    private BigDecimal companyShareAmount;

    private BigDecimal companyProfit;

    private Integer riskStatus;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
