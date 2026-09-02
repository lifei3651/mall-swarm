package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsWithdrawalPayoutDao;
import com.macro.mall.distribution.dao.DmsWithdrawRecordDao;
import com.macro.mall.distribution.entity.DmsWithdrawalPayout;
import com.macro.mall.distribution.entity.DmsWithdrawRecord;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.service.MemberMessageService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.WithdrawalPayoutGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalPayoutTransactionServiceTest {
    @Mock private DmsWithdrawRecordDao withdrawDao;
    @Mock private DmsWithdrawalPayoutDao payoutDao;
    @Mock private DmsShopMemberDao memberDao;
    @Mock private AgentAccountService accountService;
    @Mock private OperationLogService operationLogService;
    @Mock private MemberMessageService memberMessageService;

    @Test
    void acceptedOrUnverifiableChannelResponseNeverMarksFundsPaid() {
        DmsWithdrawRecord withdraw = payingWithdrawal();
        DmsWithdrawalPayout payout = processingPayout();
        when(withdrawDao.selectByIdForUpdate(1L)).thenReturn(withdraw);
        when(payoutDao.selectByWithdrawIdForUpdate(1L)).thenReturn(payout);

        var result = new WithdrawalPayoutGateway.PayoutResult(
                WithdrawalPayoutGateway.State.PROCESSING, "WD-1", "provider-1", "ACCEPTED",
                new BigDecimal("100.00"), "recipient-hash", "response-digest", null, null);
        var view = service().apply(1L, result);

        assertEquals("PROCESSING", view.getState());
        assertEquals(2, withdraw.getStatus());
        verify(accountService, never()).addWithdrawnAmount(any(), any());
        verify(withdrawDao, never()).update(any());
    }

    @Test
    void successWithWrongAmountIsDowngradedToUnknownAndCannotBookMoney() {
        DmsWithdrawRecord withdraw = payingWithdrawal();
        DmsWithdrawalPayout payout = processingPayout();
        when(withdrawDao.selectByIdForUpdate(1L)).thenReturn(withdraw);
        when(payoutDao.selectByWithdrawIdForUpdate(1L)).thenReturn(payout);

        var result = new WithdrawalPayoutGateway.PayoutResult(
                WithdrawalPayoutGateway.State.SUCCESS, "WD-1", "provider-1", "SUCCESS",
                new BigDecimal("99.99"), "recipient-hash", "response-digest", null, null);
        var view = service().apply(1L, result);

        assertEquals("UNKNOWN", view.getState());
        assertEquals(2, withdraw.getStatus());
        verify(accountService, never()).addWithdrawnAmount(any(), any());
        verify(withdrawDao, never()).update(any());
    }

    @Test
    void onlyFullyMatchedFinalSuccessBooksWithdrawalExactlyOnce() {
        DmsWithdrawRecord withdraw = payingWithdrawal();
        DmsWithdrawalPayout payout = processingPayout();
        when(withdrawDao.selectByIdForUpdate(1L)).thenReturn(withdraw);
        when(payoutDao.selectByWithdrawIdForUpdate(1L)).thenReturn(payout);
        when(accountService.addWithdrawnAmount(9L, new BigDecimal("100.00"))).thenReturn(true);
        when(withdrawDao.update(withdraw)).thenReturn(1);

        var result = new WithdrawalPayoutGateway.PayoutResult(
                WithdrawalPayoutGateway.State.SUCCESS, "WD-1", "provider-1", "SUCCESS",
                new BigDecimal("100.00"), "recipient-hash", "response-digest", null, null);
        var view = service().apply(1L, result);

        assertEquals("SUCCESS", view.getState());
        assertEquals(3, withdraw.getStatus());
        assertEquals("provider-1", withdraw.getPayNo());
        verify(accountService).addWithdrawnAmount(9L, new BigDecimal("100.00"));
        verify(withdrawDao).update(withdraw);
    }

    @Test
    void retryUsesANewRequestNumberOnlyAfterExplicitFailure() {
        DmsWithdrawRecord withdraw = payingWithdrawal();
        DmsWithdrawalPayout payout = processingPayout();
        payout.setState("UNKNOWN");
        when(withdrawDao.selectByIdForUpdate(1L)).thenReturn(withdraw);
        when(payoutDao.selectByWithdrawIdForUpdate(1L)).thenReturn(payout);
        DmsShopMember member = new DmsShopMember();
        member.setId(77L);
        member.setUserId(99L);
        when(memberDao.selectByUserId(99L)).thenReturn(member);

        var unknown = service().reserve(1L);
        assertFalse(unknown.initiate());
        assertEquals("WD-1", unknown.command().requestNo());

        payout.setState("FAILED");
        withdraw.setStatus(1);
        assertThrows(ApiException.class, () -> service().reserve(1L, false));

        when(withdrawDao.update(withdraw)).thenReturn(1);
        var retry = service().reserve(1L, true);
        assertTrue(retry.initiate());
        assertEquals("WD-1R2", retry.command().requestNo());
        ArgumentCaptor<DmsWithdrawalPayout> saved = ArgumentCaptor.forClass(DmsWithdrawalPayout.class);
        verify(payoutDao).update(saved.capture());
        assertEquals(2, saved.getValue().getAttemptNo());
    }

    @Test
    void reconcileCannotCreateAMissingPayoutRequest() {
        DmsWithdrawRecord withdraw = payingWithdrawal();
        withdraw.setStatus(1);
        when(withdrawDao.selectByIdForUpdate(1L)).thenReturn(withdraw);
        when(payoutDao.selectByWithdrawIdForUpdate(1L)).thenReturn(null);

        assertThrows(ApiException.class, () -> service().reserve(1L, false));

        verify(payoutDao, never()).insert(any());
        verify(withdrawDao, never()).update(any());
    }

    @Test
    void wechatConfirmationPackageIsOnlyReturnedToTheWithdrawalOwner() {
        DmsWithdrawRecord withdraw = payingWithdrawal();
        withdraw.setWithdrawType(2);
        DmsWithdrawalPayout payout = processingPayout();
        payout.setChannel("WECHAT");
        payout.setState("WAIT_USER_CONFIRM");
        payout.setConfirmationPackage("package-token");
        when(withdrawDao.selectById(1L)).thenReturn(withdraw);
        when(payoutDao.selectByWithdrawId(1L)).thenReturn(payout);

        assertThrows(ApiException.class, () -> service().memberConfirmation(1L, 100L));
        var confirmation = service().memberConfirmation(1L, 99L);

        assertEquals("WAIT_USER_CONFIRM", confirmation.state());
        assertEquals("package-token", confirmation.packageInfo());
    }

    @Test
    void nonTerminalQueryKeepsExistingWechatConfirmationEvidence() {
        DmsWithdrawRecord withdraw = payingWithdrawal();
        withdraw.setWithdrawType(2);
        DmsWithdrawalPayout payout = processingPayout();
        payout.setChannel("WECHAT");
        payout.setState("WAIT_USER_CONFIRM");
        payout.setProviderOrderNo("wx-bill-1");
        payout.setConfirmationPackage("package-token");
        when(withdrawDao.selectByIdForUpdate(1L)).thenReturn(withdraw);
        when(payoutDao.selectByWithdrawIdForUpdate(1L)).thenReturn(payout);

        var result = new WithdrawalPayoutGateway.PayoutResult(
                WithdrawalPayoutGateway.State.PROCESSING, "WD-1", null, "PROCESSING",
                new BigDecimal("100.00"), "recipient-hash", "response-digest", null, null);
        var view = service().apply(1L, result);

        assertEquals("wx-bill-1", payout.getProviderOrderNo());
        assertEquals("package-token", payout.getConfirmationPackage());
        assertEquals("PROCESSING", view.getState());
    }

    private WithdrawalPayoutTransactionService service() {
        return new WithdrawalPayoutTransactionService(withdrawDao, payoutDao, memberDao,
                accountService, operationLogService, memberMessageService);
    }

    private DmsWithdrawRecord payingWithdrawal() {
        DmsWithdrawRecord withdraw = new DmsWithdrawRecord();
        withdraw.setId(1L);
        withdraw.setWithdrawNo("WD-1");
        withdraw.setAgentId(9L);
        withdraw.setUserId(99L);
        withdraw.setWithdrawType(3);
        withdraw.setWithdrawAmount(new BigDecimal("100.00"));
        withdraw.setBankAccount("member@example.com");
        withdraw.setAccountName("测试会员");
        withdraw.setStatus(2);
        return withdraw;
    }

    private DmsWithdrawalPayout processingPayout() {
        DmsWithdrawalPayout payout = new DmsWithdrawalPayout();
        payout.setId(8L);
        payout.setWithdrawId(1L);
        payout.setWithdrawNo("WD-1");
        payout.setAttemptNo(1);
        payout.setRequestNo("WD-1");
        payout.setChannel("ALIPAY");
        payout.setState("PROCESSING");
        payout.setAmount(new BigDecimal("100.00"));
        return payout;
    }
}
