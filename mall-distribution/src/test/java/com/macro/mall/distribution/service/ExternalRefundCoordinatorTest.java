package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.service.impl.ExternalRefundCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExternalRefundCoordinatorTest {
    @Test
    void completedSaleIsAnIdempotentNoOp() {
        DmsShopAfterSaleDao saleDao = mock(DmsShopAfterSaleDao.class);
        DmsShopAfterSale completed = pendingSale();
        completed.setStatus(1);
        when(saleDao.selectById(1L)).thenReturn(completed);
        AlipayService alipay = mock(AlipayService.class);

        assertDoesNotThrow(() -> new ExternalRefundCoordinator(saleDao,
                mock(DmsShopAfterSaleItemDao.class), mock(DmsShopOrderDao.class),
                mock(DmsShopOrderItemDao.class), mock(DmsAgentDao.class), mock(AgentService.class),
                alipay, mock(PlatformTransactionManager.class)).process(1L));

        verifyNoInteractions(alipay);
    }

    @Test
    void channelSuccessIsFollowedByIndependentLocalCompletion() {
        DmsShopAfterSaleDao saleDao = mock(DmsShopAfterSaleDao.class);
        DmsShopAfterSaleItemDao saleItemDao = mock(DmsShopAfterSaleItemDao.class);
        DmsShopOrderDao orderDao = mock(DmsShopOrderDao.class);
        DmsShopOrderItemDao orderItemDao = mock(DmsShopOrderItemDao.class);
        DmsAgentDao agentDao = mock(DmsAgentDao.class);
        AgentService agentService = mock(AgentService.class);
        AlipayService alipay = mock(AlipayService.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        DmsShopAfterSale sale = pendingSale();
        when(saleDao.selectById(1L)).thenReturn(sale);
        when(saleDao.selectByIdForUpdate(1L)).thenReturn(sale);
        when(saleDao.markRefundCompleted(1L)).thenReturn(1);
        DmsShopOrder groupedChild = alipayOrder();
        groupedChild.setPaymentOrderNo("TRADE-100");
        when(orderDao.selectById(2L)).thenReturn(groupedChild);
        when(orderItemDao.selectByOrderId(2L)).thenReturn(List.of(orderItem(2)));
        when(saleItemDao.sumApprovedQuantityByOrderId(2L)).thenReturn(2);
        when(alipay.isConfigured()).thenReturn(true);
        when(alipay.refund("TRADE-100", "AS-1", "99.00", "商城售后退款：测试退款")).thenReturn(true);

        new ExternalRefundCoordinator(saleDao, saleItemDao, orderDao, orderItemDao,
                agentDao, agentService, alipay, manager).process(1L);

        verify(saleDao).markRefundCompleted(1L);
        verify(alipay).refund("TRADE-100", "AS-1", "99.00", "商城售后退款：测试退款");
        verify(orderDao).closeAfterSale(2L);
        verify(manager).commit(any());
    }

    @Test
    void channelFailureKeepsRecoverableProcessingState() {
        DmsShopAfterSaleDao saleDao = mock(DmsShopAfterSaleDao.class);
        DmsShopAfterSaleItemDao saleItemDao = mock(DmsShopAfterSaleItemDao.class);
        DmsShopOrderDao orderDao = mock(DmsShopOrderDao.class);
        DmsShopOrderItemDao orderItemDao = mock(DmsShopOrderItemDao.class);
        DmsAgentDao agentDao = mock(DmsAgentDao.class);
        AgentService agentService = mock(AgentService.class);
        AlipayService alipay = mock(AlipayService.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(saleDao.selectById(1L)).thenReturn(pendingSale());
        when(orderDao.selectById(2L)).thenReturn(alipayOrder());
        when(alipay.isConfigured()).thenReturn(true);
        when(alipay.refund(any(), any(), any(), any())).thenReturn(false);

        assertThrows(ApiException.class,
                () -> new ExternalRefundCoordinator(saleDao, saleItemDao, orderDao, orderItemDao,
                        agentDao, agentService, alipay, manager).process(1L));
        verify(saleDao, never()).markRefundCompleted(1L);
    }

    @Test
    void localCompletionFailureRollsBackAndCanBeRetriedWithSameAfterSaleNumber() {
        DmsShopAfterSaleDao saleDao = mock(DmsShopAfterSaleDao.class);
        DmsShopAfterSaleItemDao saleItemDao = mock(DmsShopAfterSaleItemDao.class);
        DmsShopOrderDao orderDao = mock(DmsShopOrderDao.class);
        DmsShopOrderItemDao orderItemDao = mock(DmsShopOrderItemDao.class);
        AlipayService alipay = mock(AlipayService.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        DmsShopAfterSale sale = pendingSale();
        when(saleDao.selectById(1L)).thenReturn(sale);
        when(saleDao.selectByIdForUpdate(1L)).thenReturn(sale);
        when(saleDao.markRefundCompleted(1L)).thenReturn(0);
        when(orderDao.selectById(2L)).thenReturn(alipayOrder());
        when(alipay.isConfigured()).thenReturn(true);
        when(alipay.refund("ORDER-2", "AS-1", "99.00", "商城售后退款：测试退款")).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> new ExternalRefundCoordinator(saleDao, saleItemDao, orderDao, orderItemDao,
                        mock(DmsAgentDao.class), mock(AgentService.class), alipay, manager).process(1L));

        verify(manager).rollback(transaction);
        verify(orderDao, never()).closeAfterSale(2L);
    }

    private DmsShopAfterSale pendingSale() {
        DmsShopAfterSale sale = new DmsShopAfterSale();
        sale.setId(1L);
        sale.setOrderId(2L);
        sale.setAfterSaleNo("AS-1");
        sale.setRefundAmount(new BigDecimal("99.00"));
        sale.setReason("测试退款");
        sale.setStatus(6);
        return sale;
    }

    private DmsShopOrder alipayOrder() {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(2L);
        order.setOrderNo("ORDER-2");
        order.setPayType("ALIPAY");
        return order;
    }

    private DmsShopOrderItem orderItem(int quantity) {
        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setQuantity(quantity);
        return item;
    }
}
