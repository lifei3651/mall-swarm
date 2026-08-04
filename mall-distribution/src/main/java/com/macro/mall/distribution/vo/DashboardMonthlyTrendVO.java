package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 工作台月度销售趋势的数据库聚合行。 */
@Data
public class DashboardMonthlyTrendVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer statYear;
    private Integer statMonth;
    private BigDecimal performanceAmount;
}
