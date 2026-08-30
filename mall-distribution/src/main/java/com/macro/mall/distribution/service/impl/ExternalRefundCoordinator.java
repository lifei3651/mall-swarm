package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopTradeDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsShopTrade;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.AlipayService;
import com.macro.mall.distribution.service.WeChatPayService;
import com.macro.mall.distribution.wechat.WeChatPayGateway;
import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.RoundingMode;
import java.math.BigDecimal;

/**
 * 第三方退款恢复器。调用发生在本地账务事务提交之后；渠道成功后再用独立事务标记完成。
 * 如果渠道成功但最终标记提交失败，售后仍停在状态6，同一售后号可安全重试并恢复。
 */
@Service
@Slf4j
public class ExternalRefundCoordinator {
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopTradeDao tradeDao;
    private final DmsShopOrderItemDao orderItemDao;
    private final DmsShopOrderShipmentDao orderShipmentDao;
    private final DmsAgentDao agentDao;
    private final AgentService agentService;
    private final AlipayService alipayService;
    private final WeChatPayService weChatPayService;
    private final TransactionTemplate transactionTemplate;

    public ExternalRefundCoordinator(DmsShopAfterSaleDao afterSaleDao, DmsShopAfterSaleItemDao afterSaleItemDao,
                                     DmsShopOrderDao orderDao, DmsShopOrderItemDao orderItemDao,
                                     DmsShopOrderShipmentDao orderShipmentDao,
                                     DmsAgentDao agentDao, AgentService agentService,
                                     DmsShopTradeDao tradeDao, AlipayService alipayService,
                                     WeChatPayService weChatPayService, PlatformTransactionManager transactionManager) {
        this.afterSaleDao = afterSaleDao;
        this.afterSaleItemDao = afterSaleItemDao;
        this.orderDao = orderDao;
        this.tradeDao = tradeDao;
        this.orderItemDao = orderItemDao;
        this.orderShipmentDao = orderShipmentDao;
        this.agentDao = agentDao;
        this.agentService = agentService;
        this.alipayService = alipayService;
        this.weChatPayService = weChatPayService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void process(Long afterSaleId) {
        DmsShopAfterSale afterSale = afterSaleDao.selectById(afterSaleId);
        if (afterSale == null || !Integer.valueOf(6).equals(afterSale.getStatus())) return;
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null || (!"ALIPAY".equalsIgnoreCase(order.getPayType())
                && !"WECHAT".equalsIgnoreCase(order.getPayType()))) {
            Asserts.fail("退款处理中订单不存在或支付方式不正确");
        }
        String paymentOrderNo = order.getPaymentOrderNo() == null || order.getPaymentOrderNo().isBlank()
                ? order.getOrderNo() : order.getPaymentOrderNo();
        String reason = "商城售后退款：" + (afterSale.getReason() == null ? "后台处理" : afterSale.getReason());
        if ("ALIPAY".equalsIgnoreCase(order.getPayType())) {
            if (!alipayService.isConfigured()) Asserts.fail("支付宝退款未配置，请先完成支付宝密钥配置");
            boolean success = alipayService.refund(paymentOrderNo, afterSale.getAfterSaleNo(),
                    afterSale.getRefundAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(), reason);
            if (!success) Asserts.fail("支付宝退款失败，售后已保留在退款处理中，可核对后重试");
            completeChannelRefund(afterSaleId, "支付宝");
            return;
        }
        if (!weChatPayService.isConfigured()) Asserts.fail("微信退款未配置，请先完成微信支付商户配置");
        WeChatPayService.RefundState state = weChatPayService.requestRefund(paymentOrderNo,
                afterSale.getAfterSaleNo(), afterSale.getRefundAmount(), paymentAmount(order), reason);
        if (state == WeChatPayService.RefundState.FAILED) {
            Asserts.fail("微信退款申请失败，售后已保留在退款处理中，可核对后重试");
        }
        if (state == WeChatPayService.RefundState.PROCESSING) {
            log.info("微信退款已受理，等待异步结果: afterSaleId={}, afterSaleNo={}", afterSaleId, afterSale.getAfterSaleNo());
            return;
        }
        completeChannelRefund(afterSaleId, "微信支付");
    }

