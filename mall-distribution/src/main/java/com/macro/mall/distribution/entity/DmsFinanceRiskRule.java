package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsFinanceRiskRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String ruleCode;

    private String ruleName;

    private BigDecimal thresholdValue;

    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
