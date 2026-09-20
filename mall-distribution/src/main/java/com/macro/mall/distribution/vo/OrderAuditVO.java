package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderAuditVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    private String orderNo;

    private BigDecimal orderAmount;

    private LocalDateTime orderTime;

    private Long ownerUserId;

    private String ownerMemberAccount;

    private String ownerMemberName;

    private Long ownerAgentId;

    private String ownerAgentName;

    private BigDecimal productCost;

    private BigDecimal bonusAmount;

    private BigDecimal companyProfit;

    /** ESTIMATED=售后期结束前的预计利润；REALIZED=售后期结束后的已实现利润。 */
    private String profitStage;

    private String profitStageName;

    private Integer riskStatus;
}
