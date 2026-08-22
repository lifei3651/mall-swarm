package com.macro.mall.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 当前自然日和自然月的提现使用量。 */
@Data
public class WithdrawalLimitUsageVO {
    private Long dailyCount;
    private BigDecimal dailyAmount;
    private Long monthlyCount;
    private BigDecimal monthlyAmount;
}