    public void completeWechatRefund(WeChatPayGateway.RefundNotification notification) {
        if (notification == null || !"SUCCESS".equals(notification.state())) {
            Asserts.fail("微信退款通知状态不正确");
        }
        if (notification.refundNo() != null && notification.refundNo().startsWith("LATEPAY-")) {
            completeLateWechatRefund(notification);
            return;
        }
        RefundCompletionTarget completionTarget = transactionTemplate.execute(status -> {
            DmsShopAfterSale afterSale = afterSaleDao.selectByAfterSaleNoForUpdate(notification.refundNo());
            if (afterSale == null) throw new IllegalStateException("微信退款对应售后单不存在");
            if (!Integer.valueOf(6).equals(afterSale.getStatus())
                    && !Integer.valueOf(1).equals(afterSale.getStatus())) {
                throw new IllegalStateException("微信退款对应售后单状态不正确");
            }
            DmsShopOrder order = orderDao.selectByIdForUpdate(afterSale.getOrderId());
            if (order == null || !"WECHAT".equalsIgnoreCase(order.getPayType())) {
                throw new IllegalStateException("微信退款订单不存在或支付方式不正确");
            }
            validateWechatRefund(notification, order, afterSale.getRefundAmount());
            return new RefundCompletionTarget(afterSale.getId(), Integer.valueOf(6).equals(afterSale.getStatus()));
        });
        if (completionTarget == null) Asserts.fail("微信退款对应售后单不存在");
        if (!completionTarget.needsCompletion()) return;
        completeChannelRefund(completionTarget.afterSaleId(), "微信支付");
    }

    private void completeChannelRefund(Long afterSaleId, String channelName) {
        transactionTemplate.executeWithoutResult(status -> {
            DmsShopAfterSale locked = afterSaleDao.selectByIdForUpdate(afterSaleId);
            if (locked != null && Integer.valueOf(6).equals(locked.getStatus())
                    && afterSaleDao.markRefundCompleted(afterSaleId) != 1) {
                throw new IllegalStateException(channelName + "已退款，但本地完成状态保存失败，请使用同一售后单重试恢复");
            }
            if (locked != null) {
                DmsShopOrder lockedOrder = orderDao.selectByIdForUpdate(locked.getOrderId());
                if (lockedOrder == null) throw new IllegalStateException(channelName + "已退款，但本地订单不存在，请人工核对");
                finalizeOrderAfterChannelSuccess(lockedOrder);
            }
        });
        log.info("{}退款与本地售后状态已完成: afterSaleId={}", channelName, afterSaleId);
    }

    private void completeLateWechatRefund(WeChatPayGateway.RefundNotification notification) {
        String expectedRefundNo = "LATEPAY-" + SecureUtil.sha256(notification.paymentNo()).substring(0, 32);
        if (!expectedRefundNo.equals(notification.refundNo())) Asserts.fail("微信迟到支付退款编号不匹配");
        transactionTemplate.executeWithoutResult(status -> {
            DmsShopTrade trade = tradeDao.selectByTradeNoForUpdate(notification.paymentNo());
            DmsShopOrder order = trade == null ? orderDao.selectByOrderNoForUpdate(notification.paymentNo()) : null;
            if (trade == null && order == null) throw new IllegalStateException("微信迟到支付退款订单不存在");
            BigDecimal amount = trade == null ? order.getPayAmount() : trade.getPayAmount();
            String payType = trade == null ? order.getPayType() : trade.getPayType();
            Integer localStatus = trade == null ? order.getStatus() : trade.getStatus();
            if (!"WECHAT".equalsIgnoreCase(payType) || !Integer.valueOf(4).equals(localStatus)) {
                throw new IllegalStateException("微信迟到支付退款订单状态不正确");
            }
            validateWechatRefundAmount(notification, amount, amount);
            int marked = trade == null ? orderDao.markLateRefunded(order.getId()) : tradeDao.markLateRefunded(trade.getId());
            if (marked != 1) {
                DmsShopTrade refreshedTrade = trade == null ? null : tradeDao.selectByTradeNo(notification.paymentNo());
                DmsShopOrder refreshedOrder = trade == null ? orderDao.selectByOrderNo(notification.paymentNo()) : null;
                Integer existingFlag = refreshedTrade == null
                        ? (refreshedOrder == null ? null : refreshedOrder.getLateRefundFlag())
                        : refreshedTrade.getLateRefundFlag();
                if (!Integer.valueOf(1).equals(existingFlag)) {
                    throw new IllegalStateException("微信迟到支付退款完成标记保存失败");
                }
            }
        });
        log.info("微信迟到支付退款完成: paymentNo={}", notification.paymentNo());
    }

