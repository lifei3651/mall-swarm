package com.macro.mall.distribution.service;

import com.macro.mall.distribution.config.WithdrawalLimitProperties;
import com.macro.mall.distribution.dao.DmsFinanceRiskRuleDao;
import com.macro.mall.distribution.entity.DmsFinanceRiskRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WithdrawalRiskPolicyServiceTest {

    @Test
    void adminRuleOverridesDeploymentDefaultAndDisabledRuleMakesEveryWithdrawalManual() {
        DmsFinanceRiskRuleDao dao = mock(DmsFinanceRiskRuleDao.class);
        WithdrawalLimitProperties defaults = new WithdrawalLimitProperties();
        defaults.setManualReviewThreshold(new BigDecimal("1000.00"));
        WithdrawalRiskPolicyService service = new WithdrawalRiskPolicyService(dao, defaults);

        assertEquals(0, new BigDecimal("1000.00").compareTo(service.manualReviewThreshold()));

        DmsFinanceRiskRule rule = new DmsFinanceRiskRule();
        rule.setEnabled(1);
        rule.setThresholdValue(new BigDecimal("2000.00"));
        when(dao.selectByCode(WithdrawalRiskPolicyService.MANUAL_REVIEW_AMOUNT_RULE)).thenReturn(rule);
        assertEquals(0, new BigDecimal("2000.00").compareTo(service.manualReviewThreshold()));

        rule.setEnabled(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(service.manualReviewThreshold()));
    }
}
