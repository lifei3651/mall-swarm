package com.macro.mall.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceSummaryVO {

    private Long orderCount;

    private Long riskOrderCount;

    private BigDecimal payAmount;

    private BigDecimal refundAmount;

    private BigDecimal netPayAmount;

    private BigDecimal productCost;

    private BigDecimal bonusAmount;

    private BigDecimal companyShareAmount;

    private BigDecimal companyProfit;

    private BigDecimal profitRate;

    private BigDecimal payoutRate;
}
