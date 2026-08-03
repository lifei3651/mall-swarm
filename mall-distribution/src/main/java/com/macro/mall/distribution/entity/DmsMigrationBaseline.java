package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMigrationBaseline implements Serializable {
    private Long id;
    private String batchNo;
    private Long agentId;
    private Long userId;
    private String externalMemberCode;
    private Integer historicalOrderCount;
    private BigDecimal historicalPersonalPerformance;
    private BigDecimal historicalTeamPerformance;
    private Integer initialLevel;
    private LocalDateTime cutoverTime;
    private LocalDateTime createTime;
}
