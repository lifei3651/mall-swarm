package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsWechatExpressOrderDao;
import com.macro.mall.distribution.dao.DmsWechatLogisticsFollowTaskDao;
import com.macro.mall.distribution.dao.DmsWechatShippingSyncTaskDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsWechatExpressOrder;
import com.macro.mall.distribution.entity.DmsWechatLogisticsFollowTask;
import com.macro.mall.distribution.entity.DmsWechatShippingSyncTask;
import com.macro.mall.distribution.security.AdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 微信运单撤销的本地状态机。外部微信调用由编排服务执行，事务边界分别保存“准备、微信已确认、完成回滚”，
 * 即使最终本地提交暂时失败，也可以从 CANCEL_CONFIRMED 状态安全重试而不会重复生成或遗留商城包裹。
 */
@Service
@RequiredArgsConstructor
public class WeChatExpressCancellationStateService {
    private final DmsShopOrderDao orderDao;
    private final DmsShopOrderItemDao itemDao;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopOrderShipmentDao shipmentDao;
    private final DmsWechatExpressOrderDao expressOrderDao;
    private final DmsWechatShippingSyncTaskDao shippingTaskDao;
    private final DmsWechatLogisticsFollowTaskDao followTaskDao;
    private final DmsMerchantDao merchantDao;
    private final OperationLogService operationLogService;
    private final WeChatShippingInfoService shippingInfoService;
    private final OrderRealtimeService orderRealtimeService;

    @Transactional(rollbackFor = Exception.class)
    public CancellationContext prepare(Long orderId, Long shipmentId) {
        if (orderId == null || shipmentId == null) Asserts.fail("撤销运单参数不完整");
        Long tenantId = TenantContext.getTenantId();
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null) Asserts.fail("订单不存在");
        assertOrderAccess(order);
        if (order.getStatus() == null || !Set.of(1, 2).contains(order.getStatus())) {
            Asserts.fail("只有待发货或已发货未完成订单可以撤销运单");
        }
        if (afterSaleDao.selectOpenByOrderId(orderId) != null) Asserts.fail("订单正在售后处理中，不能撤销运单");

        DmsWechatExpressOrder express = expressOrderDao.selectByShipmentIdForUpdate(tenantId, orderId, shipmentId);
        if (express == null) Asserts.fail("该包裹不是微信快递配送生成的运单，不能在这里撤销");
        if ("CANCELLED".equals(express.getStatus())) return context(order, express, true, false);
        if (!Set.of("SUCCESS", "CANCELLING", "CANCEL_CONFIRMED").contains(express.getStatus())) {
            Asserts.fail("当前微信运单状态不能撤销，请刷新后重试");
        }
        if ("CANCELLING".equals(express.getStatus()) && express.getUpdateTime() != null
                && express.getUpdateTime().isAfter(LocalDateTime.now().minusMinutes(2))) {
            Asserts.fail("微信运单正在撤销，请稍后刷新；超过两分钟仍未完成可再次重试");
        }
        DmsShopOrderShipment shipment = shipmentDao.selectByIdScoped(tenantId, orderId, shipmentId);
        if (shipment == null) Asserts.fail("商城包裹不存在，请刷新订单状态");

        String paymentNo = paymentNo(order);
        DmsWechatShippingSyncTask shippingTask = shippingTaskDao.selectByPaymentOrderNo(tenantId, paymentNo);
        if (shippingTask != null && "SENDING".equals(shippingTask.getStatus())) {
            Asserts.fail("微信发货信息正在同步，请稍后再撤销运单");
        }
        if (shippingTask != null && shippingTask.getRevision() != null && shippingTask.getRevision() > 0
                && shippingTask.getSyncedRevision() != null
                && shippingTask.getSyncedRevision() >= shippingTask.getRevision()) {
            Asserts.fail("该包裹已同步到微信交易组件，不能直接撤销；请联系平台按售后或异常履约流程处理");
        }
        DmsWechatLogisticsFollowTask followTask = followTaskDao.selectByShipment(tenantId, orderId, shipmentId);
        if (followTask != null && "SENDING".equals(followTask.getStatus())) {
            Asserts.fail("微信物流消息正在登记，请稍后再撤销运单");
        }
        if (followTask != null && "SUCCESS".equals(followTask.getStatus())) {
            Asserts.fail("该包裹已登记微信物流消息，不能直接撤销；请联系平台按售后或异常履约流程处理");
        }

