package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMessageDeliveryAttempt implements Serializable {
    private Long id;
    @JsonIgnore private Long tenantId;
    private Long taskId;
    private Integer attemptNo;
    @JsonIgnore private String idempotencyKey;
    private String state;
    private String providerCode;
    @JsonIgnore private String providerMessageId;
    private Integer queryCount;
    private BigDecimal estimatedCost;
    private BigDecimal actualCost;
    private String errorCode;
    @JsonIgnore private String errorMessage;
    private LocalDateTime submittedTime;
    private LocalDateTime resolvedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
