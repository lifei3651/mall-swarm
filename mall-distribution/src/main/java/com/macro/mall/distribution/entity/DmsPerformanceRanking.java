package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业绩排名实体类
 * 对应数据库表：dms_performance_ranking
 */
@Data
@Schema(description = "业绩排名信息")
public class DmsPerformanceRanking implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID */
    @Schema(description = "id")
    private Long id;

    /** 代理ID */
    @Schema(description = "agentId")
    private Long agentId;

    /** 代理名称 */
    @Schema(description = "agentName")
    private String agentName;

    /** 代理等级 */
    @Schema(description = "agentLevel")
    private Integer agentLevel;

    /**
     * 排名类型
     * 1-个人业绩 2-团队业绩 3-新增代理
     */
    @Schema(description = "rankType")
    private Integer rankType;

    /**
     * 排名周期
     * 1-日 2-周 3-月 4-年
     */
    @Schema(description = "rankPeriod")
    private Integer rankPeriod;

    /** 统计日期 */
    @Schema(description = "statDate")
    private LocalDate statDate;

    /** 业绩值 */
    @Schema(description = "performanceValue")
    private BigDecimal performanceValue;

    /** 排名 */
    @Schema(description = "ranking")
    private Integer ranking;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;
}
