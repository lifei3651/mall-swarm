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

    private BigDecimal wechatPayAmount;

    private BigDecimal wechatRefundAmount;

    private BigDecimal wechatNetPayAmount;

    private BigDecimal alipayPayAmount;

    private BigDecimal alipayRefundAmount;

    private BigDecimal alipayNetPayAmount;

    private BigDecimal balancePayAmount;

    private BigDecimal balanceRefundAmount;

    private BigDecimal balanceNetPayAmount;

    /** Historical orders with a missing or unrecognised payment channel. */
    private BigDecimal otherPayAmount;

    private BigDecimal otherRefundAmount;

    private BigDecimal otherNetPayAmount;

    /** Ledger credits made by an operator, not external payment receipts. */
    private BigDecimal manualBalanceAddedAmount;

    private BigDecimal productCost;

    private BigDecimal bonusAmount;

    private BigDecimal companyShareAmount;

    private BigDecimal companyProfit;

    private BigDecimal profitRate;

    private BigDecimal payoutRate;
}
