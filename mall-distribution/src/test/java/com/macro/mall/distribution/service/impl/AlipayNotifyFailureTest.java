package com.macro.mall.distribution.service.impl;

import com.alipay.api.response.AlipayTradeQueryResponse;
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
        when(config.getSellerId()).thenReturn("2088123456789012");
    }

    @Test
    void invalidSignatureReturnsFailureBeforeReadingOrder() {
        AlipayServiceImpl service = service(false);

        assertEquals("failure", service.handleNotify(validParams()));

        verifyNoInteractions(orderDao, shopService);
    }

    @Test
    void callbackForAnotherSellerIsRejectedBeforeReadingOrder() {
        AlipayServiceImpl service = service(true);
        Map<String, String> params = validParams();
        params.put("seller_id", "2088000000000000");

        assertEquals("failure", service.handleNotify(params));

        verifyNoInteractions(orderDao, shopService);
    }

    @Test
    void sdkClientUsesBoundedConnectionAndReadTimeouts() {
        AlipayConfig actual = new AlipayConfig();
        actual.setGatewayUrl("https://openapi.alipay.com/gateway.do");
        actual.setAppId("app-1");
        actual.setPrivateKey("private-key");
        actual.setAlipayPublicKey("public-key");
        actual.setSignType("RSA2");
        actual.setConnectTimeoutMs(7000);
        actual.setReadTimeoutMs(12000);
        AlipayServiceImpl service = new AlipayServiceImpl(actual, orderDao, tradeDao, shopService, new ObjectMapper());

        com.alipay.api.AlipayConfig sdk = service.buildSdkConfig();

        assertEquals(7000, sdk.getConnectTimeout());
        assertEquals(12000, sdk.getReadTimeout());
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

    @Test
    void paymentArrivingAfterTimeoutCloseIsRefundedWithStableIdempotencyNumber() {
        AlipayServiceImpl service = spy(service(true));
        DmsShopOrder closed = pendingOrder();
        closed.setStatus(4);
        when(orderDao.selectByOrderNoForUpdate("ORDER-1")).thenReturn(closed);
        when(orderDao.markLateRefunded(101L)).thenReturn(1);
        doReturn(true).when(service).refund("ORDER-1",
                "LATEPAY-" + cn.hutool.crypto.SecureUtil.sha256("ORDER-1").substring(0, 32),
                "99.00", "订单超时关闭后的支付自动退回");

        assertEquals("success", service.handleNotify(validParams()));
        assertEquals("success", service.handleNotify(validParams()));

        verify(shopService, never()).markOrderPaid(anyLong(), anyString());
        verify(service, times(1)).refund("ORDER-1",
                "LATEPAY-" + cn.hutool.crypto.SecureUtil.sha256("ORDER-1").substring(0, 32),
                "99.00", "订单超时关闭后的支付自动退回");
        verify(orderDao, times(1)).markLateRefunded(101L);
    }

    @Test
    void failedLatePaymentRefundReturnsFailureSoGatewayRetries() {
        AlipayServiceImpl service = spy(service(true));
        DmsShopTrade closed = pendingTrade();
        closed.setStatus(4);
        when(tradeDao.selectByTradeNoForUpdate("ORDER-1")).thenReturn(closed);
        doReturn(false).when(service).refund(anyString(), anyString(), anyString(), anyString());

        assertEquals("failure", service.handleNotify(validParams()));
        verify(shopService, never()).markCheckoutPaid(anyLong(), anyString());
        verify(tradeDao, never()).markLateRefunded(anyLong());
    }

    @Test
    void groupedLatePaymentRefundIsPersistedAndDuplicateNotificationIsAcknowledged() {
        AlipayServiceImpl service = spy(service(true));
        DmsShopTrade closed = pendingTrade();
        closed.setStatus(4);
        when(tradeDao.selectByTradeNoForUpdate("ORDER-1")).thenReturn(closed);
        when(tradeDao.markLateRefunded(201L)).thenReturn(1);
        doReturn(true).when(service).refund(anyString(), anyString(), anyString(), anyString());

        assertEquals("success", service.handleNotify(validParams()));
        assertEquals("success", service.handleNotify(validParams()));

        verify(service, times(1)).refund(anyString(), anyString(), anyString(), anyString());
        verify(tradeDao, times(1)).markLateRefunded(201L);
        verifyNoInteractions(orderDao);
    }

    @Test
    void latePaymentRefundMarkerFailureKeepsGatewayRetryEnabled() {
        AlipayServiceImpl service = spy(service(true));
        DmsShopTrade closed = pendingTrade();
        closed.setStatus(4);
        when(tradeDao.selectByTradeNoForUpdate("ORDER-1")).thenReturn(closed);
        when(tradeDao.markLateRefunded(201L)).thenReturn(0);
        doReturn(true).when(service).refund(anyString(), anyString(), anyString(), anyString());

        assertEquals("failure", service.handleNotify(validParams()));
        verify(tradeDao).markLateRefunded(201L);
    }

    @Test
    void synchronousReconciliationPersistsAndHonorsLateRefundMarker() throws Exception {
        AlipayServiceImpl service = spy(service(true));
        AlipayTradeQueryResponse response = mock(AlipayTradeQueryResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(response.getTradeStatus()).thenReturn("TRADE_SUCCESS");
        doReturn(response).when(service).executeTradeQuery("ORDER-1");
        DmsShopOrder closed = pendingOrder();
        closed.setStatus(4);
        when(orderDao.selectByOrderNoForUpdate("ORDER-1")).thenReturn(closed);
        when(orderDao.markLateRefunded(101L)).thenReturn(1);
        doReturn(true).when(service).refund(anyString(), anyString(), anyString(), anyString());

        assertEquals(true, service.reconcileOrderFromQuery("ORDER-1"));
        assertEquals(true, service.reconcileOrderFromQuery("ORDER-1"));

        verify(service, times(1)).refund(anyString(), anyString(), anyString(), anyString());
        verify(orderDao, times(1)).markLateRefunded(101L);
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
        params.put("seller_id", "2088123456789012");
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
