package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 微信一次性订阅授权；不保存模板原文或OpenID明文。 */
@Data
public class DmsMiniProgramSubscriptionGrant implements Serializable {
    private Long id;
    @JsonIgnore private Long tenantId;
    @JsonIgnore private Long memberId;
    @JsonIgnore private Long userId;
    @JsonIgnore private String templateIdHash;
    @JsonIgnore private String clientRequestId;
    private String status;
    @JsonIgnore private Long reservedTaskId;
    private LocalDateTime authorizedTime;
    private LocalDateTime reservedTime;
    private LocalDateTime consumedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
