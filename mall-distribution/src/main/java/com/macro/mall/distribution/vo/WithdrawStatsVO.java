package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 提现统计VO
 */
@Data
public class WithdrawStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal totalAmount = BigDecimal.ZERO;

    private BigDecimal pendingAmount = BigDecimal.ZERO;

    private BigDecimal successAmount = BigDecimal.ZERO;

    private BigDecimal rejectedAmount = BigDecimal.ZERO;

    private Integer totalCount = 0;

    private Integer pendingCount = 0;
}
