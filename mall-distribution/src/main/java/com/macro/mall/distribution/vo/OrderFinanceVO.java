package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderFinanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    private String orderNo;

    /** 下单时冻结的应付快照；未支付时不是实际到账金额，实际净收款见 netPayAmount。 */
    private BigDecimal payAmount;

    private BigDecimal refundAmount;

    private BigDecimal netPayAmount;

    private BigDecimal productCost;

    private BigDecimal bonusAmount;

    private BigDecimal companyShareAmount;

    private BigDecimal companyProfit;

    private Integer riskStatus;

    private String riskStatusName;

    private String remark;

    private LocalDateTime updateTime;
}