    private void validateWechatRefund(WeChatPayGateway.RefundNotification notification,
                                      DmsShopOrder order, BigDecimal refundAmount) {
        String paymentNo = order.getPaymentOrderNo() == null || order.getPaymentOrderNo().isBlank()
                ? order.getOrderNo() : order.getPaymentOrderNo();
        if (!paymentNo.equals(notification.paymentNo())) Asserts.fail("微信退款支付单号不匹配");
        validateWechatRefundAmount(notification, refundAmount, paymentAmount(order));
    }

    private void validateWechatRefundAmount(WeChatPayGateway.RefundNotification notification,
                                            BigDecimal refundAmount, BigDecimal paymentTotal) {
        long expectedRefund = refundAmount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        long expectedTotal = paymentTotal.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        if (!"CNY".equalsIgnoreCase(notification.currency()) || notification.refundFen() == null
                || notification.totalFen() == null || notification.refundFen() != expectedRefund
                || notification.totalFen() != expectedTotal) {
            Asserts.fail("微信退款金额或币种不匹配");
        }
    }

    private BigDecimal paymentAmount(DmsShopOrder order) {
        if (order.getTradeId() == null) return order.getPayAmount();
        DmsShopTrade trade = tradeDao.selectById(order.getTradeId());
        String paymentNo = order.getPaymentOrderNo() == null || order.getPaymentOrderNo().isBlank()
                ? order.getOrderNo() : order.getPaymentOrderNo();
        if (trade == null || !paymentNo.equals(trade.getTradeNo())) {
            Asserts.fail("微信退款交易父单不存在或不匹配");
        }
        return trade.getPayAmount();
    }

    private record RefundCompletionTarget(Long afterSaleId, boolean needsCompletion) {
    }

    private void finalizeOrderAfterChannelSuccess(DmsShopOrder order) {
        int originalQuantity = orderItemDao.selectByOrderId(order.getId()).stream()
                .map(DmsShopOrderItem::getQuantity).filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        int refundedQuantity = afterSaleItemDao.sumApprovedQuantityByOrderId(order.getId());
        if (refundedQuantity < originalQuantity) {
            reconcilePartiallyShippedOrder(order, originalQuantity - refundedQuantity);
            return;
        }
        orderDao.closeAfterSale(order.getId());
        DmsAgent agent = agentDao.selectByUserId(order.getUserId());
        if (agent == null || !AgentSourceTypeEnum.SELF_REGISTER.getValue().equals(agent.getSourceType())
                || orderDao.countValidPaidOrdersByUserId(order.getUserId()) > 0) return;
        try {
            agentService.deactivate(agent.getId(),
                    "退款后退回非会员：名下已无有效支付订单，订单：" + order.getOrderNo());
        } catch (ApiException e) {
            log.warn("渠道退款完成后自动取消会员资格未执行: userId={}, reason={}", order.getUserId(), e.getMessage());
        }
    }

    private void reconcilePartiallyShippedOrder(DmsShopOrder order, int shippableQuantity) {
        if (!Integer.valueOf(1).equals(order.getStatus())
                || orderShipmentDao.sumQuantityByOrderId(order.getId()) < shippableQuantity) return;
        java.util.List<DmsShopOrderShipment> shipments = orderShipmentDao.selectByOrderId(order.getId());
        if (shipments == null || shipments.isEmpty()) return;
        DmsShopOrderShipment latest = shipments.get(shipments.size() - 1);
        if (orderDao.ship(order.getId(), latest.getDeliveryCompany(), latest.getDeliveryNo()) != 1) {
            throw new IllegalStateException("外部渠道已退款，但订单发货状态同步失败，请使用同一售后单重试恢复");
        }
    }
}
