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
    /** 客户端生成的持久化防重复申请号。 */
    private String requestNo;
    private Long merchantId;
    private String merchantName;
    private Integer merchantProfileVersion;
    private String legalEntityNameSnapshot;
    private String bankAccountNameSnapshot;
    private String bankNameSnapshot;
    private String bankAccountNoSnapshot;
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
    /** 风控冻结前的业务状态，用于审核后准确恢复。 */
    private String resumeStatus;
    private String rejectReason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime applyTime;
    private LocalDateTime paidTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
