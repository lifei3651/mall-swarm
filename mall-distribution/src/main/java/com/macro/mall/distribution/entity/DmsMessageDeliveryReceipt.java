package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsMessageDeliveryReceipt implements Serializable {
    private Long id;
    private Long tenantId;
    private String channel;
    private String providerCode;
    private String receiptId;
    private Long taskId;
    private String payloadDigest;
    private Integer signatureValid;
    private String receiptStatus;
    private String errorCode;
    private LocalDateTime receivedTime;
}
