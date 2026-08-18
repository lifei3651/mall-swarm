package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.config.AlipayConfig;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopTrade;
import com.macro.mall.distribution.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AlipayNotifyFailureTest {

    private AlipayConfig config;
    private DmsShopOrderDao orderDao;
    private ShopService shopService;
    private DmsShopTradeDao tradeDao;

    @BeforeEach
    void setUp() {
        config = mock(AlipayConfig.class);
        orderDao = mock(DmsShopOrderDao.class);
        shopService = mock(ShopService.class);
        tradeDao = mock(DmsShopTradeDao.class);
        when(config.isConfigured()).thenReturn(true);
        when(config.getAppId()).thenReturn("test-app-id");
    }

    @Test
    void invalidSignatureReturnsFailureBeforeReadingOrder() {
        AlipayServiceImpl service = service(false);

        assertEquals("failure", service.handleNotify(validParams()));

        verifyNoInteractions(orderDao, shopService);
    }

    @Test
    void nonSuccessTradeStatusNeverMarksOrderPaid() {
        AlipayServiceImpl service = service(true);
        DmsShopOrder order = pendingOrder();
        when(orderDao.selectByOrderNoForUpdate("ORDER-1")).thenReturn(order);
        Map<String, String> params = validParams();
        params.put("trade_status", "WAIT_BUYER_PAY");

        assertEquals("success", service.handleNotify(params));

        verify(shopService, never()).markOrderPaid(anyLong(), anyString());
    }

    @Test
    void processingFailureReturnsFailureSoAlipayCanRetry() {
        AlipayServiceImpl service = service(true);
        when(orderDao.selectByOrderNoForUpdate("ORDER-1")).thenReturn(pendingOrder());
        doThrow(new IllegalStateException("temporary database failure"))
                .when(shopService).markOrderPaid(101L, "ALIPAY");

        assertEquals("failure", service.handleNotify(validParams()));
    }

    @Test
    void successfulCallbackMarksPendingOrderOnlyOnce() {
        AlipayServiceImpl service = service(true);
        when(orderDao.selectByOrderNoForUpdate("ORDER-1")).thenReturn(pendingOrder());

        assertEquals("success", service.handleNotify(validParams()));
        verify(shopService).markOrderPaid(101L, "ALIPAY");
    }

    @Test
    void duplicateSuccessfulNotificationReturnsSuccessWithoutProcessingAgain() {
        AlipayServiceImpl service = service(true);
        DmsShopOrder paidOrder = pendingOrder();
        paidOrder.setStatus(1);
        when(orderDao.selectByOrderNoForUpdate("ORDER-1")).thenReturn(pendingOrder(), paidOrder);

        assertEquals("success", service.handleNotify(validParams()));
        assertEquals("success", service.handleNotify(validParams()));

        verify(shopService, times(1)).markOrderPaid(101L, "ALIPAY");
    }

    @Test
    void groupedTradeCallbackMarksAllChildrenThroughParentTrade() {
        AlipayServiceImpl service = service(true);
        when(tradeDao.selectByTradeNoForUpdate("ORDER-1")).thenReturn(pendingTrade());

        assertEquals("success", service.handleNotify(validParams()));

        verify(shopService).markCheckoutPaid(201L, "ALIPAY");
        verifyNoInteractions(orderDao);
    }

    @Test
    void groupedTradeAmountMismatchIsRejectedBeforePostingChildren() {
        AlipayServiceImpl service = service(true);
        DmsShopTrade trade = pendingTrade();
        trade.setPayAmount(new BigDecimal("100.00"));
        when(tradeDao.selectByTradeNoForUpdate("ORDER-1")).thenReturn(trade);

        assertEquals("failure", service.handleNotify(validParams()));

        verify(shopService, never()).markCheckoutPaid(anyLong(), anyString());
        verifyNoInteractions(orderDao);
    }

    @Test
    void duplicateGroupedTradeCallbackDoesNotPostChildrenAgain() {
        AlipayServiceImpl service = service(true);
        DmsShopTrade paid = pendingTrade();
        paid.setStatus(1);
        when(tradeDao.selectByTradeNoForUpdate("ORDER-1")).thenReturn(pendingTrade(), paid);

        assertEquals("success", service.handleNotify(validParams()));
        assertEquals("success", service.handleNotify(validParams()));

        verify(shopService, times(1)).markCheckoutPaid(201L, "ALIPAY");
        verifyNoInteractions(orderDao);
    }

    private AlipayServiceImpl service(boolean signatureValid) {
        return new AlipayServiceImpl(config, orderDao, tradeDao, shopService, new ObjectMapper()) {
            @Override
            protected boolean verifyNotifySignature(Map<String, String> params) {
                return signatureValid;
            }
        };
    }

    private Map<String, String> validParams() {
        Map<String, String> params = new HashMap<>();
        params.put("trade_no", "ALIPAY-1");
        params.put("out_trade_no", "ORDER-1");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "99.00");
        params.put("app_id", "test-app-id");
        return params;
    }

    private DmsShopOrder pendingOrder() {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(101L);
        order.setOrderNo("ORDER-1");
        order.setStatus(0);
        order.setPayAmount(new BigDecimal("99.00"));
        return order;
    }

    private DmsShopTrade pendingTrade() {
        DmsShopTrade trade = new DmsShopTrade();
        trade.setId(201L);
        trade.setTradeNo("ORDER-1");
        trade.setStatus(0);
        trade.setPayAmount(new BigDecimal("99.00"));
        return trade;
    }
}
