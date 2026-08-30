package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.WeChatPayService;
import com.macro.mall.distribution.vo.WeChatPayParametersVO;
import com.macro.mall.distribution.wechat.WeChatPayGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class WeChatPayServiceImplTest {

    private WeChatPayGateway gateway;
    private DmsShopOrderDao orderDao;
    private DmsShopTradeDao tradeDao;
    private DmsShopMemberDao memberDao;
    private DmsWechatMiniProgramIdentityDao identityDao;
    private ShopService shopService;
    private WeChatPayServiceImpl service;
    private DmsShopMember member;
    private DmsShopOrder order;

    @BeforeEach
    void setUp() {
        WeChatPayProperties pay = new WeChatPayProperties();
        pay.setEnabled(true);
        pay.setMchId("1900000001");
        pay.setMerchantSerialNumber("ABCDEF0123456789");
        pay.setPrivateKeyPath("/secure/apiclient_key.pem");
        pay.setPublicKeyId("PUB_KEY_ID_ABCDEF");
        pay.setPublicKeyPath("/secure/wechatpay_public_key.pem");
        pay.setApiV3Key("12345678901234567890123456789012");
        pay.setNotifyUrl("https://mall.example.com/api/pay/wechat/notify");
        pay.setRefundNotifyUrl("https://mall.example.com/api/pay/wechat/refund-notify");
        WeChatMiniProgramProperties mini = new WeChatMiniProgramProperties();
        mini.setEnabled(true);
        mini.setAppId("wx1234567890abcdef");
        mini.setAppSecret("strong-app-secret");
        gateway = mock(WeChatPayGateway.class);
        orderDao = mock(DmsShopOrderDao.class);
        tradeDao = mock(DmsShopTradeDao.class);
        memberDao = mock(DmsShopMemberDao.class);
        identityDao = mock(DmsWechatMiniProgramIdentityDao.class);
        shopService = mock(ShopService.class);
        service = new WeChatPayServiceImpl(pay, mini, gateway, orderDao, tradeDao, memberDao, identityDao, shopService);
        member = new DmsShopMember();
        member.setId(7L);
        member.setUserId(8L);
        order = new DmsShopOrder();
        order.setId(9L);
        order.setOrderNo("L202608300001");
        order.setPaymentOrderNo("L202608300001");
        order.setUserId(8L);
        order.setPayType("WECHAT");
        order.setPayAmount(new BigDecimal("19.90"));
        order.setStatus(0);
        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setMemberId(7L);
        identity.setUserId(8L);
        identity.setOpenId("openid-user-8");
        when(identityDao.selectByMember(any(), any(), any())).thenReturn(identity);
    }

    @Test
    void prepayUsesServerAmountAndBoundOpenId() {
        when(orderDao.selectById(9L)).thenReturn(order);
        when(gateway.prepay(any())).thenAnswer(invocation -> {
            WeChatPayGateway.PrepayCommand command = invocation.getArgument(0);
            assertEquals(1990, command.totalFen());
            assertEquals("openid-user-8", command.openId());
            return new WeChatPayGateway.PrepayResult("wx1234567890abcdef", "1788060000", "nonce",
                    "prepay_id=wx123", "RSA", "signature");
        });

        WeChatPayParametersVO result = service.createPayOrder(9L, member);

        assertEquals("prepay_id=wx123", result.getPackageValue());
        assertEquals("L202608300001", result.getPaymentNo());
    }

    @Test
    void verifiedNotificationMarksOrderPaidOnlyAfterAllFieldsMatch() {
        when(gateway.parsePaymentNotification(any())).thenReturn(paymentResult(1990));
        when(tradeDao.selectByTradeNoForUpdate("L202608300001")).thenReturn(null);
        when(orderDao.selectByOrderNoForUpdate("L202608300001")).thenReturn(order);
        when(memberDao.selectByUserId(8L)).thenReturn(member);

        service.handlePaymentNotification(notification());

        verify(shopService).markOrderPaid(9L, "WECHAT");
    }

    @Test
    void amountMismatchNeverMarksOrderPaid() {
        when(gateway.parsePaymentNotification(any())).thenReturn(paymentResult(1));
        when(tradeDao.selectByTradeNoForUpdate("L202608300001")).thenReturn(null);
        when(orderDao.selectByOrderNoForUpdate("L202608300001")).thenReturn(order);

        assertThrows(ApiException.class, () -> service.handlePaymentNotification(notification()));
        verify(shopService, never()).markOrderPaid(any(), any());
    }

    @Test
    void asynchronousRefundStaysProcessingUntilSignedCallback() {
        when(gateway.refund(any())).thenReturn(new WeChatPayGateway.RefundResult(
                "PROCESSING", "L202608300001", "AS-100", 500L, 1990L, "CNY"));

        WeChatPayService.RefundState state = service.requestRefund("L202608300001", "AS-100",
                new BigDecimal("5.00"), new BigDecimal("19.90"), "售后退款");

        assertEquals(WeChatPayService.RefundState.PROCESSING, state);
    }

    @Test
    void paidRaceDuringCloseIsDetectedAndRefundedIdempotently() {
        order.setStatus(4);
        order.setPayTime(null);
        when(tradeDao.selectByTradeNoForUpdate("L202608300001")).thenReturn(null);
        when(orderDao.selectByOrderNoForUpdate("L202608300001")).thenReturn(order);
        when(memberDao.selectByUserId(8L)).thenReturn(member);
        doThrow(new IllegalStateException("ORDERPAID")).when(gateway).close("L202608300001");
        when(gateway.query("L202608300001")).thenReturn(paymentResult(1990));
        when(gateway.refund(any())).thenAnswer(invocation -> {
            WeChatPayGateway.RefundCommand command = invocation.getArgument(0);
            return new WeChatPayGateway.RefundResult("SUCCESS", command.paymentNo(), command.refundNo(),
                    command.refundFen(), command.totalFen(), "CNY");
        });
        when(orderDao.markLateRefunded(9L)).thenReturn(1);

        service.closeOrder("L202608300001");

        verify(gateway).query("L202608300001");
        verify(orderDao).markLateRefunded(9L);
        verify(shopService, never()).markOrderPaid(any(), any());
    }

    private WeChatPayGateway.PaymentResult paymentResult(int totalFen) {
        return new WeChatPayGateway.PaymentResult("SUCCESS", "wx1234567890abcdef", "1900000001",
                "L202608300001", totalFen, "CNY", "openid-user-8");
    }

    private WeChatPayGateway.NotificationRequest notification() {
        return new WeChatPayGateway.NotificationRequest("PUB_KEY_ID", "signature", "1788060000",
                "nonce", "WECHATPAY2-SHA256-RSA2048", "{}");
    }
}
