package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommissionSettlementBatchCreateDTO {
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    /** 截止此时刻创建且仍待结算的佣金会被快照锁定；默认当前时刻。 */
    private LocalDateTime cutoffTime;

    @Size(max = 500, message = "结算批次备注不能超过500个字符")
    private String remark;
}
