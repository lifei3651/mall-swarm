package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMerchantDepositFlow implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    private Long merchantId;
    private String merchantName;
    private String operationNo;
    private String operationType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
}
