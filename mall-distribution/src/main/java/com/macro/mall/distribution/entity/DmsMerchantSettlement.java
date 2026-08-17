package com.macro.mall.distribution.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsMerchantSettlement implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long tenantId;
    private Long merchantId;
    private String merchantName;
    private Long orderId;
    private String orderNo;
    private Long orderItemId;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private Integer refundedQuantity;
    private BigDecimal costAmount;
    private BigDecimal settlementAmount;
    private BigDecimal reversedAmount;
    private String status;
    private LocalDateTime availableTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
