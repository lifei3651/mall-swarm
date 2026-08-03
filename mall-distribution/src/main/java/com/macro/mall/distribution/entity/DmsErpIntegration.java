package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

@Data
public class DmsErpIntegration {
    private Long id;
    private Long tenantId;
    private String providerCode;
    private String integrationName;
    private Integer enabled;
    private String environment;
    private String endpoint;
    private String appKey;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String appSecret;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String callbackToken;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
