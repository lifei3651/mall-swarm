package com.macro.mall.distribution.entity;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsMemberRealName implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private Integer status;
    @ToString.Exclude
    private String realName;
    @ToString.Exclude
    private String idCard;
    private String provider;
    private String providerRequestId;
    private String consentVersion;
    private LocalDateTime consentTime;
    private LocalDateTime verifiedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
