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

    /** 变动前余额 */
    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    /** 执行本次变动的管理员；系统自动流水为空或为 system。 */
    private Long operatorId;

    private String operatorName;

    private String bizType;

    private String bizId;

    private String remark;

    private LocalDateTime createTime;
}
