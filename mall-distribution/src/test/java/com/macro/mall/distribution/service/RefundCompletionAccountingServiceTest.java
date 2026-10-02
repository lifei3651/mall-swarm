package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.entity.DmsFinanceRefund;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.service.impl.RefundCompletionAccountingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundCompletionAccountingServiceTest {

    @Test
    void existingFinanceRefundMakesCompletionIdempotentAcrossRepeatedCallbacksAndLegacyRows() {
        DmsShopAfterSaleItemDao saleItems = mock(DmsShopAfterSaleItemDao.class);
        DmsShopOrderItemDao orderItems = mock(DmsShopOrderItemDao.class);
        DistributionAuditService audit = mock(DistributionAuditService.class);
        MemberAssetService assets = mock(MemberAssetService.class);
        OrderBalanceAllocationService allocations = mock(OrderBalanceAllocationService.class);
        MerchantService merchants = mock(MerchantService.class);
        DmsShopAfterSale sale = sale();
        DmsShopOrder order = order("WECHAT");
        DmsFinanceRefund existing = new DmsFinanceRefund();
        existing.setRefundNo(sale.getAfterSaleNo());
        when(audit.getRefundsByOrderId(order.getId())).thenReturn(List.of(existing));
        RefundCompletionAccountingService service = new RefundCompletionAccountingService(
                saleItems, orderItems, audit, assets, allocations, merchants);

        assertFalse(service.complete(sale, order));

        verify(audit, never()).saveRefund(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(saleItems, orderItems, assets, allocations, merchants);
    }

    @Test
    void completedRefundAppliesFinanceAllocationAndMerchantEffectsExactlyOnce() {
        DmsShopAfterSaleItemDao saleItems = mock(DmsShopAfterSaleItemDao.class);
        DmsShopOrderItemDao orderItems = mock(DmsShopOrderItemDao.class);
        DistributionAuditService audit = mock(DistributionAuditService.class);
        MemberAssetService assets = mock(MemberAssetService.class);
        OrderBalanceAllocationService allocations = mock(OrderBalanceAllocationService.class);
        MerchantService merchants = mock(MerchantService.class);
        DmsShopAfterSale sale = sale();
        DmsShopOrder order = order("WECHAT");
        DmsShopAfterSaleItem refundLine = new DmsShopAfterSaleItem();
        refundLine.setOrderId(order.getId());
        refundLine.setOrderItemId(11L);
        refundLine.setProductId(21L);
        refundLine.setRefundQuantity(1);
        refundLine.setRefundAmount(new BigDecimal("5.00"));
        DmsShopOrderItem orderLine = new DmsShopOrderItem();
        orderLine.setId(11L);
        orderLine.setQuantity(1);
        orderLine.setTotalAmount(new BigDecimal("10.00"));
        orderLine.setTeamBonusMode("STANDARD");
        when(audit.getRefundsByOrderId(order.getId())).thenReturn(List.of());
        when(saleItems.selectByAfterSaleId(sale.getId())).thenReturn(List.of(refundLine));
        when(orderItems.selectByOrderId(order.getId())).thenReturn(List.of(orderLine));
        when(saleItems.sumApprovedBonusRefundByOrderId(order.getId())).thenReturn(new BigDecimal("5.00"));
        RefundCompletionAccountingService service = new RefundCompletionAccountingService(
                saleItems, orderItems, audit, assets, allocations, merchants);

        assertTrue(service.complete(sale, order));

        ArgumentCaptor<FinanceRefundDTO> refund = ArgumentCaptor.forClass(FinanceRefundDTO.class);
        verify(audit).saveRefund(refund.capture());
        assertEquals("AS-1", refund.getValue().getRefundNo());
        assertEquals(0, new BigDecimal("5.00").compareTo(refund.getValue().getBonusRefundAmount()));
        verify(allocations).recalculateAfterRefund(order.getId(), sale.getId());
        verify(merchants).reverseAfterSaleItems(List.of(refundLine));
        verifyNoInteractions(assets);
    }

    @Test
    void remainingOneCentCompletionDoesNotRepeatPreviousEightyNineRefund() {
        DmsShopAfterSaleItemDao saleItems = mock(DmsShopAfterSaleItemDao.class);
        DmsShopOrderItemDao orderItems = mock(DmsShopOrderItemDao.class);
        DistributionAuditService audit = mock(DistributionAuditService.class);
        MemberAssetService assets = mock(MemberAssetService.class);
        OrderBalanceAllocationService allocations = mock(OrderBalanceAllocationService.class);
        MerchantService merchants = mock(MerchantService.class);
        DmsShopAfterSale sale = sale(); sale.setAfterSaleNo("AS-B"); sale.setRefundAmount(new BigDecimal("0.01")); sale.setProductRefundAmount(new BigDecimal("0.01"));
        DmsShopOrder order = order("BALANCE"); order.setTotalAmount(new BigDecimal("89.01")); order.setPayAmount(new BigDecimal("89.01"));
        DmsFinanceRefund previous = new DmsFinanceRefund(); previous.setRefundNo("AS-A"); previous.setRefundAmount(new BigDecimal("89.00"));
        when(audit.getRefundsByOrderId(order.getId())).thenReturn(List.of(previous));
        DmsShopAfterSaleItem b = new DmsShopAfterSaleItem(); b.setOrderId(order.getId()); b.setProductId(22L); b.setOrderItemId(12L); b.setRefundQuantity(1); b.setRefundAmount(new BigDecimal("0.01"));
        when(saleItems.selectByAfterSaleId(sale.getId())).thenReturn(List.of(b));
        DmsShopOrderItem aLine = new DmsShopOrderItem(); aLine.setId(11L); aLine.setQuantity(1); aLine.setTotalAmount(new BigDecimal("89.00"));
        DmsShopOrderItem bLine = new DmsShopOrderItem(); bLine.setId(12L); bLine.setQuantity(1); bLine.setTotalAmount(new BigDecimal("0.01"));
        when(orderItems.selectByOrderId(order.getId())).thenReturn(List.of(aLine, bLine));
        RefundCompletionAccountingService service = new RefundCompletionAccountingService(saleItems, orderItems, audit, assets, allocations, merchants);
        assertTrue(service.complete(sale, order));
        ArgumentCaptor<FinanceRefundDTO> refund = ArgumentCaptor.forClass(FinanceRefundDTO.class); verify(audit).saveRefund(refund.capture());
        assertEquals("AS-B", refund.getValue().getRefundNo()); assertEquals(new BigDecimal("0.01"), refund.getValue().getRefundAmount());
        ArgumentCaptor<com.macro.mall.distribution.dto.AssetChangeDTO> balance = ArgumentCaptor.forClass(com.macro.mall.distribution.dto.AssetChangeDTO.class); verify(assets).issue(balance.capture());
        assertEquals(new BigDecimal("0.01"), balance.getValue().getAmount()); verify(merchants).reverseAfterSaleItems(List.of(b));
    }

    private DmsShopAfterSale sale() {
        DmsShopAfterSale sale = new DmsShopAfterSale();
        sale.setId(1L);
        sale.setOrderId(2L);
        sale.setOrderNo("ORDER-2");
        sale.setAfterSaleNo("AS-1");
        sale.setRefundAmount(new BigDecimal("5.00"));
        sale.setProductRefundAmount(new BigDecimal("5.00"));
        sale.setFreightRefundAmount(BigDecimal.ZERO);
        sale.setRefundQuantity(1);
        sale.setReason("测试退款");
        return sale;
    }

    private DmsShopOrder order(String payType) {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(2L);
        order.setUserId(3L);
        order.setOrderNo("ORDER-2");
        order.setPayType(payType);
        order.setPayAmount(new BigDecimal("10.00"));
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFreightAmount(BigDecimal.ZERO);
        return order;
    }
}
