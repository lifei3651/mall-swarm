package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopTrade;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.ShopCatalogCacheService;
import com.macro.mall.distribution.service.impl.ShopServiceImpl;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.ShopTradeDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiMerchantCheckoutTest {

    @Mock private DmsShopOrderDao orderDao;
    @Mock private DmsShopOrderItemDao orderItemDao;
    @Mock private DmsShopTradeDao tradeDao;
    @Mock private ShopCatalogCacheService catalogCache;
    @Mock private MerchantService merchantService;
    @Spy @InjectMocks private ShopServiceImpl shopService;

    @AfterEach
    void clearAdminContext() {
        AdminContext.clear();
    }

    @Test
    void parentPaymentPostsEveryMerchantChildAndReturnsGroupedResult() {
        DmsShopTrade pending = trade(0, "150.00");
        DmsShopTrade paid = trade(1, "150.00");
        DmsShopOrder first = child(11L, "60.00");
        DmsShopOrder second = child(12L, "90.00");
        when(tradeDao.selectByIdForUpdate(10L)).thenReturn(pending);
        when(orderDao.selectByTradeIdForUpdate(10L)).thenReturn(List.of(first, second));
        when(tradeDao.markPaid(10L, "ALIPAY")).thenReturn(1);
        when(tradeDao.selectById(10L)).thenReturn(paid);
        when(orderDao.selectByTradeId(10L)).thenReturn(List.of(first, second));
        doReturn(orderView(first)).when(shopService).markOrderPaid(11L, "ALIPAY");
        doReturn(orderView(second)).when(shopService).markOrderPaid(12L, "ALIPAY");
        doReturn(orderView(first)).when(shopService).getOrder(11L);
        doReturn(orderView(second)).when(shopService).getOrder(12L);

        ShopOrderVO result = shopService.markCheckoutPaid(10L, "ALIPAY");

        assertEquals(10L, result.getCheckoutId());
        assertEquals("T-10", result.getCheckoutNo());
        assertEquals(2, result.getChildOrders().size());
        verify(shopService).markOrderPaid(11L, "ALIPAY");
        verify(shopService).markOrderPaid(12L, "ALIPAY");
        verify(tradeDao).markPaid(10L, "ALIPAY");
    }

    @Test
    void parentChildAmountMismatchStopsPaymentBeforeAnyChildIsPosted() {
        when(tradeDao.selectByIdForUpdate(10L)).thenReturn(trade(0, "149.99"));
        when(orderDao.selectByTradeIdForUpdate(10L)).thenReturn(List.of(
                child(11L, "60.00"), child(12L, "90.00")));

        assertThrows(ApiException.class, () -> shopService.markCheckoutPaid(10L, "ALIPAY"));

        verify(shopService, never()).markOrderPaid(anyLong(), anyString());
        verify(tradeDao, never()).markPaid(anyLong(), anyString());
    }

    @Test
    void paymentChannelCannotOverrideParentTradeChoice() {
        DmsShopTrade trade = trade(0, "150.00");
        trade.setPayType("BALANCE");
        when(tradeDao.selectByIdForUpdate(10L)).thenReturn(trade);
        when(orderDao.selectByTradeIdForUpdate(10L)).thenReturn(List.of(
                child(11L, "60.00"), child(12L, "90.00")));

        assertThrows(ApiException.class, () -> shopService.markCheckoutPaid(10L, "ALIPAY"));

        verify(shopService, never()).markOrderPaid(anyLong(), anyString());
        verifyNoInteractions(merchantService);
        verify(tradeDao, never()).markPaid(anyLong(), anyString());
    }

    @Test
    void changedChildPaymentNumberStopsWholeCheckout() {
        DmsShopOrder first = child(11L, "60.00");
        DmsShopOrder second = child(12L, "90.00");
        second.setPaymentOrderNo("OTHER-PAYMENT");
        when(tradeDao.selectByIdForUpdate(10L)).thenReturn(trade(0, "150.00"));
        when(orderDao.selectByTradeIdForUpdate(10L)).thenReturn(List.of(first, second));

        assertThrows(ApiException.class, () -> shopService.markCheckoutPaid(10L, "ALIPAY"));

        verify(shopService, never()).markOrderPaid(anyLong(), anyString());
        verify(tradeDao, never()).markPaid(anyLong(), anyString());
    }

    @Test
    void cancellingOnePendingChildClosesEverySiblingAndParentTrade() {
        DmsShopOrder first = child(11L, "60.00");
        DmsShopOrder second = child(12L, "90.00");
        when(tradeDao.selectByIdForUpdate(10L)).thenReturn(trade(0, "150.00"));
        when(orderDao.selectByTradeIdForUpdate(10L)).thenReturn(List.of(first, second));
        when(orderDao.cancel(11L)).thenReturn(1);
        when(orderDao.cancel(12L)).thenReturn(1);
        when(orderDao.selectById(11L)).thenReturn(first);
        when(orderDao.selectById(12L)).thenReturn(second);
        when(tradeDao.closePending(10L)).thenReturn(1);
        DmsShopMember member = new DmsShopMember();
        member.setUserId(100L);

        assertEquals(true, shopService.cancelCheckout(10L, member));

        verify(orderDao).cancel(11L);
        verify(orderDao).cancel(12L);
        verify(tradeDao).closePending(10L);
        verify(catalogCache, times(2)).invalidateAfterCommit(1L);
    }

    @Test
    void platformCanInspectWholeTradeButMerchantCannotReadTheAggregate() {
        DmsShopTrade trade = trade(1, "150.00");
        DmsShopOrder first = child(11L, "60.00");
        DmsShopOrder second = child(12L, "90.00");
        when(tradeDao.selectById(10L)).thenReturn(trade);
        when(orderDao.selectByTradeId(10L)).thenReturn(List.of(first, second));
        doReturn(orderView(first)).when(shopService).getOrder(11L);
        doReturn(orderView(second)).when(shopService).getOrder(12L);

        ShopTradeDetailVO detail = shopService.getAdminTrade(10L);

        assertEquals(2, detail.getChildCount());
        assertEquals(new BigDecimal("150.00"), detail.getChildPayAmount());

        DmsAdminUser merchant = new DmsAdminUser();
        merchant.setMerchantId(9001L);
        AdminContext.set(merchant);
        assertThrows(ApiException.class, () -> shopService.getAdminTrade(10L));
    }

    private DmsShopTrade trade(int status, String amount) {
        DmsShopTrade trade = new DmsShopTrade();
        trade.setId(10L);
        trade.setTradeNo("T-10");
        trade.setTenantId(1L);
        trade.setUserId(100L);
        trade.setPayType("ALIPAY");
        trade.setStatus(status);
        trade.setPayAmount(new BigDecimal(amount));
        return trade;
    }

    private DmsShopOrder child(long id, String amount) {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(id);
        order.setOrderNo("O-" + id);
        order.setTradeId(10L);
        order.setTradeNo("T-10");
        order.setPaymentOrderNo("T-10");
        order.setTenantId(1L);
        order.setUserId(100L);
        order.setStatus(0);
        order.setPayAmount(new BigDecimal(amount));
        return order;
    }

    private ShopOrderVO orderView(DmsShopOrder order) {
        ShopOrderVO vo = new ShopOrderVO();
        vo.setOrder(order);
        return vo;
    }
}
