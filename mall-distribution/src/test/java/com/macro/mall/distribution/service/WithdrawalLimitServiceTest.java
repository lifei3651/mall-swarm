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
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import com.macro.mall.distribution.vo.WithdrawalLimitUsageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private WithdrawalPayoutService payouts;
    private WithdrawalRiskPolicyService riskPolicy;

    @BeforeEach
    void setUp() {
        withdrawDao = mock(DmsWithdrawRecordDao.class);
        DmsAgentDao agentDao = mock(DmsAgentDao.class);
        DmsAgent agent = new DmsAgent();
        agent.setId(1L);
        agent.setUserId(70L);
        when(agentDao.selectByIdForUpdate(1L)).thenReturn(agent);
        WithdrawalLimitProperties limits = new WithdrawalLimitProperties();
        limits.setDailyMaxCount(2);
        limits.setMonthlyMaxCount(10);
        limits.setManualReviewThreshold(new BigDecimal("1000.00"));
        messages = mock(MemberMessageService.class);
        assets = mock(MemberAssetService.class);
        payouts = mock(WithdrawalPayoutService.class);
        riskPolicy = mock(WithdrawalRiskPolicyService.class);
        when(riskPolicy.manualReviewThreshold()).thenReturn(new BigDecimal("1000.00"));
        service = new WithdrawServiceImpl(withdrawDao, mock(AgentAccountService.class),
                assets, agentDao, mock(DmsShopMemberDao.class),
                mock(OperationLogService.class), limits, messages, payouts, riskPolicy);
    }

    @Test
    void allowsRequestInsideAllLimits() {
        when(withdrawDao.selectLimitUsage(eq(1L), any(), any())).thenReturn(usage(1, "20", 4, "100"));
        assertDoesNotThrow(() -> service.validateWithdrawalLimits(1L, new BigDecimal("30.00")));
    }

    @Test
    void rejectsDailyAndMonthlyRequestCountButNotCumulativeAmount() {
        when(withdrawDao.selectLimitUsage(eq(1L), any(), any()))
                .thenReturn(usage(2, "999999", 4, "999999"))
                .thenReturn(usage(1, "999999", 10, "999999"))
                .thenReturn(usage(1, "999999", 4, "999999"));

        assertThrows(ApiException.class, () -> service.validateWithdrawalLimits(1L, BigDecimal.TEN));
        assertThrows(ApiException.class, () -> service.validateWithdrawalLimits(1L, BigDecimal.TEN));
        assertDoesNotThrow(() -> service.validateWithdrawalLimits(1L, new BigDecimal("2000")));
    }

    @Test
    void familiarPayeeWithinSingleThresholdIsAutomaticallyApproved() {
        when(withdrawDao.selectLimitUsage(eq(1L), any(), any())).thenReturn(usage(0, "0", 0, "0"));
        when(payouts.isReady(3)).thenReturn(true);
        DmsWithdrawRecord previous = successful("member@example.com");
        when(withdrawDao.selectLatestSuccessfulByAgentAndType(1L, 3)).thenReturn(previous);

        WithdrawRecordVO record = service.applyWithdraw(withdrawal("900.00", "member@example.com"));

        assertEquals(1, record.getStatus());
        assertTrue(record.getAuditRemark().contains("系统风控自动通过"));
    }

    @Test
    void firstLargeOrChangedPayeeWithdrawalNeedsOnlyOneManualReview() {
        when(withdrawDao.selectLimitUsage(eq(1L), any(), any())).thenReturn(usage(0, "0", 0, "0"));
        when(payouts.isReady(3)).thenReturn(true);

        WithdrawRecordVO first = service.applyWithdraw(withdrawal("200.00", "first@example.com"));
        assertEquals(0, first.getStatus());
        assertTrue(first.getAuditRemark().contains("首次提现"));

        when(withdrawDao.selectLatestSuccessfulByAgentAndType(1L, 3))
                .thenReturn(successful("old@example.com"));
        WithdrawRecordVO changed = service.applyWithdraw(withdrawal("200.00", "new@example.com"));
        assertEquals(0, changed.getStatus());
        assertTrue(changed.getAuditRemark().contains("收款账号"));

        WithdrawRecordVO large = service.applyWithdraw(withdrawal("1000.01", "old@example.com"));
        assertEquals(0, large.getStatus());
        assertTrue(large.getAuditRemark().contains("超过1000元"));
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

    private WithdrawApplyDTO withdrawal(String amount, String account) {
        WithdrawApplyDTO dto = new WithdrawApplyDTO();
        dto.setAgentId(1L);
        dto.setWithdrawAmount(new BigDecimal(amount));
        dto.setWithdrawType(3);
        dto.setBankName("支付宝");
        dto.setBankAccount(account);
        dto.setAccountName("测试");
        return dto;
    }

    private DmsWithdrawRecord successful(String account) {
        DmsWithdrawRecord record = new DmsWithdrawRecord();
        record.setBankAccount(account);
        record.setStatus(3);
        return record;
    }
}
