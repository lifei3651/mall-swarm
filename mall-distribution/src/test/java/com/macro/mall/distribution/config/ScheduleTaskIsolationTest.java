package com.macro.mall.distribution.config;

import com.macro.mall.distribution.service.BonusCalculationTaskService;
import com.macro.mall.distribution.service.CommissionSettlementService;
import com.macro.mall.distribution.service.ErpIntegrationService;
import com.macro.mall.distribution.service.MerchantService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.ShopService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
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
    @Mock private DistributedScheduledTaskRunner scheduledTaskRunner;

    @Test
    void commissionFailureDoesNotSkipBalanceAndMerchantSettlement() {
        ScheduleTask task = new ScheduleTask(performanceService, bonusCalculationTaskService,
                erpIntegrationService, commissionSettlementService, shopService,
                orderBalanceAllocationService, operationLogService, merchantService,
                shopAfterSaleService, scheduledTaskRunner);
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
        ScheduleTask task = new ScheduleTask(performanceService, bonusCalculationTaskService,
                erpIntegrationService, commissionSettlementService, shopService,
                orderBalanceAllocationService, operationLogService, merchantService,
                shopAfterSaleService, scheduledTaskRunner);
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
        ScheduleTask task = new ScheduleTask(performanceService, bonusCalculationTaskService,
                erpIntegrationService, commissionSettlementService, shopService,
                orderBalanceAllocationService, operationLogService, merchantService,
                shopAfterSaleService, scheduledTaskRunner);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(scheduledTaskRunner).run(eq("auto-complete-exchange-receipts"), any(), any());
        when(shopAfterSaleService.autoCompleteExpiredExchangeReceipts(200)).thenReturn(1);

        task.autoCompleteExpiredExchangeReceipts();

        verify(shopAfterSaleService).autoCompleteExpiredExchangeReceipts(200);
    }
}
