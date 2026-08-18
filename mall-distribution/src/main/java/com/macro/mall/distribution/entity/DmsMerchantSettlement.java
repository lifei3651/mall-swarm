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
    /** 下单时锁定的结算等待天数，不受商品或商户后续修改影响。 */
    private Integer settlementDelayDays;
    /** 确认收货时按当时售后规则和订单快照固化的预计可结算时间。 */
    private LocalDateTime eligibleTime;
    private String status;
    private LocalDateTime availableTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
