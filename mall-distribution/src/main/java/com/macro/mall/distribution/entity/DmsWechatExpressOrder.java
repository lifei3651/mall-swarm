package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 微信物流助手生成的快递运单与商城包裹关联。 */
@Data
public class DmsWechatExpressOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long userId;
    private String requestKey;
    private String expressOrderNo;
    private String deliveryId;
    private String deliveryName;
    private String bizId;
    private Integer serviceType;
    private String serviceName;
    private Integer shipmentQuantity;
    private Integer packageCount;
    private BigDecimal weight;
    private BigDecimal packageLength;
    private BigDecimal packageWidth;
    private BigDecimal packageHeight;
    private String remark;
    private String status;
    private String waybillId;
    private Long shipmentId;
    private String errorCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
