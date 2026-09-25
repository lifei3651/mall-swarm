package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopTrade;
import com.macro.mall.distribution.dto.BalancePayDTO;
import com.macro.mall.distribution.service.impl.ShopWalletServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopWalletPaymentChannelTest {

    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsAgentDao agentDao;
    @Mock private DmsMemberAssetAccountDao assetAccountDao;
    @Mock private DmsShopOrderDao orderDao;
    @Mock private DmsShopTradeDao tradeDao;
    @Mock private MemberAssetService memberAssetService;
    @Mock private ShopService shopService;
    @Mock private PaymentPasswordAttemptService passwordAttemptService;
    @Mock private SmsVerificationService smsVerificationService;
    @Mock private WithdrawService withdrawService;
    @Mock private MemberMessageService memberMessageService;
    @Mock private RealNameVerificationService realNameVerificationService;
    @Mock private WithdrawalRiskPolicyService withdrawalRiskPolicyService;
    @InjectMocks private ShopWalletServiceImpl walletService;

    @Test
    void balanceEndpointRejectsPendingOrderSelectedForAnotherChannel() {
        DmsShopMember member = member();
        DmsShopOrder order = order(0);
        when(memberDao.selectById(1L)).thenReturn(member);
        when(orderDao.selectTradeIdById(11L)).thenReturn(null);
        when(orderDao.selectByIdForUpdate(11L)).thenReturn(order);

        assertThrows(ApiException.class, () -> walletService.payOrder(member, 11L, new BalancePayDTO()));

        verifyNoInteractions(memberAssetService, shopService);
    }

    @Test
    void balanceEndpointDoesNotReportAnotherChannelsPaidOrderAsBalanceSuccess() {
        DmsShopMember member = member();
        DmsShopOrder order = order(1);
        when(memberDao.selectById(1L)).thenReturn(member);
        when(orderDao.selectTradeIdById(11L)).thenReturn(null);
        when(orderDao.selectByIdForUpdate(11L)).thenReturn(order);

        assertThrows(ApiException.class, () -> walletService.payOrder(member, 11L, new BalancePayDTO()));

        verifyNoInteractions(memberAssetService, shopService);
    }

    @Test
    void balanceEndpointDoesNotReportAnotherChannelsPaidParentTradeAsBalanceSuccess() {
        DmsShopMember member = member();
        DmsShopTrade trade = new DmsShopTrade();
        trade.setId(22L);
        trade.setUserId(100L);
        trade.setStatus(1);
        trade.setPayType("WECHAT");
        when(memberDao.selectById(1L)).thenReturn(member);
        when(orderDao.selectTradeIdById(22L)).thenReturn(null);
        when(tradeDao.selectByIdForUpdate(22L)).thenReturn(trade);

        assertThrows(ApiException.class, () -> walletService.payOrder(member, 22L, new BalancePayDTO()));

        verifyNoInteractions(memberAssetService, shopService);
    }

    private DmsShopMember member() {
        DmsShopMember member = new DmsShopMember();
        member.setId(1L);
        member.setUserId(100L);
        member.setStatus(1);
        return member;
    }

    private DmsShopOrder order(int status) {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(11L);
        order.setUserId(100L);
        order.setStatus(status);
        order.setPayType("WECHAT");
        return order;
    }
}