        if ("SUCCESS".equals(express.getStatus()) && expressOrderDao.markCancelling(tenantId, express.getId()) != 1) {
            Asserts.fail("运单状态已变化，请刷新后重试");
        }
        if (!"CANCEL_CONFIRMED".equals(express.getStatus())) {
            shippingTaskDao.suspendForShipmentCancellation(tenantId, paymentNo);
            followTaskDao.suspendForShipmentCancellation(tenantId, orderId, shipmentId);
        }
        return context(order, express, false, "CANCEL_CONFIRMED".equals(express.getStatus()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirm(CancellationContext context) {
        if (context == null || context.alreadyCancelled() || context.cancelConfirmed()) return;
        if (expressOrderDao.markCancelConfirmed(context.tenantId(), context.expressId()) != 1) {
            Asserts.fail("微信已取消运单，但本地确认暂时失败；请使用同一订单重新执行撤销");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(CancellationContext context, String errorCode) {
        if (context == null || context.alreadyCancelled() || context.cancelConfirmed()) return;
        expressOrderDao.restoreCancelFailure(context.tenantId(), context.expressId(), safeCode(errorCode));
        shippingTaskDao.resumeAfterShipmentCancellationFailure(context.tenantId(), context.paymentOrderNo());
        followTaskDao.resumeAfterShipmentCancellationFailure(context.tenantId(), context.orderId(), context.shipmentId());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean finish(CancellationContext context) {
        if (context == null || context.alreadyCancelled()) return true;
        DmsShopOrder order = orderDao.selectByIdForUpdate(context.orderId());
        if (order == null) Asserts.fail("订单不存在");
        DmsWechatExpressOrder express = expressOrderDao.selectByShipmentIdForUpdate(
                context.tenantId(), context.orderId(), context.shipmentId());
        if (express == null) Asserts.fail("微信运单记录不存在");
        if ("CANCELLED".equals(express.getStatus())) return true;
        if (!"CANCEL_CONFIRMED".equals(express.getStatus())) {
            Asserts.fail("微信尚未确认取消运单，请稍后重试");
        }
        if (shipmentDao.deleteScoped(context.tenantId(), context.orderId(), context.shipmentId()) != 1) {
            Asserts.fail("商城包裹状态已变化，请刷新后重试");
        }

        List<DmsShopOrderShipment> remaining = shipmentDao.selectByOrderId(context.orderId());
        int shipped = remaining.stream().map(DmsShopOrderShipment::getShipmentQuantity)
                .filter(value -> value != null).mapToInt(Integer::intValue).sum();
        int shippable = Math.max(0, itemDao.sumQuantityByOrderId(context.orderId())
                - afterSaleItemDao.sumApprovedQuantityByOrderId(context.orderId()));
        int nextStatus = shippable > 0 && shipped >= shippable ? 2 : 1;
        DmsShopOrderShipment legacy = remaining.isEmpty() ? null : remaining.get(remaining.size() - 1);
        if (orderDao.reconcileShipmentState(context.orderId(), nextStatus,
                legacy == null ? null : legacy.getDeliveryCompany(),
                legacy == null ? null : legacy.getDeliveryNo(),
                legacy == null ? null : legacy.getDeliveryTime()) != 1) {
            Asserts.fail("订单履约状态已变化，请刷新后重试");
        }
        order.setStatus(nextStatus);
        order.setDeliveryCompany(legacy == null ? null : legacy.getDeliveryCompany());
        order.setDeliveryNo(legacy == null ? null : legacy.getDeliveryNo());
        order.setDeliveryTime(legacy == null ? null : legacy.getDeliveryTime());
        followTaskDao.markShipmentCancelled(context.tenantId(), context.orderId(), context.shipmentId());
        if (remaining.isEmpty()) {
            shippingTaskDao.markLocalShipmentCancelled(context.tenantId(), context.paymentOrderNo(),
                    "LOCAL_WAYBILL_CANCELLED");
        } else {
            shippingInfoService.enqueue(order);
        }
        if (expressOrderDao.markCancelled(context.tenantId(), express.getId()) != 1) {
            Asserts.fail("微信运单撤销状态保存失败，请使用同一订单重试");
        }
        operationLogService.log("SHOP_ORDER", "CANCEL_WECHAT_WAYBILL", "SHOP_ORDER",
                String.valueOf(order.getId()),
                "status=" + order.getStatus() + ", shipmentId=" + context.shipmentId(),
                "status=" + nextStatus + ", shipmentRemoved=true",
                "撤销微信快递运单，订单号=" + order.getOrderNo() + "，运单号=" + context.waybillId());
        orderRealtimeService.orderChanged(order, "ORDER_SHIPMENT_CANCELLED");
        return true;
    }

    private CancellationContext context(DmsShopOrder order, DmsWechatExpressOrder express,
                                        boolean cancelled, boolean confirmed) {
        return new CancellationContext(tenantId(order), order.getId(), express.getShipmentId(), express.getId(),
                order.getUserId(), paymentNo(order), express.getExpressOrderNo(), express.getDeliveryId(),
                express.getWaybillId(), cancelled, confirmed);
    }

    private void assertOrderAccess(DmsShopOrder order) {
        if (!TenantContext.getTenantId().equals(tenantId(order))) Asserts.fail("无权访问当前租户数据");
        DmsAdminUser admin = AdminContext.get();
        if (admin == null || admin.getMerchantId() == null) return;
        if (!admin.getMerchantId().equals(order.getMerchantId())) Asserts.fail("不能处理其他商户的订单");
        DmsMerchant merchant = merchantDao.selectById(admin.getMerchantId());
        if (merchant == null || !"ENABLED".equals(merchant.getFulfillmentStatus())) {
            Asserts.fail("商户履约权限已由平台接管或冻结，不能撤销运单");
        }
    }

    private Long tenantId(DmsShopOrder order) { return order.getTenantId() == null ? 1L : order.getTenantId(); }
    private String paymentNo(DmsShopOrder order) {
        return order.getPaymentOrderNo() == null || order.getPaymentOrderNo().isBlank()
                ? order.getOrderNo() : order.getPaymentOrderNo().trim();
    }
    private String safeCode(String value) {
        return value == null ? "UNKNOWN" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public record CancellationContext(Long tenantId, Long orderId, Long shipmentId, Long expressId,
                                      Long userId, String paymentOrderNo, String expressOrderNo,
                                      String deliveryId, String waybillId,
                                      boolean alreadyCancelled, boolean cancelConfirmed) { }
}
