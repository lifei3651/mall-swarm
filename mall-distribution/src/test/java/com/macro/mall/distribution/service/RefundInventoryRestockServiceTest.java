package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.constants.ShopBusinessType;
import com.macro.mall.distribution.dao.DmsFlashSaleActivityDao;
import com.macro.mall.distribution.dao.DmsFlashSaleReservationDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopSkuDao;
import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import com.macro.mall.distribution.entity.DmsFlashSaleReservation;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import com.macro.mall.distribution.entity.DmsShopOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundInventoryRestockServiceTest {

    @Test
    void returnedGoodsRestoreExactProductAndSkuQuantity() {
        Fixture fixture = new Fixture();
        DmsShopAfterSale afterSale = afterSale(2);
        DmsShopOrder order = order(3);
        DmsShopAfterSaleItem item = item(10L, 11L, 2);
        when(fixture.afterSaleItemDao.selectByAfterSaleId(1L)).thenReturn(List.of(item));
        when(fixture.skuDao.increaseStock(11L, 2)).thenReturn(1);
        when(fixture.productDao.increaseStock(10L, 2)).thenReturn(1);

        assertEquals(2, fixture.service.restoreAfterRefundCompleted(afterSale, order));

        verify(fixture.skuDao).increaseStock(11L, 2);
        verify(fixture.productDao).increaseStock(10L, 2);
        verify(fixture.operationLogService).log(eq("SHOP_PRODUCT_STOCK"), eq("AFTER_SALE_RESTORE"),
                eq("SHOP_AFTER_SALE"), eq("1"), isNull(), eq("restoredQuantity=2"), anyString());
    }

    @Test
    void unshippedRefundRestoresStockWhenNoParcelHasLeftWarehouse() {
        Fixture fixture = new Fixture();
        DmsShopAfterSale afterSale = afterSale(1);
        DmsShopOrder order = order(1);
        DmsShopAfterSaleItem item = item(10L, null, 1);
        when(fixture.orderShipmentDao.sumQuantityByOrderId(2L)).thenReturn(0);
        when(fixture.afterSaleItemDao.selectByAfterSaleId(1L)).thenReturn(List.of(item));
        when(fixture.productDao.increaseStock(10L, 1)).thenReturn(1);

        assertEquals(1, fixture.service.restoreAfterRefundCompleted(afterSale, order));

        verify(fixture.productDao).increaseStock(10L, 1);
        verifyNoInteractions(fixture.skuDao);
    }

    @Test
    void shippedRefundWithoutGoodsReturnNeverCreatesSellableStock() {
        Fixture fixture = new Fixture();
        DmsShopAfterSale afterSale = afterSale(1);
        DmsShopOrder order = order(2);
        order.setDeliveryTime(LocalDateTime.now());

        assertEquals(0, fixture.service.restoreAfterRefundCompleted(afterSale, order));

        verifyNoInteractions(fixture.afterSaleItemDao, fixture.productDao, fixture.skuDao,
                fixture.flashSaleActivityDao, fixture.flashSaleReservationDao,
                fixture.flashSaleStockGate, fixture.operationLogService);
    }

    @Test
    void missingSkuRollsBackInsteadOfSilentlyCompletingPartialRestock() {
        Fixture fixture = new Fixture();
        DmsShopAfterSale afterSale = afterSale(2);
        DmsShopOrder order = order(3);
        when(fixture.afterSaleItemDao.selectByAfterSaleId(1L))
                .thenReturn(List.of(item(10L, 11L, 1)));
        when(fixture.skuDao.increaseStock(11L, 1)).thenReturn(0);

        assertThrows(ApiException.class,
                () -> fixture.service.restoreAfterRefundCompleted(afterSale, order));

        verify(fixture.productDao, never()).increaseStock(10L, 1);
        verifyNoInteractions(fixture.operationLogService);
    }

    @Test
    void flashSaleRefundRestoresReservationActivityAndRuntimeGateTogether() {
        Fixture fixture = new Fixture();
        DmsShopAfterSale afterSale = afterSale(2);
        DmsShopOrder order = order(3);
        order.setBusinessType(ShopBusinessType.FLASH_SALE);
        when(fixture.afterSaleItemDao.selectByAfterSaleId(1L))
                .thenReturn(List.of(item(10L, 11L, 1)));
        when(fixture.skuDao.increaseStock(11L, 1)).thenReturn(1);
        when(fixture.productDao.increaseStock(10L, 1)).thenReturn(1);
        DmsFlashSaleReservation reservation = new DmsFlashSaleReservation();
        reservation.setActivityId(20L);
        reservation.setReleasedQuantity(1);
        when(fixture.flashSaleReservationDao.selectByOrderId(2L)).thenReturn(reservation);
        when(fixture.flashSaleReservationDao.releaseRefundedQuantity(2L, 1)).thenReturn(1);
        when(fixture.flashSaleActivityDao.increaseStock(20L, 1)).thenReturn(1);
        DmsFlashSaleActivity activity = new DmsFlashSaleActivity();
        activity.setId(20L);
        when(fixture.flashSaleActivityDao.selectById(20L)).thenReturn(activity);

        assertEquals(1, fixture.service.restoreAfterRefundCompleted(afterSale, order));

        verify(fixture.flashSaleReservationDao).releaseRefundedQuantity(2L, 1);
        verify(fixture.flashSaleActivityDao).increaseStock(20L, 1);
        verify(fixture.flashSaleStockGate).restoreStockOnly(activity, 1);
    }

    private static DmsShopAfterSale afterSale(int applyType) {
        DmsShopAfterSale afterSale = new DmsShopAfterSale();
        afterSale.setId(1L);
        afterSale.setAfterSaleNo("AS-1");
        afterSale.setApplyType(applyType);
        return afterSale;
    }

    private static DmsShopOrder order(int status) {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(2L);
        order.setOrderNo("ORDER-2");
        order.setStatus(status);
        return order;
    }

    private static DmsShopAfterSaleItem item(Long productId, Long skuId, int quantity) {
        DmsShopAfterSaleItem item = new DmsShopAfterSaleItem();
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setRefundQuantity(quantity);
        return item;
    }

    private static final class Fixture {
        private final DmsShopAfterSaleItemDao afterSaleItemDao = mock(DmsShopAfterSaleItemDao.class);
        private final DmsShopOrderShipmentDao orderShipmentDao = mock(DmsShopOrderShipmentDao.class);
        private final DmsShopProductDao productDao = mock(DmsShopProductDao.class);
        private final DmsShopSkuDao skuDao = mock(DmsShopSkuDao.class);
        private final DmsFlashSaleActivityDao flashSaleActivityDao = mock(DmsFlashSaleActivityDao.class);
        private final DmsFlashSaleReservationDao flashSaleReservationDao = mock(DmsFlashSaleReservationDao.class);
        private final FlashSaleStockGate flashSaleStockGate = mock(FlashSaleStockGate.class);
        private final OperationLogService operationLogService = mock(OperationLogService.class);
        private final RefundInventoryRestockService service = new RefundInventoryRestockService(
                afterSaleItemDao, orderShipmentDao, productDao, skuDao,
                flashSaleActivityDao, flashSaleReservationDao, flashSaleStockGate, operationLogService);
    }
}
