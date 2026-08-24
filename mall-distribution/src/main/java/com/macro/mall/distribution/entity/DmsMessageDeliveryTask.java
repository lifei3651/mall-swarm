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
    private String channel;
    private String status;
    private Integer retryCount;
    private BigDecimal estimatedCost;
    @JsonIgnore
    private String providerMessageId;
    private String errorCode;
    @JsonIgnore
    private String errorMessage;
    private LocalDateTime nextRetryTime;
    private LocalDateTime sentTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
