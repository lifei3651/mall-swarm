package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.AlipayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.RoundingMode;

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
    private final DmsShopOrderItemDao orderItemDao;
    private final DmsAgentDao agentDao;
    private final AgentService agentService;
    private final AlipayService alipayService;
    private final TransactionTemplate transactionTemplate;

    public ExternalRefundCoordinator(DmsShopAfterSaleDao afterSaleDao, DmsShopAfterSaleItemDao afterSaleItemDao,
                                     DmsShopOrderDao orderDao, DmsShopOrderItemDao orderItemDao,
                                     DmsAgentDao agentDao, AgentService agentService,
                                     AlipayService alipayService, PlatformTransactionManager transactionManager) {
        this.afterSaleDao = afterSaleDao;
        this.afterSaleItemDao = afterSaleItemDao;
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
        this.agentDao = agentDao;
        this.agentService = agentService;
        this.alipayService = alipayService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void process(Long afterSaleId) {
        DmsShopAfterSale afterSale = afterSaleDao.selectById(afterSaleId);
        if (afterSale == null || !Integer.valueOf(6).equals(afterSale.getStatus())) return;
        DmsShopOrder order = orderDao.selectById(afterSale.getOrderId());
        if (order == null || !"ALIPAY".equalsIgnoreCase(order.getPayType())) {
            Asserts.fail("退款处理中订单不存在或支付方式不正确");
        }
        if (!alipayService.isConfigured()) Asserts.fail("支付宝退款未配置，请先完成支付宝密钥配置");
        String paymentOrderNo = order.getPaymentOrderNo() == null || order.getPaymentOrderNo().isBlank()
                ? order.getOrderNo() : order.getPaymentOrderNo();
        boolean success = alipayService.refund(paymentOrderNo, afterSale.getAfterSaleNo(),
                afterSale.getRefundAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "商城售后退款：" + (afterSale.getReason() == null ? "后台处理" : afterSale.getReason()));
        if (!success) Asserts.fail("支付宝退款失败，售后已保留在退款处理中，可核对后重试");

        transactionTemplate.executeWithoutResult(status -> {
            DmsShopAfterSale locked = afterSaleDao.selectByIdForUpdate(afterSaleId);
            if (locked != null && Integer.valueOf(6).equals(locked.getStatus())
                    && afterSaleDao.markRefundCompleted(afterSaleId) != 1) {
                throw new IllegalStateException("支付宝已退款，但本地完成状态保存失败，请使用同一售后单重试恢复");
            }
            if (locked != null) finalizeOrderAfterChannelSuccess(order);
        });
        log.info("支付宝退款与本地售后状态已完成: afterSaleId={}, afterSaleNo={}", afterSaleId, afterSale.getAfterSaleNo());
    }

    private void finalizeOrderAfterChannelSuccess(DmsShopOrder order) {
        int originalQuantity = orderItemDao.selectByOrderId(order.getId()).stream()
                .map(DmsShopOrderItem::getQuantity).filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        if (afterSaleItemDao.sumApprovedQuantityByOrderId(order.getId()) < originalQuantity) return;
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
}
