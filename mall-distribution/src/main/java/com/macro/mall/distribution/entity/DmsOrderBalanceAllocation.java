package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单商品款进入指定会员余额的独立凭证。
 *
 * <p>这不是推广奖金，不能混入佣金记录或奖金拨出率。产品成本和扣除成本、
 * 推广奖金后的剩余商品款分别留痕，并与订单采用相同的收货后7天保护期。</p>
 */
@Data
public class DmsOrderBalanceAllocation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long orderId;
    private String orderNo;
    private String allocationType;
    private Long targetMemberId;
    private Long targetUserId;
    private Long targetAgentId;
    private String targetAccount;
    private BigDecimal originalAmount;
    private BigDecimal currentAmount;
    private BigDecimal settledAmount;
    private BigDecimal reversedAmount;
    /** 0-待结算 1-已结算 2-已全部冲回/无需结算。 */
    private Integer status;
    private LocalDateTime settleTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
