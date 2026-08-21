package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户一次结算、一次支付对应的交易父单；实际履约继续由商户子订单承担。 */
@Data
public class DmsShopTrade implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String tradeNo;
    private Long tenantId;
    private Long userId;
    private String payType;
    private BigDecimal totalAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    /** 0待付款、1已支付、4已关闭。履约状态由子订单分别维护。 */
    private Integer status;
    /** 0未处理、1超时关单后的迟到支付已原路退款。 */
    private Integer lateRefundFlag;
    private LocalDateTime payTime;
    private LocalDateTime closeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
