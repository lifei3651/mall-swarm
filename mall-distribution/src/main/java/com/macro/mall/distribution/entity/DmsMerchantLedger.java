package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商户资金不可变流水；每一行同时保存各资金桶变化量与变化后的余额。 */
@Data
public class DmsMerchantLedger implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    private Long merchantId;
    private String merchantName;
    private String ledgerNo;
    private String bizType;
    private String bizId;
    private String summary;
    private BigDecimal pendingDelta;
    private BigDecimal availableDelta;
    private BigDecimal frozenDelta;
    private BigDecimal depositDelta;
    private BigDecimal debtDelta;
    private BigDecimal paidDelta;
    private BigDecimal pendingAfter;
    private BigDecimal availableAfter;
    private BigDecimal frozenAfter;
    private BigDecimal depositAfter;
    private BigDecimal debtAfter;
    private BigDecimal paidAfter;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
}
