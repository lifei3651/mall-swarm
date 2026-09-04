package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 微信支付订单发货信息同步任务；只保存业务单号和脱敏结果，不保存OpenID。 */
@Data
public class DmsWechatShippingSyncTask implements Serializable {
    private Long id;
    @JsonIgnore private Long tenantId;
    private String paymentOrderNo;
    @JsonIgnore private Long userId;
    private String status;
    private Integer revision;
    private Integer syncedRevision;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    @JsonIgnore private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String payloadDigest;
    private String errorCode;
    private LocalDateTime syncedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
