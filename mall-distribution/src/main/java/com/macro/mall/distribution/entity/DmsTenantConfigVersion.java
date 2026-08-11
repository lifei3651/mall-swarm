package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商城客户配置历史版本。每个版本同时保存商城资料和前台展示配置快照。
 */
@Data
public class DmsTenantConfigVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String versionNo;
    private String changeType;
    private String tenantSnapshot;
    private String displaySnapshot;
    private Long operatorId;
    private String operatorName;
    private Long sourceVersionId;
    private LocalDateTime createTime;
}
