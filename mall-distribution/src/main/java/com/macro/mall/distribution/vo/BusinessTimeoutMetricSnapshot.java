package com.macro.mall.distribution.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务处理中超时的聚合快照。
 *
 * <p>只包含超时数量和最早更新时间，不暴露任何业务单号或用户信息。</p>
 */
@Data
public class BusinessTimeoutMetricSnapshot {
    private Long timedOutCount;
    private LocalDateTime oldestTime;
}
