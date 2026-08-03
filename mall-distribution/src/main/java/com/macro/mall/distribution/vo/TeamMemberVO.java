package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 团队成员VO
 */
@Data
public class TeamMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 代理ID */
    private Long agentId;

    /** 用户ID */
    private Long userId;

    /** 代理编号 */
    private String agentCode;

    /** 代理名称 */
    private String agentName;

    /** 代理等级 */
    private Integer agentLevel;

    /** 代理等级名称 */
    private String agentLevelName;

    /** 关系层级 */
    private Integer relationLevel;

    /** 关系层级名称 */
    private String relationLevelName;

    /** 个人业绩 */
    private BigDecimal personalPerformance;

    /** 团队业绩 */
    private BigDecimal teamPerformance;

    /** 订单数 */
    private Integer orderCount;

    /** 团队人数 */
    private Integer teamMemberCount;

    /** 状态 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 加入时间 */
    private LocalDateTime joinTime;
}
