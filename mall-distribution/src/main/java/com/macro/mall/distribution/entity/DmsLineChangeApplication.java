package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsLineChangeApplication implements Serializable {
    private Long id;
    private String applyNo;
    private Long agentId;
    private Long oldParentAgentId;
    private Long newParentAgentId;
    private String reason;
    /** 0旧版待处理 1旧版已通过待生效 2已取消/拒绝 3已执行 */
    private Integer status;
    private Long applicantId;
    private String applicantName;
    private Long auditorId;
    private String auditorName;
    private String auditRemark;
    private LocalDateTime effectiveTime;
    private String beforeSnapshot;
    private String afterSnapshot;
    private LocalDateTime auditTime;
    private LocalDateTime executeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
