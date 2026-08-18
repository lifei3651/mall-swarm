package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MerchantBalanceReconciliationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long merchantId;
    private String merchantName;
    private Boolean ledgerInitialized;
    private Boolean consistent;
    private String latestLedgerNo;
    private LocalDateTime latestLedgerTime;
    private BigDecimal pendingAmount;
    private BigDecimal availableAmount;
    private BigDecimal frozenAmount;
    private BigDecimal depositAmount;
    private BigDecimal debtAmount;
    private BigDecimal paidAmount;
    private BigDecimal pendingDifference;
    private BigDecimal availableDifference;
    private BigDecimal frozenDifference;
    private BigDecimal depositDifference;
    private BigDecimal debtDifference;
    private BigDecimal paidDifference;
}
