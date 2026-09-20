package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WeChatExpressCancellationStateServiceTest {

    private final DmsShopOrderDao orderDao = mock(DmsShopOrderDao.class);
    private final DmsShopOrderItemDao itemDao = mock(DmsShopOrderItemDao.class);
    private final DmsShopAfterSaleDao afterSaleDao = mock(DmsShopAfterSaleDao.class);
    private final DmsShopAfterSaleItemDao afterSaleItemDao = mock(DmsShopAfterSaleItemDao.class);
    private final DmsShopOrderShipmentDao shipmentDao = mock(DmsShopOrderShipmentDao.class);
    private final DmsWechatExpressOrderDao expressDao = mock(DmsWechatExpressOrderDao.class);
    private final DmsWechatShippingSyncTaskDao shippingTaskDao = mock(DmsWechatShippingSyncTaskDao.class);
    private final DmsWechatLogisticsFollowTaskDao followTaskDao = mock(DmsWechatLogisticsFollowTaskDao.class);
    private final DmsMerchantDao merchantDao = mock(DmsMerchantDao.class);
    private final OperationLogService operationLog = mock(OperationLogService.class);
    private final WeChatShippingInfoService shippingInfo = mock(WeChatShippingInfoService.class);
    private final OrderRealtimeService realtime = mock(OrderRealtimeService.class);
    private final WeChatExpressCancellationStateService service = new WeChatExpressCancellationStateService(
            orderDao, itemDao, afterSaleDao, afterSaleItemDao, shipmentDao, expressDao, shippingTaskDao,
            followTaskDao, merchantDao, operationLog, shippingInfo, realtime);

    @AfterEach
    void clear() {
        TenantContext.clear();
        com.macro.mall.distribution.security.AdminContext.clear();
    }

    @Test
    void refusesRollbackAfterLatestShipmentWasAlreadySyncedToWechatTransactionComponent() {
        TenantContext.setTenantId(7L);
        DmsShopOrder order = order(2);
        DmsWechatExpressOrder express = express("SUCCESS");
        DmsShopOrderShipment shipment = shipment();
        DmsWechatShippingSyncTask task = new DmsWechatShippingSyncTask();
        task.setStatus("SUCCESS"); task.setRevision(2); task.setSyncedRevision(2);
        when(orderDao.selectByIdForUpdate(99L)).thenReturn(order);
        when(expressDao.selectByShipmentIdForUpdate(7L, 99L, 77L)).thenReturn(express);
        when(shipmentDao.selectByIdScoped(7L, 99L, 77L)).thenReturn(shipment);
        when(shippingTaskDao.selectByPaymentOrderNo(7L, "PAY-99")).thenReturn(task);

        assertThatThrownBy(() -> service.prepare(99L, 77L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已同步到微信交易组件");
        verify(expressDao, never()).markCancelling(anyLong(), anyLong());
    }

    @Test
    void refusesDuplicateCancellationWhileWechatCallIsStillInFlight() {
        TenantContext.setTenantId(7L);
        DmsWechatExpressOrder express = express("CANCELLING");
        express.setUpdateTime(LocalDateTime.now());
        when(orderDao.selectByIdForUpdate(99L)).thenReturn(order(2));
        when(expressDao.selectByShipmentIdForUpdate(7L, 99L, 77L)).thenReturn(express);

        assertThatThrownBy(() -> service.prepare(99L, 77L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("正在撤销");

        verify(shipmentDao, never()).selectByIdScoped(anyLong(), anyLong(), anyLong());
    }

    @Test
    void refusesRollbackAfterLogisticsMessageWasRegistered() {
        TenantContext.setTenantId(7L);
        when(orderDao.selectByIdForUpdate(99L)).thenReturn(order(2));
        when(expressDao.selectByShipmentIdForUpdate(7L, 99L, 77L)).thenReturn(express("SUCCESS"));
        when(shipmentDao.selectByIdScoped(7L, 99L, 77L)).thenReturn(shipment());
        DmsWechatLogisticsFollowTask task = new DmsWechatLogisticsFollowTask();
        task.setStatus("SUCCESS");
        when(followTaskDao.selectByShipment(7L, 99L, 77L)).thenReturn(task);

        assertThatThrownBy(() -> service.prepare(99L, 77L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已登记微信物流消息");

        verify(expressDao, never()).markCancelling(anyLong(), anyLong());
    }

    @Test
    void removesLocalParcelAndRestoresPendingShipmentAfterWechatConfirmedCancellation() {
        TenantContext.setTenantId(7L);
        DmsShopOrder order = order(2);
        DmsWechatExpressOrder express = express("CANCEL_CONFIRMED");
        when(orderDao.selectByIdForUpdate(99L)).thenReturn(order);
        when(expressDao.selectByShipmentIdForUpdate(7L, 99L, 77L)).thenReturn(express);
        when(shipmentDao.deleteScoped(7L, 99L, 77L)).thenReturn(1);
        when(shipmentDao.selectByOrderId(99L)).thenReturn(List.of());
        when(itemDao.sumQuantityByOrderId(99L)).thenReturn(2);
        when(afterSaleItemDao.sumApprovedQuantityByOrderId(99L)).thenReturn(0);
        when(orderDao.reconcileShipmentState(99L, 1, null, null, null)).thenReturn(1);
        when(expressDao.markCancelled(7L, 88L)).thenReturn(1);

        service.finish(new WeChatExpressCancellationStateService.CancellationContext(
                7L, 99L, 77L, 88L, 12L, "PAY-99", "LQ-99", "YTO", "YT123", false, true));

        verify(followTaskDao).markShipmentCancelled(7L, 99L, 77L);
        verify(shippingTaskDao).markLocalShipmentCancelled(7L, "PAY-99", "LOCAL_WAYBILL_CANCELLED");
        verify(expressDao).markCancelled(7L, 88L);
        verify(realtime).orderChanged(order, "ORDER_SHIPMENT_CANCELLED");
    }

    private DmsShopOrder order(int status) {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(99L); order.setTenantId(7L); order.setOrderNo("ORDER-99");
        order.setPaymentOrderNo("PAY-99"); order.setUserId(12L); order.setStatus(status);
        order.setPayType("WECHAT");
        return order;
    }

    private DmsWechatExpressOrder express(String status) {
        DmsWechatExpressOrder value = new DmsWechatExpressOrder();
        value.setId(88L); value.setTenantId(7L); value.setOrderId(99L); value.setUserId(12L);
        value.setShipmentId(77L); value.setStatus(status); value.setExpressOrderNo("LQ-99");
        value.setDeliveryId("YTO"); value.setWaybillId("YT123");
        return value;
    }

    private DmsShopOrderShipment shipment() {
        DmsShopOrderShipment value = new DmsShopOrderShipment();
        value.setId(77L); value.setTenantId(7L); value.setOrderId(99L);
        value.setSource("WECHAT_EXPRESS"); value.setShipmentQuantity(2);
        return value;
    }
}
