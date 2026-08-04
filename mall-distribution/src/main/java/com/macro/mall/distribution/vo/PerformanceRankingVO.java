package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 业绩排行VO
 */
@Data
public class PerformanceRankingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long agentId;

    private String agentName;

    private Integer agentLevel;

    private String agentLevelName;

    private Integer rankType;

    private Integer rankPeriod;

    private LocalDate statDate;

    private BigDecimal performanceValue;

    /** 截至统计日的累计业绩；新增代理排名时表示累计新增代理数 */
    private BigDecimal totalPerformance;

    /** 统计日所在月份的业绩；新增代理排名时表示当月新增代理数 */
    private BigDecimal currentMonthPerformance;

    /** 截至统计日累计新增代理数 */
    private Integer totalNewAgentCount;

    /** 统计日所在月份新增代理数 */
    private Integer currentMonthNewAgentCount;

    private Integer ranking;
}
