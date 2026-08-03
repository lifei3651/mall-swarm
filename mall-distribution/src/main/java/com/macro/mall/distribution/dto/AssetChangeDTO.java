package com.macro.mall.distribution.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssetChangeDTO {

    private Long agentId;

    private Long userId;

    private BigDecimal amount;

    private String bizType;

    private String bizId;

    /** 客户端为高风险人工调账生成的唯一请求号，用于防止重复入账/扣款。 */
    private String requestId;

    private String remark;
}
