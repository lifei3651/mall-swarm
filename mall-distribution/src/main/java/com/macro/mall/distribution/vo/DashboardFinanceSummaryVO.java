package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 工作台累计财务汇总。 */
@Data
public class DashboardFinanceSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已支付订单实付金额扣除退款后的累计净收款。 */
    private BigDecimal totalReceiptAmount;

    /** 产品成本、有效奖金和公司分账之和。 */
    private BigDecimal totalPayoutAmount;

    private BigDecimal totalProductCostAmount;

    private BigDecimal totalBonusPayoutAmount;

    private BigDecimal totalCompanyShareAmount;

    /** 累计净收款减去累计总拨出。 */
    private BigDecimal totalProfitAmount;
}
