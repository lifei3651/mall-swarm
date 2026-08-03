package com.macro.mall.distribution.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommissionSettlementBatchCreateDTO {
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    /** 截止此时刻创建且仍待结算的佣金会被快照锁定；默认当前时刻。 */
    private LocalDateTime cutoffTime;
    private String remark;
}
