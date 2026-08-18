package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class MerchantExitReadinessVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long merchantId;
    private String merchantName;
    private Integer activeProductCount;
    private Integer unfinishedOrderCount;
    private Integer openAfterSaleCount;
    private Integer pendingSettlementCount;
    private Integer activeWithdrawalCount;
    private BigDecimal pendingAmount;
    private BigDecimal availableAmount;
    private BigDecimal frozenAmount;
    private BigDecimal depositAmount;
    private BigDecimal debtAmount;
    private Boolean ready;
    private List<String> blockers = new ArrayList<>();
}
