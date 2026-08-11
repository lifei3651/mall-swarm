package com.macro.mall.distribution.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 商城配置版本列表，仅返回历史元数据，避免把完整配置快照传到列表页。 */
@Data
public class TenantConfigVersionVO {
    private Long id;
    private Long tenantId;
    private String versionNo;
    private String changeType;
    private Long operatorId;
    private String operatorName;
    private Long sourceVersionId;
    private LocalDateTime createTime;
}
