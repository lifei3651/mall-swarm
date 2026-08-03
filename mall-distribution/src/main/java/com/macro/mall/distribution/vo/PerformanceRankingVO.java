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

    private Integer ranking;
}
