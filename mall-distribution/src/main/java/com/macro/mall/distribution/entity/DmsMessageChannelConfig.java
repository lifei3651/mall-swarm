package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMessageChannelConfig implements Serializable {
    private Long id;
    private Long tenantId;
    private String eventType;
    private Integer inAppEnabled;
    private Integer smsEnabled;
    private Integer appPushEnabled;
    private Integer miniProgramEnabled;
    private BigDecimal estimatedSmsCost;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
