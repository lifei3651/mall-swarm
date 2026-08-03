package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖金异步计算任务
 */
@Data
public class DmsBonusCalculationTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private Long ruleVersionId;

    private Long orderId;

    private String orderNo;

    private BigDecimal orderAmount;

    private Long orderUserId;

    /** 仅用于后台返回，不落库。 */
    private String orderMemberAccount;

    private String orderUserName;

    /**
     * 0-待处理 1-处理中 2-成功 3-失败
     */
    private Integer status;

    private Integer retryCount;

    private Integer maxRetryCount;

    private String failReason;

    private LocalDateTime nextRetryTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
