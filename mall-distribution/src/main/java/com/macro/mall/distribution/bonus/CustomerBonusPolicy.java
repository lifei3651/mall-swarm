package com.macro.mall.distribution.bonus;

import java.util.List;

/**
 * 客户独立奖金程序的稳定接入边界。
 *
 * <p>商城基座只负责提供订单快照并落库、结算和退款追回；每个客户派生项目
 * 自行提供本接口的实现，基座不预设客户的奖金名称、层级、条件或比例。</p>
 */
public interface CustomerBonusPolicy {

    /** 与奖金规则版本表中的 versionNo 一致。 */
    String policyCode();

    /**
     * 根据支付时冻结的订单与关系数据返回本单奖金结果。
     * 实现不得直接修改钱包或结算状态，这些资金动作统一由商城基座执行。
     */
    List<CustomerBonusPayout> calculate(CustomerBonusOrderContext context);

    /**
     * 奖金记录已经按支付时状态冻结后的客户制度扩展点。
     * 例如客户项目可在这里刷新只影响后续订单的自定义等级或复购资格。
     */
    default void afterOrder(CustomerBonusOrderContext context) {
        // 通用基座没有客户制度副作用。
    }

    /**
     * 退款已由基座完成资金追回和业绩冲销后的客户制度扩展点。
     * 例如客户项目可在这里重算其自定义身份、等级或复购资格；基座默认不做任何调级。
     */
    default void afterRefund(CustomerBonusRefundContext context) {
        // 通用基座没有客户制度副作用。
    }
}
