package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.WithdrawalLimitProperties;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsWithdrawRecordDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.service.impl.WithdrawServiceImpl;
import com.macro.mall.distribution.vo.WithdrawalLimitUsageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WithdrawalLimitServiceTest {

    private DmsWithdrawRecordDao withdrawDao;
    private WithdrawServiceImpl service;

    @BeforeEach
    void setUp() {
        withdrawDao = mock(DmsWithdrawRecordDao.class);
        DmsAgentDao agentDao = mock(DmsAgentDao.class);
        DmsAgent agent = new DmsAgent();
        agent.setId(1L);
        when(agentDao.selectByIdForUpdate(1L)).thenReturn(agent);
        WithdrawalLimitProperties limits = new WithdrawalLimitProperties();
        limits.setDailyMaxCount(3);
        limits.setMonthlyMaxCount(10);
        limits.setDailyMaxAmount(new BigDecimal("100.00"));
        limits.setMonthlyMaxAmount(new BigDecimal("500.00"));
        service = new WithdrawServiceImpl(withdrawDao, mock(AgentAccountService.class),
                mock(MemberAssetService.class), agentDao, mock(DmsShopMemberDao.class),
                mock(OperationLogService.class), limits);
    }

    @Test
    void allowsRequestInsideAllLimits() {
        when(withdrawDao.selectLimitUsage(eq(1L), any(), any())).thenReturn(usage(1, "20", 4, "100"));
        assertDoesNotThrow(() -> service.validateWithdrawalLimits(1L, new BigDecimal("30.00")));
    }

    @Test
    void rejectsDailyAndMonthlyCountOrAmountOverflow() {
        when(withdrawDao.selectLimitUsage(eq(1L), any(), any()))
                .thenReturn(usage(3, "20", 4, "100"))
                .thenReturn(usage(1, "20", 10, "100"))
                .thenReturn(usage(1, "90", 4, "100"))
                .thenReturn(usage(1, "20", 4, "490"));

        assertThrows(ApiException.class, () -> service.validateWithdrawalLimits(1L, BigDecimal.TEN));
        assertThrows(ApiException.class, () -> service.validateWithdrawalLimits(1L, BigDecimal.TEN));
        assertThrows(ApiException.class, () -> service.validateWithdrawalLimits(1L, new BigDecimal("20")));
        assertThrows(ApiException.class, () -> service.validateWithdrawalLimits(1L, new BigDecimal("20")));
    }

    private WithdrawalLimitUsageVO usage(long dailyCount, String dailyAmount,
                                         long monthlyCount, String monthlyAmount) {
        WithdrawalLimitUsageVO usage = new WithdrawalLimitUsageVO();
        usage.setDailyCount(dailyCount);
        usage.setDailyAmount(new BigDecimal(dailyAmount));
        usage.setMonthlyCount(monthlyCount);
        usage.setMonthlyAmount(new BigDecimal(monthlyAmount));
        return usage;
    }
}
