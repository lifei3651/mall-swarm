package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 业绩概览VO
 */
@Data
public class PerformanceOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 代理ID */
    private Long agentId;

    /** 代理名称 */
    private String agentName;

    /** 个人业绩 */
    private BigDecimal personalPerformance;

    /** 个人有效商品件数 */
    private Integer personalOrderCount;

    /** 团队业绩 */
    private BigDecimal teamPerformance;

    /** 无限层团队有效商品件数 */
    private Integer teamOrderCount;

    /** 一级业绩（直属） */
    private BigDecimal level1Performance;

    /** 二级业绩 */
    private BigDecimal level2Performance;

    /** 三级业绩 */
    private BigDecimal level3Performance;

    /** 团队成员数 */
    private Integer teamMemberCount;

    /** 活跃成员数 */
    private Integer activeMemberCount;

    /** 业绩增长率 */
    private BigDecimal performanceGrowthRate;
}
