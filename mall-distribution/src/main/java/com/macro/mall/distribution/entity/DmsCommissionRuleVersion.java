package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户奖金规则版本
 */
@Data
public class DmsCommissionRuleVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private String versionNo;

    private String versionName;

    private Integer status;

    private LocalDateTime effectiveTime;

    private String remark;

    /** 创建规则版本时保存，后续配置变更只创建新版本，不改此快照。 */
    private String directReferralConfig;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
