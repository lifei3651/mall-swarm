package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 后台余额流水查询结果。 */
@Data
public class BalanceFlowVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String flowNo;
    private Long agentId;
    private Long userId;
    private Long memberId;
    private String memberName;
    private String memberUsername;
    private String memberPhone;
    private Long relatedAgentId;
    private Long relatedUserId;
    private Integer changeType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long operatorId;
    private String operatorName;
    private String bizType;
    private String bizId;
    private String remark;
    private LocalDateTime createTime;
}
