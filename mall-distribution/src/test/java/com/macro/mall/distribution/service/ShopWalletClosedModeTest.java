package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.dto.BalancePayDTO;
import com.macro.mall.distribution.dto.BalanceTransferDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopTrade;
import com.macro.mall.distribution.service.impl.ShopWalletServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ShopWalletClosedModeTest {

    @Mock private DmsShopMemberDao members;
    @Mock private DmsAgentDao agents;
    @Mock private DmsMemberAssetAccountDao accounts;
    @Mock private DmsShopOrderDao orders;
    @Mock private DmsShopTradeDao trades;
    @Mock private MemberAssetService assets;
    @Mock private ShopService shop;
    @Mock private PaymentPasswordAttemptService passwords;
    @Mock private SmsVerificationService sms;
    @Mock private WithdrawService withdrawals;
    @Mock private MemberMessageService messages;
    @Mock private RealNameVerificationService realNames;
    @Mock private WithdrawalRiskPolicyService withdrawalRisk;
    @Mock private BalanceTransactionModeService mode;
    @InjectMocks private ShopWalletServiceImpl wallet;

    @BeforeEach void useTestTenant() { TenantContext.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void closedModeRejectsSingleOrderBeforeConsumingFunds() {
        DmsShopMember member = member();
        DmsShopOrder order = new DmsShopOrder();
        order.setId(11L); order.setTenantId(7L); order.setUserId(101L);
        order.setStatus(0); order.setPayType("BALANCE"); order.setPayAmount(BigDecimal.ONE);
        when(members.selectById(1L)).thenReturn(member);
        when(orders.selectTradeIdById(11L)).thenReturn(null);
        when(orders.selectByIdForUpdate(11L)).thenReturn(order);
        doThrow(new ApiException("本商城已关闭新余额交易"))
                .when(mode).requireEnabledForNewTransaction(7L);

        assertThrows(ApiException.class, () -> wallet.payOrder(member, 11L, new BalancePayDTO()));

        verify(assets, never()).consume(any());
        verify(shop, never()).markOrderPaid(any(), any());
    }

    @Test
    void closedModeRejectsParentCheckoutBeforeConsumingFunds() {
        DmsShopMember member = member();
        DmsShopTrade trade = new DmsShopTrade();
        trade.setId(22L); trade.setTenantId(7L); trade.setUserId(101L);
        trade.setStatus(0); trade.setPayType("BALANCE"); trade.setPayAmount(BigDecimal.ONE);
        when(members.selectById(1L)).thenReturn(member);
        when(orders.selectTradeIdById(22L)).thenReturn(22L);
        when(trades.selectByIdForUpdate(22L)).thenReturn(trade);
        doThrow(new ApiException("本商城已关闭新余额交易"))
                .when(mode).requireEnabledForNewTransaction(7L);

        assertThrows(ApiException.class, () -> wallet.payOrder(member, 22L, new BalancePayDTO()));

        verify(assets, never()).consume(any());
        verify(shop, never()).markCheckoutPaid(any(), any());
    }

    @Test
    void closedModeRejectsTransferBeforeRecipientOrPasswordWork() {
        DmsShopMember member = member();
        when(members.selectById(1L)).thenReturn(member);
        doThrow(new ApiException("本商城已关闭新余额交易"))
                .when(mode).requireEnabledForNewTransaction(1L);
        BalanceTransferDTO dto = new BalanceTransferDTO();
        dto.setRecipientPhone("13900000002"); dto.setAmount(BigDecimal.ONE);

        assertThrows(ApiException.class, () -> wallet.transfer(member, dto));

        verify(assets, never()).transfer(any());
    }

    private DmsShopMember member() {
        DmsShopMember member = new DmsShopMember();
        member.setId(1L); member.setUserId(101L); member.setStatus(1);
        return member;
    }
}
