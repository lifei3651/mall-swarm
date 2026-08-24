package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsMemberRealNameAttempt implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private String provider;
    private String resultCode;
    private Integer matched;
    private String providerRequestId;
    private LocalDateTime createTime;
}
