package com.macro.mall.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 单个会员在当前自然日和自然月内的提现占用。 */
@Data
public class WithdrawalLimitUsageVO {
    private Long dailyCount;
    private BigDecimal dailyAmount;
    private Long monthlyCount;
    private BigDecimal monthlyAmount;
}
