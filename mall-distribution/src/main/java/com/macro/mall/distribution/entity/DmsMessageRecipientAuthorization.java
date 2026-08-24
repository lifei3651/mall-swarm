package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsMessageRecipientAuthorization implements Serializable {
    private Long id;
    @JsonIgnore private Long tenantId;
    @JsonIgnore private Long memberId;
    private String channel;
    @JsonIgnore private String endpointHash;
    private Integer authorized;
    private LocalDateTime authorizedTime;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedTime;
}
