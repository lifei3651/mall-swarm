package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTrendVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private LocalDate statDate;
    private BigDecimal performanceAmount;
}
