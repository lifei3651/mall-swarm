package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopTrade;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.WeChatPayService;
import com.macro.mall.distribution.vo.WeChatPayParametersVO;
import com.macro.mall.distribution.wechat.WeChatPayGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

class WeChatPayServiceImplTest {

    private WeChatPayGateway gateway;
    private DmsShopOrderDao orderDao;
    private DmsShopTradeDao tradeDao;
    private DmsShopMemberDao memberDao;
    private DmsWechatMiniProgramIdentityDao identityDao;
    private DmsTenantDao tenantDao;
    private ShopService shopService;
    private PlatformTransactionManager transactionManager;
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
        tenantDao = mock(DmsTenantDao.class);
        shopService = mock(ShopService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        DmsTenant tenant = new DmsTenant();
        tenant.setId(1L);
        when(tenantDao.selectAll()).thenReturn(java.util.List.of(tenant));
        service = new WeChatPayServiceImpl(pay, mini, gateway, orderDao, tradeDao, memberDao, identityDao, tenantDao,
                shopService, transactionManager);
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
    void anonymousPaymentOperationsUseUnauthorizedSemanticCode() {
        ApiException prepay = assertThrows(ApiException.class, () -> service.createPayOrder(9L, null));
        ApiException reconcile = assertThrows(ApiException.class, () -> service.reconcileOrder(9L, null));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), prepay.getErrorCode().getCode());
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), reconcile.getErrorCode().getCode());
        verifyNoInteractions(gateway, orderDao, tradeDao, memberDao, identityDao, shopService);
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
    void signedNotificationForAnotherSelectedChannelNeverMarksOrderPaid() {
        order.setPayType("ALIPAY");
        when(gateway.parsePaymentNotification(any())).thenReturn(paymentResult(1990));
        when(tradeDao.selectByTradeNoForUpdate("L202608300001")).thenReturn(null);
        when(orderDao.selectByOrderNoForUpdate("L202608300001")).thenReturn(order);

        assertThrows(ApiException.class, () -> service.handlePaymentNotification(notification()));
        verify(shopService, never()).markOrderPaid(any(), any());
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
        when(orderDao.markLateRefundProcessing(9L)).thenReturn(1);
        when(orderDao.markLateRefunded(9L)).thenReturn(1);

        service.closeOrder("L202608300001");

        verify(gateway).query("L202608300001");
        verify(orderDao).markLateRefundProcessing(9L);
        verify(orderDao).markLateRefunded(9L);
        verify(shopService, never()).markOrderPaid(any(), any());
    }

    @Test
    void latePaymentPersistsProcessingBeforeRequestAndKeepsItWhileChannelProcesses() {
        order.setStatus(4);
        order.setPayTime(null);
        when(gateway.parsePaymentNotification(any())).thenReturn(paymentResult(1990));
        when(tradeDao.selectByTradeNoForUpdate("L202608300001")).thenReturn(null);
        when(orderDao.selectByOrderNoForUpdate("L202608300001")).thenReturn(order);
        when(memberDao.selectByUserId(8L)).thenReturn(member);
        when(orderDao.markLateRefundProcessing(9L)).thenReturn(1);
        when(gateway.refund(any())).thenReturn(new WeChatPayGateway.RefundResult(
                "PROCESSING", "L202608300001", lateRefundNo(), 1990L, 1990L, "CNY"));

        service.handlePaymentNotification(notification());

        InOrder persistenceBeforeChannel = inOrder(orderDao, gateway);
        persistenceBeforeChannel.verify(orderDao).markLateRefundProcessing(9L);
        persistenceBeforeChannel.verify(gateway).refund(any());
        verify(orderDao, never()).markLateRefunded(9L);
    }

    @Test
    void channelRefundStartsOnlyAfterProcessingMarkerTransactionCommits() {
        order.setStatus(4);
        order.setPayTime(null);
        when(gateway.parsePaymentNotification(any())).thenReturn(paymentResult(1990));
        when(tradeDao.selectByTradeNoForUpdate("L202608300001")).thenReturn(null);
        when(orderDao.selectByOrderNoForUpdate("L202608300001")).thenReturn(order);
        when(memberDao.selectByUserId(8L)).thenReturn(member);
        when(orderDao.markLateRefundProcessing(9L)).thenReturn(1);
        when(gateway.refund(any())).thenReturn(new WeChatPayGateway.RefundResult(
                "PROCESSING", "L202608300001", lateRefundNo(), 1990L, 1990L, "CNY"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.handlePaymentNotification(notification());
            verify(orderDao).markLateRefundProcessing(9L);
            verify(gateway, never()).refund(any());

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(gateway).refund(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void repeatedNotificationForPersistedLateRefundDoesNotIssueAnotherChannelRequest() {
        order.setStatus(4);
        order.setPayTime(null);
        order.setLateRefundFlag(2);
        when(gateway.parsePaymentNotification(any())).thenReturn(paymentResult(1990));
        when(tradeDao.selectByTradeNoForUpdate("L202608300001")).thenReturn(null);
        when(orderDao.selectByOrderNoForUpdate("L202608300001")).thenReturn(order);
        when(memberDao.selectByUserId(8L)).thenReturn(member);

        service.handlePaymentNotification(notification());
        service.handlePaymentNotification(notification());

        verify(gateway, never()).refund(any());
        verify(orderDao, never()).markLateRefundProcessing(any());
        verify(shopService, never()).markOrderPaid(any(), any());
    }

    @Test
    void scheduledRecoveryUsesStableRefundNumberAndCompletesLostCallback() {
        order.setStatus(4);
        order.setPayTime(null);
        order.setLateRefundFlag(2);
        when(tradeDao.selectLateRefundProcessingIds(50)).thenReturn(java.util.List.of());
        when(orderDao.selectLateRefundProcessingIds(50)).thenReturn(java.util.List.of(9L));
        when(orderDao.selectById(9L)).thenReturn(order);
        when(gateway.refund(any())).thenAnswer(invocation -> {
            WeChatPayGateway.RefundCommand command = invocation.getArgument(0);
            return new WeChatPayGateway.RefundResult("SUCCESS", command.paymentNo(), command.refundNo(),
                    command.refundFen(), command.totalFen(), "CNY");
        });
        when(orderDao.markLateRefunded(9L)).thenReturn(1);

        assertEquals(1, service.reconcileProcessingLatePaymentRefunds(50));

        ArgumentCaptor<WeChatPayGateway.RefundCommand> command =
                ArgumentCaptor.forClass(WeChatPayGateway.RefundCommand.class);
        verify(gateway).refund(command.capture());
        assertEquals(lateRefundNo(), command.getValue().refundNo());
        verify(orderDao).markLateRefunded(9L);
    }

    @Test
    void scheduledRecoveryLeavesFailedRequestInProcessingForNextRetry() {
        DmsShopTrade trade = new DmsShopTrade();
        trade.setId(12L);
        trade.setTradeNo("T202608300001");
        trade.setUserId(8L);
        trade.setPayType("WECHAT");
        trade.setPayAmount(new BigDecimal("19.90"));
        trade.setStatus(4);
        trade.setLateRefundFlag(2);
        when(tradeDao.selectLateRefundProcessingIds(50)).thenReturn(java.util.List.of(12L));
        when(tradeDao.selectById(12L)).thenReturn(trade);
        when(orderDao.selectLateRefundProcessingIds(49)).thenReturn(java.util.List.of());
        when(gateway.refund(any())).thenReturn(new WeChatPayGateway.RefundResult(
                "ABNORMAL", "T202608300001",
                "LATEPAY-" + cn.hutool.crypto.SecureUtil.sha256("T202608300001").substring(0, 32),
                1990L, 1990L, "CNY"));

        assertEquals(0, service.reconcileProcessingLatePaymentRefunds(50));

        verify(tradeDao, never()).markLateRefunded(12L);
        verify(gateway, times(1)).refund(any());
    }

    @Test
    void scheduledRecoveryScansEveryTenantAndRestoresPreviousContext() {
        DmsTenant first = new DmsTenant();
        first.setId(1L);
        DmsTenant second = new DmsTenant();
        second.setId(2L);
        when(tenantDao.selectAll()).thenReturn(java.util.List.of(first, second));
        java.util.List<Long> tradeScans = new java.util.ArrayList<>();
        java.util.List<Long> orderScans = new java.util.ArrayList<>();
        when(tradeDao.selectLateRefundProcessingIds(50)).thenAnswer(invocation -> {
            tradeScans.add(TenantContext.getTenantId());
            return java.util.List.of();
        });
        when(orderDao.selectLateRefundProcessingIds(50)).thenAnswer(invocation -> {
            Long tenantId = TenantContext.getTenantId();
            orderScans.add(tenantId);
            return Long.valueOf(2L).equals(tenantId) ? java.util.List.of(9L) : java.util.List.of();
        });
        order.setStatus(4);
        order.setPayTime(null);
        order.setLateRefundFlag(2);
        when(orderDao.selectById(9L)).thenReturn(order);
        when(gateway.refund(any())).thenAnswer(invocation -> {
            WeChatPayGateway.RefundCommand command = invocation.getArgument(0);
            return new WeChatPayGateway.RefundResult("SUCCESS", command.paymentNo(), command.refundNo(),
                    command.refundFen(), command.totalFen(), "CNY");
        });
        when(orderDao.markLateRefunded(9L)).thenAnswer(invocation -> {
            assertEquals(2L, TenantContext.getTenantId());
            return 1;
        });
        TenantContext.setTenantId(77L);
        try {
            assertEquals(1, service.reconcileProcessingLatePaymentRefunds(50));
            assertEquals(java.util.List.of(1L, 2L), tradeScans);
            assertEquals(java.util.List.of(1L, 2L), orderScans);
            assertEquals(77L, TenantContext.getTenantId());
        } finally {
            TenantContext.clear();
        }
    }

    private String lateRefundNo() {
        return "LATEPAY-" + cn.hutool.crypto.SecureUtil.sha256("L202608300001").substring(0, 32);
    }

    private WeChatPayGateway.PaymentResult paymentResult(int totalFen) {
        return new WeChatPayGateway.PaymentResult("SUCCESS", "wx1234567890abcdef", "1900000001",
                "L202608300001", totalFen, "CNY", "openid-user-8", "4200000000000000000000000000");
    }

    private WeChatPayGateway.NotificationRequest notification() {
        return new WeChatPayGateway.NotificationRequest("PUB_KEY_ID", "signature", "1788060000",
                "nonce", "WECHATPAY2-SHA256-RSA2048", "{}");
    }
}
