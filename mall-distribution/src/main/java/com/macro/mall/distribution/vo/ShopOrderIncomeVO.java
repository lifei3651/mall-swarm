package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 当前登录会员在一笔订单中的真实奖金结算视图，不包含公司利润或他人奖金。 */
@Data
public class ShopOrderIncomeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前仍有效的本人奖金合计（待结算 + 已结算）。 */
    private BigDecimal totalAmount;

    private BigDecimal pendingAmount;

    private BigDecimal settledAmount;

    private List<IncomeLine> details;

    @Data
    public static class IncomeLine implements Serializable {
        private static final long serialVersionUID = 1L;

        private String bonusType;
        private Integer commissionLevel;
        private BigDecimal commissionRate;
        private BigDecimal commissionAmount;
        private Integer status;
        private String statusName;
        private LocalDateTime settleTime;
    }
}
