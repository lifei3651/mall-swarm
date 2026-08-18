package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMerchantAccount implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    private Long merchantId;
    private String merchantName;
    private BigDecimal pendingAmount;
    private BigDecimal availableAmount;
    private BigDecimal frozenAmount;
    /** 平台单独冻结的商户保证金，不参与提现冻结。 */
    private BigDecimal depositFrozenAmount;
    /** 商户资料中配置的应缴保证金。 */
    private BigDecimal requiredDepositAmount;
    /** 应缴保证金减去当前保证金后的不足金额。 */
    private BigDecimal depositShortfallAmount;
    private BigDecimal debtAmount;
    private BigDecimal totalPaidAmount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
