package com.macro.mall.distribution.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台任务的聚合运行快照。
 *
 * <p>只包含数量和时间，不包含订单号、会员、手机号或任务载荷。</p>
 */
@Data
public class BusinessTaskMetricSnapshot {
    private Long backlogCount;
    private LocalDateTime oldestTime;
    private Long failureCount;
}
