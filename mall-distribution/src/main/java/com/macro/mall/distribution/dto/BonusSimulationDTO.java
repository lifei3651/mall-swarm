package com.macro.mall.distribution.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BonusSimulationDTO {

    private Long tenantId;

    private Long ruleVersionId;

    private Long orderUserId;

    /** 登录账号或手机号。 */
    private String orderMemberKey;

    private String orderUserName;

    private BigDecimal orderAmount;
}
