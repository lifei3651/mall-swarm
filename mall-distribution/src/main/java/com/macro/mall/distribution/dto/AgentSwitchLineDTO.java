package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @NotNull(message = "请选择移线会员")
    @Positive(message = "移线会员编号不正确")
    private Long agentId;

    /** 新上级代理ID */
    @NotNull(message = "请选择新直属上级")
    @Positive(message = "新直属上级编号不正确")
    private Long newParentAgentId;

    /** 切线原因 */
    @NotBlank(message = "移线原因不能为空")
    @Size(max = 300, message = "移线原因不能超过300个字")
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
