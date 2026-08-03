package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代理切线DTO
 */
@Data
public class AgentSwitchLineDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 代理ID（要切线的代理） */
    private Long agentId;

    /** 新上级代理ID */
    private Long newParentAgentId;

    /** 切线原因 */
    private String reason;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 操作人类型 */
    private Integer operatorType;

    /** 审核通过后的计划生效时间；为空表示立即生效 */
    private LocalDateTime effectiveTime;
}
