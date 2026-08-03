package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 月度佣金结算批次，保存结算范围和金额快照。 */
@Data
public class DmsCommissionSettlementBatch implements Serializable {
    private Long id;
    private String batchNo;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime cutoffTime;
    /** 0草稿锁定，1已执行，2已作废 */
    private Integer status;
    private Integer recordCount;
    private BigDecimal totalAmount;
    private Integer settledCount;
    private Integer skippedCount;
    private String remark;
    private Long creatorId;
    private String creatorName;
    private Long executorId;
    private String executorName;
    private LocalDateTime executeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
