package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 售后退款完成后的唯一库存回补边界。
 *
 * <p>调用方必须把“售后状态由退款中改为已完成”与本方法放在同一数据库事务。
 * 状态原子迁移是幂等门禁：只有首次完成退款的事务才能回补，重复回调不得再调用。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefundInventoryRestockService {

    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopOrderShipmentDao orderShipmentDao;
    private final DmsShopProductDao productDao;
    private final DmsShopSkuDao skuDao;
    private final DmsFlashSaleActivityDao flashSaleActivityDao;
    private final DmsFlashSaleReservationDao flashSaleReservationDao;
    private final FlashSaleStockGate flashSaleStockGate;
    private final OperationLogService operationLogService;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int restoreAfterRefundCompleted(DmsShopAfterSale afterSale, DmsShopOrder order) {
        if (afterSale == null || order == null) Asserts.fail("退款库存回补信息不完整");
        List<DmsShopAfterSaleItem> items = afterSaleItemDao.selectByAfterSaleId(afterSale.getId());
        com.macro.mall.distribution.util.ShopQuantityChecks.refundLines(items);
        if (afterSaleItemDao.countInvalidReservedItemsByOrderId(order.getId()) != 0) {
            Asserts.fail("历史售后数量或商品归属异常，请联系平台核查");
        }
        boolean physicalReturn = Integer.valueOf(2).equals(afterSale.getApplyType());
        boolean beforeAnyShipment = Integer.valueOf(1).equals(order.getStatus())
                && order.getDeliveryTime() == null
                && orderShipmentDao.sumQuantityByOrderId(order.getId()) == 0;
        // 已发货的“仅退款”没有商品退回，不能增加可售库存。
        if (!physicalReturn && !beforeAnyShipment) return 0;

        int restoredQuantity = 0;
        for (DmsShopAfterSaleItem item : items) {
            int quantity = item.getRefundQuantity();
            if (item.getSkuId() != null && skuDao.increaseStock(item.getSkuId(), quantity) != 1) {
                Asserts.fail("退款规格库存回补失败，请使用同一售后单重试");
            }
            if (item.getProductId() == null || productDao.increaseStock(item.getProductId(), quantity) != 1) {
                Asserts.fail("退款商品库存回补失败，请使用同一售后单重试");
            }
            restoredQuantity += quantity;
        }
        if (restoredQuantity <= 0) Asserts.fail("退款库存回补数量不正确");

        releaseFlashSaleAfterRefund(order, restoredQuantity);
        operationLogService.log("SHOP_PRODUCT_STOCK", "AFTER_SALE_RESTORE", "SHOP_AFTER_SALE",
                String.valueOf(afterSale.getId()), null,
                "restoredQuantity=" + restoredQuantity,
                "退款完成后回补可售库存；售后单=" + afterSale.getAfterSaleNo()
                        + "；订单号=" + order.getOrderNo());
        return restoredQuantity;
    }

    private void releaseFlashSaleAfterRefund(DmsShopOrder order, int restoredQuantity) {
        if (!ShopBusinessType.FLASH_SALE.equals(order.getBusinessType())) return;
        DmsFlashSaleReservation reservation = flashSaleReservationDao.selectByOrderId(order.getId());
        if (reservation == null) Asserts.fail("秒杀退款库存回补记录不存在");
        if (flashSaleReservationDao.releaseRefundedQuantity(order.getId(), restoredQuantity) != 1) {
            Asserts.fail("秒杀退款库存回补状态不一致，请使用同一售后单重试");
        }
        if (flashSaleActivityDao.increaseStock(reservation.getActivityId(), restoredQuantity) != 1) {
            Asserts.fail("秒杀活动库存回补失败，请使用同一售后单重试");
        }
        DmsFlashSaleActivity activity = flashSaleActivityDao.selectById(reservation.getActivityId());
        flashSaleStockGate.restoreStockOnly(activity, restoredQuantity);
        DmsFlashSaleReservation updated = flashSaleReservationDao.selectByOrderId(order.getId());
        DmsFlashSaleReservation logged = updated == null ? reservation : updated;
        operationLogService.log("FLASH_SALE_STOCK", "RESTORE", "FLASH_SALE_ACTIVITY",
                String.valueOf(reservation.getActivityId()), null,
                "availableStock=" + (activity == null ? "unknown" : activity.getAvailableStock())
                        + ", releasedQuantity=" + logged.getReleasedQuantity(),
                "秒杀退款完成后回补库存 " + restoredQuantity + " 件；订单号=" + order.getOrderNo());
        log.info("秒杀退款库存已回补: orderId={}, quantity={}", order.getId(), restoredQuantity);
    }
}
