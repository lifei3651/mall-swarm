package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsCommissionClawback;
import com.macro.mall.distribution.entity.DmsFinanceRefund;
import com.macro.mall.distribution.entity.DmsOrderBalanceAllocation;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OrderFinanceDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private OrderFinanceVO finance;

    private List<CommissionRecordVO> bonusFlows;

    /** 订单奖金从关系冻结、计算、实际记录到入账和退款冲销的完整只读链路。 */
    private OrderBonusTraceVO bonusTrace;

    private List<OrderCompanyShareVO> companyShares;

    private List<DmsFinanceRefund> refunds;

    private List<DmsCommissionClawback> clawbacks;

    /** 产品成本、剩余商品款进入指定真实余额的独立明细（不计入推广奖金）。 */
    private List<DmsOrderBalanceAllocation> balanceAllocations;

}
