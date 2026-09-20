package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 微信物流消息运单登记任务。 */
@Data
public class DmsWechatLogisticsFollowTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long shipmentId;
    private Long userId;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String payloadDigest;
    private String errorCode;
    private LocalDateTime registeredTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
