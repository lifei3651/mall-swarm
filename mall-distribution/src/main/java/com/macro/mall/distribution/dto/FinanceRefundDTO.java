package com.macro.mall.distribution.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceRefundDTO {

    private Long orderId;

    private String orderNo;

    private String refundNo;

    private BigDecimal refundAmount;

    private BigDecimal productRefundAmount;

    private BigDecimal freightRefundAmount;

    private Integer refundQuantity;

    /** 本单支付时真正进入团队奖金基数的商品实付金额。 */
    private BigDecimal bonusBaseAmount;

    /** 本次退款中属于团队奖金商品的金额；普通商品退款必须为0。 */
    private BigDecimal bonusRefundAmount;

    /** 本次退款中属于团队奖金商品的数量。 */
    private Integer bonusRefundQuantity;

    /** 截至本次退款，团队奖金商品累计已退款金额。 */
    private BigDecimal cumulativeBonusRefundAmount;

    private Integer clawbackBonus;

    private String reason;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime refundTime;
}
