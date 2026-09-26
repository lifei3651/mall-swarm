package com.macro.mall.distribution.config;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.BonusCalculationTaskService;
import com.macro.mall.distribution.service.CommissionSettlementService;
import com.macro.mall.distribution.service.ErpIntegrationService;
import com.macro.mall.distribution.service.MerchantService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.WeChatPayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleTaskIsolationTest {

    @Mock private PerformanceService performanceService;
    @Mock private BonusCalculationTaskService bonusCalculationTaskService;
    @Mock private ErpIntegrationService erpIntegrationService;
    @Mock private CommissionSettlementService commissionSettlementService;
    @Mock private ShopService shopService;
    @Mock private OrderBalanceAllocationService orderBalanceAllocationService;
    @Mock private OperationLogService operationLogService;
    @Mock private MerchantService merchantService;
    @Mock private ShopAfterSaleService shopAfterSaleService;
    @Mock private WeChatPayService weChatPayService;
    @Mock private DistributedScheduledTaskRunner scheduledTaskRunner;
    @Mock private DmsTenantDao tenantDao;

    @AfterEach
    void clearTenantContext() { TenantContext.clear(); }

    private ScheduleTask newTask() {
        return new ScheduleTask(performanceService, bonusCalculationTaskService,
                erpIntegrationService, commissionSettlementService, shopService,
                orderBalanceAllocationService, operationLogService, merchantService,
                shopAfterSaleService, weChatPayService, scheduledTaskRunner, tenantDao);
    }

    private DmsTenant tenant(long id) {
        DmsTenant tenant = new DmsTenant();
        tenant.setId(id);
        return tenant;
    }

    @Test
    void commissionFailureDoesNotSkipBalanceAndMerchantSettlement() {
        ScheduleTask task = newTask();
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L)));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("cooling-off-settlement"), any(), any());
        doThrow(new IllegalStateException("single commission failure"))
                .when(commissionSettlementService).settleEligibleAfterCoolingOff(200);

        task.settleCoolingOffCommissions();

        verify(orderBalanceAllocationService).settleEligibleAfterCoolingOff(200);
        verify(merchantService).releaseEligibleSettlements(200);
    }

    @Test
    void autoReceiptUsesDedicatedDistributedTaskAndInvokesTheBoundedBatch() {
        ScheduleTask task = newTask();
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L)));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("auto-confirm-receipt"), any(), any());
        when(shopService.autoConfirmExpiredShippedOrders(200)).thenReturn(1);

        task.autoConfirmExpiredShippedOrders();

        verify(shopService).autoConfirmExpiredShippedOrders(200);
    }

    @Test
    void exchangeAutoReceiptUsesDedicatedDistributedTaskAndInvokesTheBoundedBatch() {
        ScheduleTask task = newTask();
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L)));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("auto-complete-exchange-receipts"), any(), any());
        when(shopAfterSaleService.autoCompleteExpiredExchangeReceipts(200)).thenReturn(1);

        task.autoCompleteExpiredExchangeReceipts();

        verify(shopAfterSaleService).autoCompleteExpiredExchangeReceipts(200);
    }

    @Test
    void refundReconciliationUsesDedicatedDistributedTaskAndInvokesTheBoundedBatch() {
        ScheduleTask task = newTask();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("reconcile-processing-wechat-refunds"), any(), any());
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L)));
        when(shopAfterSaleService.reconcileProcessingExternalRefunds(50)).thenReturn(1);

        task.reconcileProcessingExternalRefunds();

        verify(shopAfterSaleService).reconcileProcessingExternalRefunds(50);
    }

    @Test
    void refundReconciliationVisitsBothTenantsAndRestoresPreviousContext() {
        ScheduleTask task = newTask();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("reconcile-processing-wechat-refunds"), any(), any());
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(2L), tenant(1L)));
        List<String> visits = new ArrayList<>();
        doAnswer(invocation -> {
            visits.add(TenantContext.getTenantId() + ":" + invocation.<Integer>getArgument(0));
            return 0;
        }).when(shopAfterSaleService).reconcileProcessingExternalRefunds(anyInt());
        TenantContext.setTenantId(77L);

        task.reconcileProcessingExternalRefunds();

        assertEquals(List.of("1:25", "2:25"), visits);
        assertEquals(77L, TenantContext.getCurrentTenantId());
    }

    @Test
    void refundFailureInOneTenantDoesNotSkipNextTenantOrLeakContext() {
        ScheduleTask task = newTask();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("reconcile-processing-wechat-refunds"), any(), any());
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L), tenant(2L)));
        doAnswer(invocation -> {
            if (TenantContext.getTenantId() == 1L) throw new IllegalStateException("channel down");
            return 0;
        }).when(shopAfterSaleService).reconcileProcessingExternalRefunds(25);

        task.reconcileProcessingExternalRefunds();

        verify(shopAfterSaleService, org.mockito.Mockito.times(2)).reconcileProcessingExternalRefunds(25);
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void refundTenantRoundRobinRespectsGlobalFiftyAttemptBudget() {
        ScheduleTask task = newTask();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("reconcile-processing-wechat-refunds"), any(), any());
        List<DmsTenant> tenants = new ArrayList<>();
        for (long id = 1; id <= 52; id++) tenants.add(tenant(id));
        when(tenantDao.selectAll()).thenReturn(tenants);
        List<Long> visits = new ArrayList<>();
        doAnswer(invocation -> {
            assertEquals(1, invocation.<Integer>getArgument(0));
            visits.add(TenantContext.getTenantId());
            return 0;
        }).when(shopAfterSaleService).reconcileProcessingExternalRefunds(1);

        task.reconcileProcessingExternalRefunds();
        assertEquals(50, visits.size());
        task.reconcileProcessingExternalRefunds();
        assertEquals(100, visits.size());
        assertEquals(List.of(51L, 52L, 1L), visits.subList(50, 53));
    }

    @Test
    void latePaymentRefundRecoveryUsesDedicatedDistributedTaskAndBoundedBatch() {
        ScheduleTask task = newTask();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("reconcile-late-payment-refunds"), any(), any());
        when(weChatPayService.reconcileProcessingLatePaymentRefunds(50)).thenReturn(1);

        task.reconcileProcessingLatePaymentRefunds();

        verify(weChatPayService).reconcileProcessingLatePaymentRefunds(50);
    }

    @Test
    void pendingOrderTimeoutVisitsBothTenantsDespiteOneFailureAndRestoresContext() {
        ScheduleTask task = newTask();
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(2L), tenant(1L)));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("close-expired-orders"), any(), any());
        List<String> visits = new ArrayList<>();
        doAnswer(invocation -> {
            visits.add(TenantContext.getTenantId() + ":" + invocation.<Integer>getArgument(0));
            if (TenantContext.getTenantId() == 1L) throw new IllegalStateException("bad pending order");
            return 1;
        }).when(shopService).closeExpiredPendingOrders(anyInt());
        TenantContext.setTenantId(77L);

        task.closeExpiredPendingOrders();

        assertEquals(List.of("1:100", "2:100"), visits);
        assertEquals(77L, TenantContext.getCurrentTenantId());
    }

    @Test
    void receiptAndAfterSaleTimeoutsUseScopedBudgetsForBothTenants() {
        ScheduleTask task = newTask();
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L), tenant(2L)));
        for (String key : List.of("auto-confirm-receipt", "close-expired-waiting-returns",
                "auto-complete-exchange-receipts")) {
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(2).run();
                return true;
            }).when(scheduledTaskRunner).run(eq(key), any(), any());
        }
        List<String> visits = new ArrayList<>();
        doAnswer(invocation -> {
            visits.add("receipt:" + TenantContext.getTenantId() + ":" + invocation.<Integer>getArgument(0));
            return 0;
        }).when(shopService).autoConfirmExpiredShippedOrders(anyInt());
        doAnswer(invocation -> {
            visits.add("return:" + TenantContext.getTenantId() + ":" + invocation.<Integer>getArgument(0));
            return 0;
        }).when(shopAfterSaleService).expireWaitingReturnShipments(anyInt());
        doAnswer(invocation -> {
            visits.add("exchange:" + TenantContext.getTenantId() + ":" + invocation.<Integer>getArgument(0));
            return 0;
        }).when(shopAfterSaleService).autoCompleteExpiredExchangeReceipts(anyInt());

        task.autoConfirmExpiredShippedOrders();
        task.closeExpiredWaitingReturns();
        task.autoCompleteExpiredExchangeReceipts();

        assertEquals(List.of("receipt:1:100", "receipt:2:100", "return:1:100", "return:2:100",
                "exchange:1:100", "exchange:2:100"), visits);
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void allCoolingOffLanesVisitBothTenantsWhenFirstTenantCommissionFails() {
        ScheduleTask task = newTask();
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L), tenant(2L)));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("cooling-off-settlement"), any(), any());
        List<String> visits = new ArrayList<>();
        doAnswer(invocation -> {
            visits.add("bonus:" + TenantContext.getTenantId() + ":" + invocation.<Integer>getArgument(0));
            if (TenantContext.getTenantId() == 1L) throw new IllegalStateException("bonus unavailable");
            return 0;
        }).when(commissionSettlementService).settleEligibleAfterCoolingOff(anyInt());
        doAnswer(invocation -> {
            visits.add("funds:" + TenantContext.getTenantId() + ":" + invocation.<Integer>getArgument(0));
            return 0;
        }).when(orderBalanceAllocationService).settleEligibleAfterCoolingOff(anyInt());
        doAnswer(invocation -> {
            visits.add("merchant:" + TenantContext.getTenantId() + ":" + invocation.<Integer>getArgument(0));
            return 0;
        }).when(merchantService).releaseEligibleSettlements(anyInt());

        task.settleCoolingOffCommissions();

        assertEquals(List.of("bonus:1:100", "funds:1:100", "merchant:1:100",
                "bonus:2:100", "funds:2:100", "merchant:2:100"), visits);
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void bonusCalculationRoundRobinHasTwentyGlobalSlotsAndRestoresContext() {
        ScheduleTask task = newTask();
        List<DmsTenant> tenants = new ArrayList<>();
        for (long id = 1; id <= 22; id++) tenants.add(tenant(id));
        when(tenantDao.selectAll()).thenReturn(tenants);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("bonus-calculation"), any(), any());
        List<Long> visits = new ArrayList<>();
        doAnswer(invocation -> {
            assertEquals(1, invocation.<Integer>getArgument(0));
            visits.add(TenantContext.getTenantId());
            return 0;
        }).when(bonusCalculationTaskService).processPendingTasks(1);
        TenantContext.setTenantId(77L);

        task.processBonusCalculationTasks();
        assertEquals(20, visits.size());
        task.processBonusCalculationTasks();

        assertEquals(40, visits.size());
        assertEquals(List.of(21L, 22L, 1L), visits.subList(20, 23));
        assertEquals(77L, TenantContext.getCurrentTenantId());
    }
}
