package com.macro.mall.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceRiskAlertVO {

    private String ruleCode;

    private String ruleName;

    private String message;

    private BigDecimal currentValue;

    private BigDecimal thresholdValue;

    private Integer level;
}
