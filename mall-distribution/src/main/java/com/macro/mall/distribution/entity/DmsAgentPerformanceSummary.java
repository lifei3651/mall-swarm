package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 代理业绩汇总实体类
 * 对应数据库表：dms_agent_performance_summary
 */
@Data
@Schema(description = "代理业绩汇总")
public class DmsAgentPerformanceSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID */
    @Schema(description = "id")
    private Long id;

    /** 代理ID */
    @Schema(description = "agentId")
    private Long agentId;

    /** 用户ID */
    @Schema(description = "userId")
    private Long userId;

    /** 代理名称 */
    @Schema(description = "agentName")
    private String agentName;

    /** 统计日期 */
    @Schema(description = "statDate")
    private LocalDate statDate;

    /**
     * 统计类型
     * 1-日 2-周 3-月 4-年
     */
    @Schema(description = "statType")
    private Integer statType;

    /** 个人有效商品件数 */
    @Schema(description = "personalOrderCount")
    private Integer personalOrderCount;

    /** 个人业绩 */
    @Schema(description = "personalPerformance")
    private BigDecimal personalPerformance;

    /** 无限层团队有效商品件数 */
    @Schema(description = "teamOrderCount")
    private Integer teamOrderCount;

    /** 团队总业绩 */
    @Schema(description = "teamPerformance")
    private BigDecimal teamPerformance;

    /** 一级业绩（直属下级） */
    @Schema(description = "level1Performance")
    private BigDecimal level1Performance;

    /** 二级业绩 */
    @Schema(description = "level2Performance")
    private BigDecimal level2Performance;

    /** 三级业绩 */
    @Schema(description = "level3Performance")
    private BigDecimal level3Performance;

    /** 团队总人数 */
    @Schema(description = "teamMemberCount")
    private Integer teamMemberCount;

    /** 一级人数（直属） */
    @Schema(description = "level1MemberCount")
    private Integer level1MemberCount;

    /** 二级人数 */
    @Schema(description = "level2MemberCount")
    private Integer level2MemberCount;

    /** 三级人数 */
    @Schema(description = "level3MemberCount")
    private Integer level3MemberCount;

    /** 活跃人数（有订单） */
    @Schema(description = "activeMemberCount")
    private Integer activeMemberCount;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "updateTime")
    private LocalDateTime updateTime;
}
