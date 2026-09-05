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

    static ShopAfterSaleItemDTO line(Long id, int quantity) {
        ShopAfterSaleItemDTO item = new ShopAfterSaleItemDTO(); item.setOrderItemId(id); item.setQuantity(quantity); return item;
    }
    static class Fixture {
        final ShopAfterSaleServiceImpl service = mock(ShopAfterSaleServiceImpl.class, CALLS_REAL_METHODS);
        final DmsShopAfterSaleDao saleDao = mock(DmsShopAfterSaleDao.class);
        final DmsShopAfterSaleItemDao saleItemDao = mock(DmsShopAfterSaleItemDao.class);
        final DmsShopMember member = new DmsShopMember();
        Fixture() {
            TenantContext.setTenantId(1L);
            member.setId(1L); member.setUserId(1L);
            DmsShopOrderDao orderDao = mock(DmsShopOrderDao.class);
            DmsShopOrderItemDao itemDao = mock(DmsShopOrderItemDao.class);
            ShopAfterSaleWindowPolicy policy = mock(ShopAfterSaleWindowPolicy.class);
            when(policy.resolve(1L)).thenReturn(new ShopAfterSaleWindowPolicy.Window("RECEIVED", 7));
            DmsShopOrder order = new DmsShopOrder(); order.setId(1L); order.setUserId(1L); order.setTenantId(1L); order.setStatus(1);
            order.setTotalAmount(new BigDecimal("201")); order.setPayAmount(new BigDecimal("201"));
            when(orderDao.selectByIdForUpdate(1L)).thenReturn(order);
            DmsShopOrderItem item = new DmsShopOrderItem(); item.setId(1L); item.setProductId(1L); item.setQuantity(1); item.setTotalAmount(BigDecimal.ONE);
            when(itemDao.selectByOrderId(1L)).thenReturn(List.of(item));
            ReflectionTestUtils.setField(service, "orderDao", orderDao);
            ReflectionTestUtils.setField(service, "orderItemDao", itemDao);
            ReflectionTestUtils.setField(service, "afterSaleDao", saleDao);
            ReflectionTestUtils.setField(service, "afterSaleItemDao", saleItemDao);
            ReflectionTestUtils.setField(service, "afterSaleWindowPolicy", policy);
        }
    }
}
