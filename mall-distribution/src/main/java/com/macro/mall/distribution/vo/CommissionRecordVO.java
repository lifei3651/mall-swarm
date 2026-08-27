package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 佣金记录VO
 */
@Data
public class CommissionRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tenantId;

    private Long ruleVersionId;

    /** 记录ID */
    private Long id;

    /** 记录编号 */
    private String recordNo;

    /** 订单编号 */
    private String orderNo;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 下单用户名称 */
    private String orderUserName;

    /** 下单会员登录账号。 */
    private String orderMemberAccount;

    /** 代理名称 */
    private String agentName;

    /** 获奖会员登录账号。 */
    private String agentMemberAccount;

    /** 代理等级 */
    private Integer agentLevel;

    /** 佣金层级 */
    private Integer commissionLevel;

    /** 客户奖金程序返回的类型编码。 */
    private String bonusType;

    /** 佣金层级名称 */
    private String commissionLevelName;

    /** 佣金比例 */
    private BigDecimal commissionRate;

    /** 佣金金额 */
    private BigDecimal commissionAmount;

    /** 状态 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 结算时间 */
    private LocalDateTime settleTime;

    private String cancelReason;

    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
