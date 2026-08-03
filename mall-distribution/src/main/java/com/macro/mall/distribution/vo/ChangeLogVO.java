package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更日志VO
 */
@Data
public class ChangeLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long id;

    /** 代理ID */
    private Long agentId;

    /** 变更类型 */
    private Integer changeType;

    /** 变更类型名称 */
    private String changeTypeName;

    /** 原上级代理ID */
    private Long oldParentAgentId;

    /** 原上级名称 */
    private String oldParentName;

    /** 新上级代理ID */
    private Long newParentAgentId;

    /** 新上级名称 */
    private String newParentName;

    /** 原等级 */
    private Integer oldLevel;

    /** 原等级名称 */
    private String oldLevelName;

    /** 新等级 */
    private Integer newLevel;

    /** 新等级名称 */
    private String newLevelName;

    /** 变更原因 */
    private String changeReason;

    /** 操作人名称 */
    private String operatorName;

    /** 操作人类型 */
    private Integer operatorType;

    /** 操作人类型名称 */
    private String operatorTypeName;

    /** 创建时间 */
    private LocalDateTime createTime;
}
