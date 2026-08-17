package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMerchantWithdrawal implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    private String withdrawalNo;
    private Long merchantId;
    private String merchantName;
    private BigDecimal requestedAmount;
    private BigDecimal invoiceRequiredAmount;
    private BigDecimal invoiceReceivedAmount;
    private String invoiceStatus;
    private BigDecimal adjustmentAmount;
    private String adjustmentReason;
    private BigDecimal actualPaidAmount;
    private String paymentReference;
    private String paymentVoucherUrl;
    private String status;
    private String rejectReason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime applyTime;
    private LocalDateTime paidTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
