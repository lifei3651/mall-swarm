package com.macro.mall.distribution.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssetTransferDTO {

    private Long fromAgentId;

    private Long fromUserId;

    private Long toAgentId;

    private Long toUserId;

    private BigDecimal amount;

    private String bizType;

    private String bizId;

    private String remark;
}
