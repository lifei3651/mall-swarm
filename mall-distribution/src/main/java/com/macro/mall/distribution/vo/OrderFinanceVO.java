package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderFinanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    private String riskStatusName;

    private String remark;

    private LocalDateTime updateTime;
}
