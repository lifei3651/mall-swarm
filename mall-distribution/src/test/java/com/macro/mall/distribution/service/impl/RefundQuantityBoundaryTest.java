package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.util.ShopQuantityChecks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundQuantityBoundaryTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void oversizedPositiveInputsCannotWrapToNegativeOrSmallPositive() {
        for (List<ShopAfterSaleItemDTO> items : List.of(
                List.of(line(1L, Integer.MAX_VALUE), line(1L, 1)),
                List.of(line(1L, Integer.MAX_VALUE), line(1L, Integer.MAX_VALUE), line(1L, 3)),
                List.of(line(1L, Integer.MAX_VALUE), line(1L, Integer.MAX_VALUE), line(1L, 1), line(2L, 2)))) {
            Fixture fixture = new Fixture();
            ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO(); apply.setOrderId(1L); apply.setItems(items);
            assertThrows(ApiException.class, () -> fixture.service.apply(fixture.member, apply));
            ShopManualRefundDTO manual = new ShopManualRefundDTO(); manual.setItems(items);
            assertThrows(ApiException.class, () -> fixture.service.manualRefund(1L, manual));
            verify(fixture.saleDao, never()).insert(any());
            verifyNoInteractions(fixture.saleItemDao);
        }
    }

    @Test void normalDuplicateSelectionAndLargeHistoricalPositiveQuantityRemainValid() {
        assertEquals(3, ShopQuantityChecks.refundSelection(List.of(line(1L, 1), line(1L, 2))).get(1L));
        assertEquals(Integer.MAX_VALUE, ShopQuantityChecks.refundSelection(List.of(line(1L, Integer.MAX_VALUE))).get(1L));
        assertEquals(2, ShopQuantityChecks.remaining(3, 1));
        assertEquals(0, ShopQuantityChecks.remaining(3, 3));
        assertThrows(ApiException.class, () -> ShopQuantityChecks.remaining(3, -1));
        assertThrows(ApiException.class, () -> ShopQuantityChecks.remaining(3, 4));
    }

    @Test void maximumOrderLinesAndLegalLargeQuantitiesAreNotSilentlyTruncated() {
        ShopOrderSubmitDTO dto = OrderInputBoundaryTest.request(200);
        assertDoesNotThrow(() -> ShopQuantityChecks.order(dto));
        assertEquals(200, dto.getItems().size());
        dto.setItems(List.of(OrderInputBoundaryTest.item(Integer.MAX_VALUE)));
        assertDoesNotThrow(() -> ShopQuantityChecks.order(dto));
        assertEquals(Integer.MAX_VALUE, dto.getItems().get(0).getQuantity());
    }

    @Test void cancelPendingShipmentRechecksStatusUnderTheShipmentOrderLock() {
        Fixture fixture = new Fixture();
        DmsShopOrder stale = new DmsShopOrder(); stale.setId(1L); stale.setTenantId(1L); stale.setStatus(1);
        DmsShopOrder shipped = new DmsShopOrder(); shipped.setId(1L); shipped.setTenantId(1L); shipped.setStatus(2);
        when(fixture.orderDao.selectById(1L)).thenReturn(stale);
        when(fixture.orderDao.selectByIdForUpdate(1L)).thenReturn(shipped);

        assertThrows(ApiException.class, () -> fixture.service.cancelPendingShipment(1L, 7L, "测试管理员"));
        verify(fixture.orderDao).selectByIdForUpdate(1L);
        verify(fixture.orderDao, never()).selectById(1L);
        verifyNoInteractions(fixture.itemDao, fixture.saleDao);
    }

    @Test void cancelPendingShipmentRejectsPartiallyShippedOrderStillMarkedPending() {
        Fixture fixture = new Fixture();
        when(fixture.shipmentDao.sumQuantityByOrderId(1L)).thenReturn(1);

        assertThrows(ApiException.class, () -> fixture.service.cancelPendingShipment(1L, 7L, "测试管理员"));
        verify(fixture.orderDao).selectByIdForUpdate(1L);
        verifyNoInteractions(fixture.itemDao, fixture.saleDao);
    }

    @Test void legacyWaybillDoesNotRefundFreightForLogisticsException() {
        Fixture fixture = new Fixture();
        DmsShopOrder order = fixture.orderDao.selectByIdForUpdate(1L);
        order.setDeliveryNo("YT-LEGACY");
        order.setFreightAmount(new BigDecimal("2.00"));
        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(1L);
        apply.setApplyType(4);
        apply.setReason("物流停滞 / 未收到货");
        apply.setItems(List.of(line(1L, 1)));

        assertThrows(ApiException.class, () -> fixture.service.apply(fixture.member, apply));
        ArgumentCaptor<DmsShopAfterSale> inserted = ArgumentCaptor.forClass(DmsShopAfterSale.class);
        verify(fixture.saleDao).insert(inserted.capture());
        assertEquals(0, inserted.getValue().getFreightRefundAmount().compareTo(BigDecimal.ZERO));
    }

    @Test void zeroQuantityShipmentRecordCannotBeTreatedAsUnshippedCancellation() {
        Fixture fixture = new Fixture();
        when(fixture.shipmentDao.selectByOrderId(1L))
                .thenReturn(List.of(new DmsShopOrderShipment()));
        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(1L);
        apply.setApplyType(4);
        apply.setReason("取消未发货订单");
        apply.setItems(List.of(line(1L, 1)));

        assertThrows(ApiException.class, () -> fixture.service.apply(fixture.member, apply));
        verify(fixture.saleDao, never()).insert(any());
    }


    @Test void eightyNineAlreadyRefundedOnlyOneCentRemainingCanBeRequested() {
        Fixture fixture = new Fixture();
        DmsShopOrder order = fixture.orderDao.selectByIdForUpdate(1L);
        order.setTotalAmount(new BigDecimal("89.01")); order.setPayAmount(new BigDecimal("89.01"));
        DmsShopOrderItem a = new DmsShopOrderItem(); a.setId(11L); a.setProductId(11L); a.setQuantity(1); a.setTotalAmount(new BigDecimal("89.00"));
        DmsShopOrderItem b = new DmsShopOrderItem(); b.setId(12L); b.setProductId(12L); b.setQuantity(1); b.setTotalAmount(new BigDecimal("0.01"));
        when(fixture.itemDao.selectByOrderId(1L)).thenReturn(List.of(a, b));
        when(fixture.saleItemDao.sumReservedQuantityByOrderItemId(11L)).thenReturn(1);
        when(fixture.saleItemDao.sumApprovedProductRefundByOrderId(1L)).thenReturn(new BigDecimal("89.00"));
        when(fixture.saleDao.insert(any())).thenAnswer(invocation -> { ((DmsShopAfterSale) invocation.getArgument(0)).setId(99L); return 1; });
        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO(); apply.setOrderId(1L); apply.setApplyType(4); apply.setReason("取消未发货订单"); apply.setItems(List.of(line(12L, 1)));
        // The minimal mock has no hydrated row; capture the real service insert before that boundary.
        try { fixture.service.apply(fixture.member, apply); } catch (NullPointerException ignored) { }
        ArgumentCaptor<DmsShopAfterSale> sale = ArgumentCaptor.forClass(DmsShopAfterSale.class);
        verify(fixture.saleDao).insert(sale.capture());
        assertEquals(0, new BigDecimal("0.01").compareTo(sale.getValue().getRefundAmount()));
        ArgumentCaptor<List<DmsShopAfterSaleItem>> items = ArgumentCaptor.forClass(List.class);
        verify(fixture.saleItemDao).insertBatch(items.capture());
        assertEquals(1, items.getValue().size());
        DmsShopAfterSaleItem item = items.getValue().get(0);
        assertEquals(12L, item.getOrderItemId()); assertEquals(1, item.getRefundQuantity());
        assertEquals(0, new BigDecimal("0.01").compareTo(item.getRefundAmount()));
        clearInvocations(fixture.saleDao, fixture.saleItemDao);
        apply.setItems(List.of(line(11L, 1)));
        assertThrows(ApiException.class, () -> fixture.service.apply(fixture.member, apply));
        verify(fixture.saleDao, never()).insert(any()); verify(fixture.saleItemDao, never()).insertBatch(any());
    }

    @Test void partialRefundKeepsRemainingShipmentAndOnlyAllRefundsCloseOrder() {
        Fixture fixture = new Fixture(); DmsShopOrder order = fixture.orderDao.selectByIdForUpdate(1L);
        when(fixture.saleItemDao.sumCompletedQuantityByOrderId(1L)).thenReturn(1);
        ReflectionTestUtils.invokeMethod(fixture.service, "reconcileOrderStateAfterRefund", order, 2);
        assertEquals(1, order.getStatus()); verify(fixture.orderDao, never()).closeAfterSale(anyLong());
        when(fixture.saleItemDao.sumCompletedQuantityByOrderId(1L)).thenReturn(2);
        when(fixture.orderDao.closeAfterSale(1L)).thenReturn(1);
        ReflectionTestUtils.invokeMethod(fixture.service, "reconcileOrderStateAfterRefund", order, 2);
        assertEquals(4, order.getStatus()); verify(fixture.orderDao).closeAfterSale(1L);
    }

    static ShopAfterSaleItemDTO line(Long id, int quantity) {
        ShopAfterSaleItemDTO item = new ShopAfterSaleItemDTO(); item.setOrderItemId(id); item.setQuantity(quantity); return item;
    }
    static class Fixture {
        final ShopAfterSaleServiceImpl service = mock(ShopAfterSaleServiceImpl.class, CALLS_REAL_METHODS);
        final DmsShopAfterSaleDao saleDao = mock(DmsShopAfterSaleDao.class);
        final DmsShopAfterSaleItemDao saleItemDao = mock(DmsShopAfterSaleItemDao.class);
        final DmsShopOrderDao orderDao = mock(DmsShopOrderDao.class);
        final DmsShopOrderItemDao itemDao = mock(DmsShopOrderItemDao.class);
        final DmsShopOrderShipmentDao shipmentDao = mock(DmsShopOrderShipmentDao.class);
        final DmsShopMember member = new DmsShopMember();
        Fixture() {
            TenantContext.setTenantId(1L);
            member.setId(1L); member.setUserId(1L);
            ShopAfterSaleWindowPolicy policy = mock(ShopAfterSaleWindowPolicy.class);
            when(policy.resolve(1L)).thenReturn(new ShopAfterSaleWindowPolicy.Window("RECEIVED", 7));
            DmsShopOrder order = new DmsShopOrder(); order.setId(1L); order.setUserId(1L); order.setTenantId(1L); order.setStatus(1);
            order.setTotalAmount(new BigDecimal("201")); order.setPayAmount(new BigDecimal("201"));
            when(orderDao.selectByIdForUpdate(1L)).thenReturn(order);
            DmsShopOrderItem item = new DmsShopOrderItem(); item.setId(1L); item.setProductId(1L); item.setQuantity(1); item.setTotalAmount(BigDecimal.ONE);
            when(itemDao.selectByOrderId(1L)).thenReturn(List.of(item));
            ReflectionTestUtils.setField(service, "orderDao", orderDao);
            ReflectionTestUtils.setField(service, "orderItemDao", itemDao);
            ReflectionTestUtils.setField(service, "orderShipmentDao", shipmentDao);
            ReflectionTestUtils.setField(service, "afterSaleDao", saleDao);
            ReflectionTestUtils.setField(service, "afterSaleItemDao", saleItemDao);
            ReflectionTestUtils.setField(service, "afterSaleWindowPolicy", policy);
        }
    }
}
