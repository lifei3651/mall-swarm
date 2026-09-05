package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopAfterSaleWechatShippingTest {
    @ParameterizedTest
    @CsvSource({"WECHAT,false,1", "WECHAT,true,0", "BALANCE,false,0", "ALIPAY,false,0"})
    void zeroAmountRefundUsesLocalCompletionAndOnlyEnqueuesRealWechatOrder(String payType, boolean simulation, int enqueues) {
        Fixture f = fixture(payType, simulation);
        assertFalse(ReflectionTestUtils.<Boolean>invokeMethod(f.service, "requiresExternalRefund", f.order, f.sale));
        ReflectionTestUtils.invokeMethod(f.service, "completeRefund", f.sale, f.order);
        assertEquals(2, f.order.getStatus());
        verify(f.orderDao).ship(2L, "顺丰速运", "SF-PARTIAL-001");
        verify(f.shipping, times(enqueues)).enqueue(f.order);
        verifyNoInteractions(f.external);
        // 已完成发货状态不因同一个状态校正再次升同步版本。
        ReflectionTestUtils.invokeMethod(f.service, "reconcileOrderStateAfterRefund", f.order, 2);
        verify(f.shipping, times(enqueues)).enqueue(f.order);
    }

    @Test
    void incompleteShipmentAndFailedTransitionNeverEnqueueShipping() {
        Fixture f = fixture("WECHAT", false);
        when(f.shipmentDao.sumQuantityByOrderId(2L)).thenReturn(0);
        ReflectionTestUtils.invokeMethod(f.service, "completeRefund", f.sale, f.order);
        assertEquals(1, f.order.getStatus());
        verifyNoInteractions(f.shipping);
        when(f.shipmentDao.sumQuantityByOrderId(2L)).thenReturn(1);
        when(f.orderDao.ship(2L, "顺丰速运", "SF-PARTIAL-001")).thenReturn(0);
        assertThrows(ApiException.class, () -> ReflectionTestUtils.invokeMethod(f.service, "completeRefund", f.sale, f.order));
        verifyNoInteractions(f.shipping);
    }

    private Fixture fixture(String payType, boolean simulation) {
        DmsShopAfterSaleItemDao saleItems = mock(DmsShopAfterSaleItemDao.class);
        DmsShopOrderDao orderDao = mock(DmsShopOrderDao.class);
        DmsShopOrderItemDao orderItems = mock(DmsShopOrderItemDao.class);
        DmsShopOrderShipmentDao shipmentDao = mock(DmsShopOrderShipmentDao.class);
        ExternalRefundCoordinator external = mock(ExternalRefundCoordinator.class);
        WeChatShippingInfoService shipping = mock(WeChatShippingInfoService.class);
        ShopAfterSaleServiceImpl service = new ShopAfterSaleServiceImpl(mock(DmsAgentDao.class), mock(AgentService.class),
                mock(DmsShopAfterSaleDao.class), saleItems, orderDao, orderItems, shipmentDao,
                mock(DmsShopProductDao.class), mock(DmsShopServiceAddressDao.class), mock(DmsShopSkuDao.class),
                mock(DmsShopMemberDao.class), mock(DistributionAuditService.class), mock(MemberAssetService.class),
                mock(OrderBalanceAllocationService.class), external, mock(ShopAfterSaleWindowPolicy.class),
                mock(ShopAfterSaleTimelinePolicy.class), mock(RefundInventoryRestockService.class),
                mock(ShopMediaStorageService.class), mock(MerchantService.class), mock(OperationLogService.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "weChatShippingInfoService", shipping);
        ReflectionTestUtils.setField(service, "simulationPaymentEnabled", simulation);
        DmsShopAfterSale sale = new DmsShopAfterSale();
        sale.setId(1L); sale.setOrderId(2L); sale.setAfterSaleNo("AS-ZERO"); sale.setRefundAmount(BigDecimal.ZERO);
        sale.setRefundQuantity(1);
        DmsShopOrder order = new DmsShopOrder();
        order.setId(2L); order.setUserId(7L); order.setTenantId(3L); order.setPayType(payType);
        order.setStatus(1); order.setPayAmount(BigDecimal.TEN); order.setPayTime(LocalDateTime.now());
        DmsShopOrderItem item = new DmsShopOrderItem(); item.setId(4L); item.setQuantity(2);
        item.setTotalAmount(BigDecimal.TEN);
        when(orderItems.selectByOrderId(2L)).thenReturn(List.of(item));
        when(saleItems.selectByAfterSaleId(1L)).thenReturn(List.of());
        when(saleItems.sumApprovedQuantityByOrderId(2L)).thenReturn(1);
        DmsShopOrderShipment shipment = new DmsShopOrderShipment();
        shipment.setDeliveryCompany("顺丰速运"); shipment.setDeliveryNo("SF-PARTIAL-001");
        when(shipmentDao.sumQuantityByOrderId(2L)).thenReturn(1);
        when(shipmentDao.selectByOrderId(2L)).thenReturn(List.of(shipment));
        when(orderDao.ship(2L, "顺丰速运", "SF-PARTIAL-001")).thenReturn(1);
        return new Fixture(service, orderDao, shipmentDao, external, shipping, sale, order);
    }
    private record Fixture(ShopAfterSaleServiceImpl service, DmsShopOrderDao orderDao, DmsShopOrderShipmentDao shipmentDao,
            ExternalRefundCoordinator external, WeChatShippingInfoService shipping, DmsShopAfterSale sale, DmsShopOrder order) { }
}
