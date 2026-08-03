package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员资产余额
 */
@Data
public class DmsMemberAssetAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long agentId;

    private Long userId;

    private String assetCode;

    private String assetName;

    private BigDecimal balance;

    private BigDecimal frozenBalance;

    private BigDecimal totalIn;

    private BigDecimal totalOut;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
