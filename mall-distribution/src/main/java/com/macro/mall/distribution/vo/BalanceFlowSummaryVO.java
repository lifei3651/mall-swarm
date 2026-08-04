package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 余额流水筛选结果汇总。 */
@Data
public class BalanceFlowSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 人工充值收入合计。 */
    private BigDecimal totalRechargeAmount;

    /** 所有余额收入合计。 */
    private BigDecimal totalIncomeAmount;

    /** 所有余额支出合计。 */
    private BigDecimal totalExpenseAmount;
}
