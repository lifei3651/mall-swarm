package com.macro.mall.distribution.service;

import com.macro.mall.distribution.config.WithdrawalLimitProperties;
import com.macro.mall.distribution.dao.DmsFinanceRiskRuleDao;
import com.macro.mall.distribution.entity.DmsFinanceRiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 提现自动审核的单一阈值来源；后台规则优先，部署默认值只负责安全兜底。 */
@Service
@RequiredArgsConstructor
public class WithdrawalRiskPolicyService {
    public static final String MANUAL_REVIEW_AMOUNT_RULE = "MEMBER_WITHDRAW_MANUAL_REVIEW_AMOUNT";

    private final DmsFinanceRiskRuleDao riskRuleDao;
    private final WithdrawalLimitProperties defaults;

    public BigDecimal manualReviewThreshold() {
        DmsFinanceRiskRule rule = riskRuleDao.selectByCode(MANUAL_REVIEW_AMOUNT_RULE);
        if (rule == null) return defaults.getManualReviewThreshold();
        if (!Integer.valueOf(1).equals(rule.getEnabled())) return BigDecimal.ZERO;
        BigDecimal value = rule.getThresholdValue();
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }
}
