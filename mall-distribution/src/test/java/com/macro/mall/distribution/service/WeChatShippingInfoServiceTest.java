package com.macro.mall.distribution.service;

import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.dao.DmsWechatShippingSyncTaskDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.entity.DmsWechatShippingSyncTask;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeChatShippingInfoServiceTest {
    @Mock private WeChatMiniProgramGateway gateway;
    @Mock private DmsWechatShippingSyncTaskDao taskDao;
    @Mock private DmsShopOrderDao orderDao;
    @Mock private DmsShopOrderShipmentDao shipmentDao;
    @Mock private DmsShopOrderItemDao itemDao;
    @Mock private DmsWechatMiniProgramIdentityDao identityDao;
    private WeChatShippingInfoService service;

    @BeforeEach
    void setUp() {
        WeChatMiniProgramProperties mini = new WeChatMiniProgramProperties();
        mini.setEnabled(true);
        mini.setShippingInfoEnabled(true);
        mini.setAppId("wx1234567890abcdef");
        mini.setAppSecret("customer-secret-value");
        WeChatPayProperties pay = configuredPay();
        service = new WeChatShippingInfoService(mini, pay, gateway, taskDao, orderDao, shipmentDao, itemDao, identityDao);
    }

    @Test
    void enqueueOnlyAcceptsPaidWechatOrders() {
        DmsShopOrder order = paidOrder();
        service.enqueue(order);
        verify(taskDao).enqueue(1L, "PAY10001", 80L);

        order.setPayType("ALIPAY");
        service.enqueue(order);
        verify(taskDao, times(1)).enqueue(1L, "PAY10001", 80L);
    }

    @Test
    void workerUploadsMaskedDeliveryInformationAndMarksRevisionSuccess() {
        when(taskDao.selectDueIds(any(), org.mockito.ArgumentMatchers.eq(20))).thenReturn(List.of(9L));
        when(taskDao.claim(org.mockito.ArgumentMatchers.eq(9L), anyString(), any(), any())).thenReturn(1);
        DmsWechatShippingSyncTask task = new DmsWechatShippingSyncTask();
        task.setId(9L); task.setTenantId(1L); task.setUserId(80L); task.setPaymentOrderNo("PAY10001");
        task.setStatus("SENDING"); task.setRevision(1); task.setAttemptCount(1);
        when(taskDao.selectById(9L)).thenReturn(task);
        DmsShopOrder order = paidOrder();
        order.setId(11L); order.setStatus(2); order.setReceiverPhone("13800138000");
        when(orderDao.selectByPaymentOrderNoScoped(1L, "PAY10001")).thenReturn(List.of(order));
        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setOpenId("openid-secret");
        when(identityDao.selectByUser(org.mockito.ArgumentMatchers.eq(1L), anyString(), org.mockito.ArgumentMatchers.eq(80L))).thenReturn(identity);
        DmsShopOrderShipment shipment = new DmsShopOrderShipment();
        shipment.setDeliveryCompany("顺丰速运"); shipment.setDeliveryNo("SF10000001");
        when(shipmentDao.selectByOrderId(11L)).thenReturn(List.of(shipment));
        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setProductName("测试商品"); item.setQuantity(2);
        when(itemDao.selectByOrderId(11L)).thenReturn(List.of(item));
        when(gateway.deliveryCompanies()).thenReturn(List.of(new WeChatMiniProgramGateway.DeliveryCompany("SF", "顺丰速运")));
        when(gateway.uploadShippingInfo(any())).thenReturn(new WeChatMiniProgramGateway.ShippingInfoResult(0));

        service.scheduledSync();

        ArgumentCaptor<WeChatMiniProgramGateway.ShippingInfoCommand> command =
                ArgumentCaptor.forClass(WeChatMiniProgramGateway.ShippingInfoCommand.class);
        verify(gateway).uploadShippingInfo(command.capture());
        assertEquals("138****8000", command.getValue().shipments().get(0).receiverContact());
        assertEquals("SF", command.getValue().shipments().get(0).expressCompany());
        assertTrue(command.getValue().allDelivered());
        verify(taskDao).markSuccess(org.mockito.ArgumentMatchers.eq(9L), anyString(),
                org.mockito.ArgumentMatchers.eq(1), anyString(), any());
    }

    private DmsShopOrder paidOrder() {
        DmsShopOrder order = new DmsShopOrder();
        order.setTenantId(1L); order.setUserId(80L); order.setPayType("WECHAT");
        order.setPayTime(LocalDateTime.now()); order.setPaymentOrderNo("PAY10001");
        return order;
    }

    private WeChatPayProperties configuredPay() {
        WeChatPayProperties pay = new WeChatPayProperties();
        pay.setEnabled(true); pay.setMchId("1900000109"); pay.setMerchantSerialNumber("ABCDEF1234567890");
        pay.setPrivateKeyPath("/secret/private.pem"); pay.setPublicKeyId("PUB_KEY_ID_1");
        pay.setPublicKeyPath("/secret/public.pem"); pay.setApiV3Key("12345678901234567890123456789012");
        pay.setNotifyUrl("https://mall.example.com/api/pay/wechat/notify");
        pay.setRefundNotifyUrl("https://mall.example.com/api/pay/wechat/refund-notify");
        return pay;
    }
}
