package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsWechatMiniProgramIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long memberId;
    private Long userId;
    private String appIdHash;
    private String openIdHash;
    private String unionIdHash;

    @JsonIgnore
    private String openId;

    @JsonIgnore
    private String unionId;

    private String privacyConsentVersion;
    private LocalDateTime privacyConsentTime;
    private LocalDateTime phoneAuthorizedTime;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
