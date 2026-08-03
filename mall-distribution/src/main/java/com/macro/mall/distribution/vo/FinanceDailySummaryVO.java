package com.macro.mall.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinanceDailySummaryVO {

    private LocalDate statDate;

    private Long orderCount;

    private Long riskOrderCount;

    private BigDecimal payAmount;

    private BigDecimal refundAmount;

    private BigDecimal netPayAmount;

    private BigDecimal productCost;

    private BigDecimal bonusAmount;

    private BigDecimal companyShareAmount;

    private BigDecimal companyProfit;
}
