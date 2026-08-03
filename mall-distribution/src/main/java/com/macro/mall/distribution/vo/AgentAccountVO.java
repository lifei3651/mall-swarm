package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 代理账户VO
 */
@Data
public class AgentAccountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 代理ID */
    private Long agentId;

    /** 用户ID */
    private Long userId;

    /** 商城登录账号（字段名为兼容旧接口保留）。 */
    private String memberAccount;

    /** 累计佣金 */
    private BigDecimal totalCommission;

    /** 已结算佣金 */
    private BigDecimal settledCommission;

    /** 待结算佣金 */
    private BigDecimal unsettledCommission;

    /** 冻结佣金 */
    private BigDecimal frozenCommission;

    /** 已提现金额 */
    private BigDecimal withdrawnAmount;

    /** 可提现余额 */
    private BigDecimal availableBalance;

    /** 本人及无限层团队累计有效商品件数 */
    private Integer totalOrders;

    /** 团队成员数 */
    private Integer totalTeamMembers;
}
