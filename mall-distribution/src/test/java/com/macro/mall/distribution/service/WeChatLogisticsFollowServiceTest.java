package com.macro.mall.distribution.service;

import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsWechatLogisticsFollowTaskDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsWechatLogisticsFollowTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeChatLogisticsFollowServiceTest {
    @Mock private DmsWechatLogisticsFollowTaskDao taskDao;
    @Mock private DmsShopOrderDao orderDao;
    @Mock private DmsShopOrderShipmentDao shipmentDao;
    @Mock private WeChatLogisticsQueryService queryService;

    private WeChatLogisticsFollowService service;

    @BeforeEach
    void setUp() {
        WeChatMiniProgramProperties mini = new WeChatMiniProgramProperties();
        mini.setEnabled(true);
        mini.setAppId("wx1234567890abcdef");
        mini.setAppSecret("secret-for-test");
        WeChatPayProperties pay = new WeChatPayProperties();
        pay.setEnabled(true);
        pay.setMchId("1900000109");
        pay.setMerchantSerialNumber("ABCDEF1234567890");
        pay.setPrivateKeyPath("/secret/private.pem");
        pay.setPublicKeyId("PUB_KEY_ID_1");
        pay.setPublicKeyPath("/secret/public.pem");
        pay.setApiV3Key("12345678901234567890123456789012");
        pay.setNotifyUrl("https://mall.example.com/api/pay/wechat/notify");
        pay.setRefundNotifyUrl("https://mall.example.com/api/pay/wechat/refund-notify");
        service = new WeChatLogisticsFollowService(mini, pay, taskDao, orderDao, shipmentDao, queryService);
    }

    @Test
    void shipmentCreatesDurableFollowTaskOnlyForPaidWechatOrder() {
        DmsShopOrder order = eligibleOrder();
        DmsShopOrderShipment shipment = shipment();

        service.enqueue(order, shipment);

        verify(taskDao).enqueue(1L, 11L, 66L, 80L);
        order.setPayType("ALIPAY");
        service.enqueue(order, shipment);
        verify(taskDao, org.mockito.Mockito.times(1)).enqueue(1L, 11L, 66L, 80L);
    }

    @Test
    void workerRegistersWaybillAndRecordsSuccessWithoutPersistingToken() {
        when(taskDao.selectDueIds(any(), eq(20))).thenReturn(List.of(9L));
        when(taskDao.claim(eq(9L), anyString(), any(), any())).thenReturn(1);
        DmsWechatLogisticsFollowTask task = task();
        when(taskDao.selectById(9L)).thenReturn(task);
        DmsShopOrder order = eligibleOrder();
        DmsShopOrderShipment shipment = shipment();
        when(orderDao.selectByIdScoped(1L, 11L)).thenReturn(order);
        when(shipmentDao.selectByIdScoped(1L, 11L, 66L)).thenReturn(shipment);
        when(queryService.register(1L, order, shipment)).thenReturn("registered-token");

        service.scheduledRegister();

        verify(queryService).register(1L, order, shipment);
        verify(taskDao).markSuccess(eq(9L), anyString(), anyString(), any());
    }

    @Test
    void closedOrRefundedOrderIsSkippedWithoutCallingWechat() {
        when(taskDao.selectDueIds(any(), eq(20))).thenReturn(List.of(9L));
        when(taskDao.claim(eq(9L), anyString(), any(), any())).thenReturn(1);
        DmsWechatLogisticsFollowTask task = task();
        when(taskDao.selectById(9L)).thenReturn(task);
        DmsShopOrder order = eligibleOrder();
        order.setStatus(4);
        when(orderDao.selectByIdScoped(1L, 11L)).thenReturn(order);

        service.scheduledRegister();

        verify(queryService, never()).register(any(), any(), any());
        verify(taskDao).markTerminal(eq(9L), anyString(), eq("SKIPPED"),
                eq("ORDER_NOT_ELIGIBLE"), any());
    }

    private DmsShopOrder eligibleOrder() {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(11L);
        order.setTenantId(1L);
        order.setUserId(80L);
        order.setPayType("WECHAT");
        order.setPayTime(LocalDateTime.now());
        order.setStatus(2);
        return order;
    }

    private DmsShopOrderShipment shipment() {
        DmsShopOrderShipment shipment = new DmsShopOrderShipment();
        shipment.setId(66L);
        shipment.setTenantId(1L);
        shipment.setOrderId(11L);
        shipment.setDeliveryCompany("圆通速递");
        shipment.setDeliveryNo("YT1234567890");
        return shipment;
    }

    private DmsWechatLogisticsFollowTask task() {
        DmsWechatLogisticsFollowTask task = new DmsWechatLogisticsFollowTask();
        task.setId(9L);
        task.setTenantId(1L);
        task.setOrderId(11L);
        task.setShipmentId(66L);
        task.setUserId(80L);
        task.setStatus("SENDING");
        task.setAttemptCount(1);
        return task;
    }
}
