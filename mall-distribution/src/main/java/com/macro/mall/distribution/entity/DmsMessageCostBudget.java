package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMessageCostBudget implements Serializable {
    private Long id;
    private Long tenantId;
    private String scopeType;
    private String scopeKey;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private String currency;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
