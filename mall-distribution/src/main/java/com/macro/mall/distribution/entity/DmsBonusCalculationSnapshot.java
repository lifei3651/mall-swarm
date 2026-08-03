package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖金计算输入和结果快照
 */
@Data
public class DmsBonusCalculationSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private Long ruleVersionId;

    private Long orderId;

    private String orderNo;

    private String inputJson;

    private String resultJson;

    private BigDecimal totalPv;

    private BigDecimal totalBonus;

    private String riskStatus;

    private LocalDateTime createTime;
}
