package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 下属业绩贡献实体类
 * 对应数据库表：dms_subordinate_contribution
 */
@Data
@Schema(description = "下属业绩贡献")
public class DmsSubordinateContribution implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID */
    @Schema(description = "id")
    private Long id;

    /** 代理ID（被贡献者） */
    @Schema(description = "agentId")
    private Long agentId;

    /** 下属代理ID（贡献者） */
    @Schema(description = "subordinateAgentId")
    private Long subordinateAgentId;

    /** 下属用户ID */
    @Schema(description = "subordinateUserId")
    private Long subordinateUserId;

    /** 下属名称 */
    @Schema(description = "subordinateName")
    private String subordinateName;

    /**
     * 关系层级
     * 1-直属，2及以上为无限层间接关系
     */
    @Schema(description = "relationLevel")
    private Integer relationLevel;

    /** 统计日期 */
    @Schema(description = "statDate")
    private LocalDate statDate;

    /**
     * 统计类型
     * 1-日 2-周 3-月 4-年
     */
    @Schema(description = "statType")
    private Integer statType;

    /** 贡献业绩金额 */
    @Schema(description = "contributionAmount")
    private BigDecimal contributionAmount;

    /** 贡献商品件数（业务口径中的累计单量） */
    @Schema(description = "orderCount")
    private Integer orderCount;

    /** 下属自己的业绩 */
    @Schema(description = "selfPerformance")
    private BigDecimal selfPerformance;

    /** 下属团队的业绩（不含下属自己） */
    @Schema(description = "teamPerformance")
    private BigDecimal teamPerformance;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "updateTime")
    private LocalDateTime updateTime;
}
