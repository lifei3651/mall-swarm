package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员资产流水
 */
@Data
public class DmsMemberAssetFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String flowNo;

    private Long agentId;

    private Long userId;

    private Long relatedAgentId;

    private Long relatedUserId;

    private String assetCode;

    private String assetName;

    /**
     * 1-发放 2-消费 3-转出 4-转入 5-扣减
     */
    private Integer changeType;

    private BigDecimal amount;

    private BigDecimal balanceAfter;

    private String bizType;

    private String bizId;

    private String remark;

    private LocalDateTime createTime;
}
