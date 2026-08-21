package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsCommissionSettlementItem implements Serializable {
    private Long tenantId;
    private Long id;
    private Long batchId;
    private Long commissionRecordId;
    private Long agentId;
    private String agentName;
    private BigDecimal snapshotAmount;
    /** 0已锁定，1已结算，2已跳过 */
    private Integer status;
    private String skipReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
