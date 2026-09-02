package com.macro.mall.distribution.controller;

import com.macro.mall.distribution.dto.ShopWithdrawalApplyDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopWalletService;
import com.macro.mall.distribution.service.WithdrawalPayoutService;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalSingleReviewControllerTest {

    @Test
    void automaticallyApprovedApplicationImmediatelyStartsPayout() {
        ShopAuthService auth = mock(ShopAuthService.class);
        ShopWalletService wallet = mock(ShopWalletService.class);
        WithdrawalPayoutService payouts = mock(WithdrawalPayoutService.class);
        ShopWalletController controller = new ShopWalletController(auth, wallet, payouts);
        DmsShopMember member = new DmsShopMember();
        ShopWithdrawalApplyDTO dto = new ShopWithdrawalApplyDTO();
        WithdrawRecordVO approved = record(81L, 1);
        WithdrawRecordVO paying = record(81L, 2);
        when(auth.requireMember("token")).thenReturn(member);
        when(wallet.applyWithdrawal(member, dto)).thenReturn(approved);
        when(wallet.listWithdrawals(member)).thenReturn(List.of(paying));

        var response = controller.applyWithdrawal("token", dto);

        verify(payouts).start(81L);
        assertEquals(2, response.getData().getStatus());
    }

    @Test
    void riskFlaggedApplicationWaitsForItsSingleManualApproval() {
        ShopAuthService auth = mock(ShopAuthService.class);
        ShopWalletService wallet = mock(ShopWalletService.class);
        WithdrawalPayoutService payouts = mock(WithdrawalPayoutService.class);
        ShopWalletController controller = new ShopWalletController(auth, wallet, payouts);
        DmsShopMember member = new DmsShopMember();
        ShopWithdrawalApplyDTO dto = new ShopWithdrawalApplyDTO();
        when(auth.requireMember("token")).thenReturn(member);
        when(wallet.applyWithdrawal(member, dto)).thenReturn(record(82L, 0));

        var response = controller.applyWithdrawal("token", dto);

        verify(payouts, never()).start(82L);
        assertEquals(0, response.getData().getStatus());
    }

    private WithdrawRecordVO record(Long id, Integer status) {
        WithdrawRecordVO record = new WithdrawRecordVO();
        record.setId(id);
        record.setStatus(status);
        return record;
    }
}
