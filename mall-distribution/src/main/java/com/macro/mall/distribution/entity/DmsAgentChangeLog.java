package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代理变更日志实体类
 * 对应数据库表：dms_agent_change_log
 */
@Data
@Schema(description = "代理变更日志")
public class DmsAgentChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志ID */
    @Schema(description = "id")
    private Long id;

    /** 代理ID */
    @Schema(description = "agentId")
    private Long agentId;

    /** 用户ID */
    @Schema(description = "userId")
    private Long userId;

    /**
     * 变更类型
     * 1-切线 2-升级 3-降级 4-冻结 5-解冻 6-信息变更
     */
    @Schema(description = "changeType")
    private Integer changeType;

    /** 原上级代理ID */
    @Schema(description = "oldParentAgentId")
    private Long oldParentAgentId;

    /** 原上级名称 */
    @Schema(description = "oldParentName")
    private String oldParentName;

    /** 新上级代理ID */
    @Schema(description = "newParentAgentId")
    private Long newParentAgentId;

    /** 新上级名称 */
    @Schema(description = "newParentName")
    private String newParentName;

    /** 原等级 */
    @Schema(description = "oldLevel")
    private Integer oldLevel;

    /** 新等级 */
    @Schema(description = "newLevel")
    private Integer newLevel;

    /** 变更原因 */
    @Schema(description = "changeReason")
    private String changeReason;

    /** 变更详情JSON */
    @Schema(description = "changeDetail")
    private String changeDetail;

    /** 操作人ID */
    @Schema(description = "operatorId")
    private Long operatorId;

    /** 操作人名称 */
    @Schema(description = "operatorName")
    private String operatorName;

    /**
     * 操作人类型
     * 1-系统 2-管理员 3-代理自己
     */
    @Schema(description = "operatorType")
    private Integer operatorType;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;
}
