package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.WithdrawalLimitProperties;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsWithdrawRecordDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.dto.WithdrawApplyDTO;
import com.macro.mall.distribution.entity.DmsWithdrawRecord;
import com.macro.mall.distribution.service.impl.WithdrawServiceImpl;
import com.macro.mall.distribution.vo.WithdrawalLimitUsageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalLimitServiceTest {

    private DmsWithdrawRecordDao withdrawDao;
    private WithdrawServiceImpl service;
    private MemberMessageService messages;
    private MemberAssetService assets;

    @BeforeEach
    void setUp() {
        withdrawDao = mock(DmsWithdrawRecordDao.class);
        DmsAgentDao agentDao = mock(DmsAgentDao.class);
        DmsAgent agent = new DmsAgent();
        agent.setId(1L);
        agent.setUserId(70L);
        when(agentDao.selectByIdForUpdate(1L)).thenReturn(agent);
        WithdrawalLimitProperties limits = new WithdrawalLimitProperties();
        limits.setDailyMaxCount(3);
        limits.setMonthlyMaxCount(10);
        limits.setDailyMaxAmount(new BigDecimal("100.00"));
        limits.setMonthlyMaxAmount(new BigDecimal("500.00"));
        messages = mock(MemberMessageService.class);
        assets = mock(MemberAssetService.class);
        service = new WithdrawServiceImpl(withdrawDao, mock(AgentAccountService.class),
                assets, agentDao, mock(DmsShopMemberDao.class),
                mock(OperationLogService.class), limits, messages);
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

    @Test
    void submittedWithdrawalPublishesThePersistedRecordAsAStableFact() {
        when(withdrawDao.selectLimitUsage(eq(1L), any(), any())).thenReturn(usage(0, "0", 0, "0"));
        doAnswer(invocation -> { DmsWithdrawRecord record = invocation.getArgument(0); record.setId(91L); return 1; })
                .when(withdrawDao).insert(any());
        WithdrawApplyDTO dto = new WithdrawApplyDTO(); dto.setAgentId(1L); dto.setWithdrawAmount(new BigDecimal("30.00"));
        dto.setWithdrawType(1); dto.setBankName("测试银行"); dto.setBankAccount("已由上层加密"); dto.setAccountName("测试");

        service.applyWithdraw(dto);

        verify(assets).withdraw(any());
        ArgumentCaptor<MemberMessageEvent> event = ArgumentCaptor.forClass(MemberMessageEvent.class);
        verify(messages).publish(event.capture());
        assertEquals("WITHDRAW_SUBMITTED:91", event.getValue().eventKey());
        assertEquals("WITHDRAW_SUBMITTED", event.getValue().eventType());
        assertEquals("WALLET_FUNDS", event.getValue().category());
        assertEquals("WITHDRAWAL", event.getValue().targetType());
        assertEquals(91L, event.getValue().targetId());
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
