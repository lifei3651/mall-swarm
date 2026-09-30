package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款佣金追回流水
 */
@Data
public class DmsCommissionClawback implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tenantId;

    private Long id;

    private Long refundId;

    private Long commissionRecordId;

    /** Direct referral: type 4 links the original debt; type 5 links the offset being reversed. */
    private Long sourceClawbackId;

    private Long orderId;

    private String orderNo;

    private Long agentId;

    private Long agentUserId;

    private String agentName;

    private BigDecimal originalCommissionAmount;

    private BigDecimal clawbackAmount;

    private BigDecimal deductedAmount;

    private BigDecimal debtAmount;

    /**
     * 1-待结算减少 2-可提现扣回 3-欠款待抵扣 4-未来佣金抵扣 5-退款恢复原历史欠款
     */
    private Integer clawbackType;

    /**
     * 0-待处理 1-已完成 2-部分完成
     */
    private Integer status;

    private String reason;

    private LocalDateTime createTime;
}
