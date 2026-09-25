package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsCommissionClawbackDao;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsFinanceRefundDao;
import com.macro.mall.distribution.dao.DmsOrderBalanceAllocationDao;
import com.macro.mall.distribution.dao.DmsOrderFinanceDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.entity.DmsOrderBalanceAllocation;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.service.MemberAssetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderBalanceAllocationLockOrderTest {

    @Mock private DmsOrderBalanceAllocationDao allocationDao;
    @Mock private DmsShopOrderDao orderDao;
    @Mock private DmsOrderFinanceDao financeDao;
    @Mock private DmsFinanceRefundDao refundDao;
    @Mock private DmsShopAfterSaleItemDao afterSaleItemDao;
    @Mock private DmsShopAfterSaleDao afterSaleDao;
    @Mock private DmsCommissionRecordDao commissionRecordDao;
    @Mock private DmsCommissionClawbackDao clawbackDao;
    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsAgentDao agentDao;
    @Mock private MemberAssetService memberAssetService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ShopAfterSaleWindowPolicy afterSaleWindowPolicy;

    @InjectMocks private OrderBalanceAllocationServiceImpl service;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void settlementLocksOrderBeforeAllocation() {
        TenantContext.setTenantId(2L);
        when(allocationDao.selectOrderIdById(2L, 7L)).thenReturn(23L);
        DmsShopOrder order = new DmsShopOrder();
        order.setId(23L);
        order.setStatus(2);
        when(orderDao.selectByIdForUpdate(23L)).thenReturn(order);
        DmsOrderBalanceAllocation allocation = pendingAllocation(7L, 23L, 2L);
        when(allocationDao.selectByIdForUpdate(7L)).thenReturn(allocation);

        Boolean settled = ReflectionTestUtils.invokeMethod(service, "settleOne", 7L);

        assertFalse(Boolean.TRUE.equals(settled));
        InOrder orderOfCalls = inOrder(allocationDao, orderDao);
        orderOfCalls.verify(allocationDao).selectOrderIdById(2L, 7L);
        orderOfCalls.verify(orderDao).selectByIdForUpdate(23L);
        orderOfCalls.verify(allocationDao).selectByIdForUpdate(7L);
        verifyNoInteractions(memberAssetService);
    }

    @Test
    void settlementRejectsChangedOrCrossTenantAllocationAfterLock() {
        TenantContext.setTenantId(2L);
        when(allocationDao.selectOrderIdById(2L, 7L)).thenReturn(23L);
        when(orderDao.selectByIdForUpdate(23L)).thenReturn(new DmsShopOrder());
        when(allocationDao.selectByIdForUpdate(7L)).thenReturn(pendingAllocation(7L, 23L, 3L));

        Boolean settled = ReflectionTestUtils.invokeMethod(service, "settleOne", 7L);

        assertFalse(Boolean.TRUE.equals(settled));
        verifyNoInteractions(memberAssetService);
        verify(allocationDao, never()).markSettled(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void missingTenantScopedAllocationDoesNotLockAnotherOrdersRow() {
        TenantContext.setTenantId(2L);
        when(allocationDao.selectOrderIdById(2L, 7L)).thenReturn(null);

        Boolean settled = ReflectionTestUtils.invokeMethod(service, "settleOne", 7L);

        assertFalse(Boolean.TRUE.equals(settled));
        verify(allocationDao).selectOrderIdById(2L, 7L);
        verifyNoInteractions(orderDao, memberAssetService);
    }

    @Test
    void preparationAndRefundRecalculationAlsoLockOrderFirst() {
        TenantContext.setTenantId(2L);
        when(orderDao.selectByIdForUpdate(23L)).thenReturn(null);
        assertEquals(List.of(), service.prepareForOrder(23L));
        verify(orderDao, never()).selectById(23L);
        verifyNoInteractions(allocationDao);

        DmsShopOrder order = new DmsShopOrder();
        order.setId(24L);
        when(orderDao.selectByIdForUpdate(24L)).thenReturn(order);
        when(allocationDao.selectByOrderId(24L)).thenReturn(List.of());
        service.recalculateAfterRefund(24L, 31L);

        InOrder orderOfCalls = inOrder(orderDao, allocationDao);
        orderOfCalls.verify(orderDao).selectByIdForUpdate(23L);
        orderOfCalls.verify(orderDao).selectByIdForUpdate(24L);
        orderOfCalls.verify(allocationDao).selectByOrderId(24L);
        verify(orderDao, never()).selectById(24L);
    }

    private DmsOrderBalanceAllocation pendingAllocation(Long id, Long orderId, Long tenantId) {
        DmsOrderBalanceAllocation allocation = new DmsOrderBalanceAllocation();
        allocation.setId(id);
        allocation.setOrderId(orderId);
        allocation.setTenantId(tenantId);
        allocation.setStatus(0);
        allocation.setCurrentAmount(new BigDecimal("1.00"));
        return allocation;
    }
}
