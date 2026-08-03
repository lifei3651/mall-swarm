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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
