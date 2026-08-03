package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 代理账户实体类
 * 对应数据库表：dms_agent_account
 */
@Data
@Schema(description = "代理账户信息")
public class DmsAgentAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "账户ID")
    private Long id;

    @Schema(description = "代理ID")
    private Long agentId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "累计佣金")
    private BigDecimal totalCommission;

    @Schema(description = "已结算佣金")
    private BigDecimal settledCommission;

    @Schema(description = "待结算佣金")
    private BigDecimal unsettledCommission;

    @Schema(description = "冻结佣金")
    private BigDecimal frozenCommission;

    @Schema(description = "已提现金额")
    private BigDecimal withdrawnAmount;

    @Schema(description = "可提现余额")
    private BigDecimal availableBalance;

    @Schema(description = "本人及无限层团队累计有效商品件数")
    private Integer totalOrders;

    @Schema(description = "团队成员数")
    private Integer totalTeamMembers;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
