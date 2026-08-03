package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代理关系实体类
 * 支持切线（代理关系变更）
 * 对应数据库表：dms_agent_relation
 */
@Data
@Schema(description = "代理关系信息")
public class DmsAgentRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID */
    @Schema(description = "id")
    private Long id;

    /** 用户ID */
    @Schema(description = "userId")
    private Long userId;

    /** 代理ID */
    @Schema(description = "agentId")
    private Long agentId;

    /** 上级用户ID */
    @Schema(description = "parentUserId")
    private Long parentUserId;

    /** 上级代理ID */
    @Schema(description = "parentAgentId")
    private Long parentAgentId;

    /**
     * 关系层级
     * 1-直属 2-二级 3-三级...
     */
    @Schema(description = "relationLevel")
    private Integer relationLevel;

    /** 关系路径，如：1001/1005/1012 */
    @Schema(description = "relationPath")
    private String relationPath;

    /**
     * 是否有效
     * 0-无效（切线后失效） 1-有效
     */
    @Schema(description = "isValid")
    private Integer isValid;

    /**
     * 绑定方式
     * 1-扫码绑定 2-邀请码绑定 3-后台绑定 4-导入绑定
     */
    @Schema(description = "bindType")
    private Integer bindType;

    /** 绑定时间 */
    @Schema(description = "bindTime")
    private LocalDateTime bindTime;

    /** 解绑时间（切线时记录） */
    @Schema(description = "unbindTime")
    private LocalDateTime unbindTime;

    /** 解绑原因 */
    @Schema(description = "unbindReason")
    private String unbindReason;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;
}
