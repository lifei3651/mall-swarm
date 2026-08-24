package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMessageDeliveryTask implements Serializable {
    private Long id;
    @JsonIgnore
    private Long tenantId;
    private Long messageId;
    private String eventType;
    private String channel;
    @JsonIgnore
    private String idempotencyKey;
    private String status;
    private Integer retryCount;
    private Integer attemptCount;
    private Integer maxAttempts;
    private BigDecimal estimatedCost;
    private BigDecimal actualCost;
    private String providerCode;
    @JsonIgnore
    private String providerMessageId;
    private String errorCode;
    @JsonIgnore
    private String errorMessage;
    @JsonIgnore
    private String leaseOwner;
    @JsonIgnore
    private LocalDateTime leaseUntil;
    private LocalDateTime nextRetryTime;
    private LocalDateTime expiresAt;
    private LocalDateTime sentTime;
    private LocalDateTime acceptedTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
