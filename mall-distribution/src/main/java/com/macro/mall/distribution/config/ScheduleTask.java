package com.macro.mall.distribution.config;

import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.service.BonusCalculationTaskService;
import com.macro.mall.distribution.service.ErpIntegrationService;
import com.macro.mall.distribution.service.CommissionSettlementService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleTask {

    private final PerformanceService performanceService;
    private final BonusCalculationTaskService bonusCalculationTaskService;
    private final ErpIntegrationService erpIntegrationService;
    private final CommissionSettlementService commissionSettlementService;
    private final ShopService shopService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;
    private final OperationLogService operationLogService;
    private final DistributedScheduledTaskRunner scheduledTaskRunner;

    /** 每分钟关闭超时待支付订单并原子返还商品及SKU库存。 */
    @Scheduled(fixedDelayString = "${shop.order.pending-scan-interval-ms:60000}")
    public void closeExpiredPendingOrders() {
        scheduledTaskRunner.run("close-expired-orders", Duration.ofMinutes(5), () -> {
            try {
                int count = shopService.closeExpiredPendingOrders(200);
                if (count > 0) log.info("超时待支付订单已关闭并返还库存: count={}", count);
            } catch (Exception e) {
                log.error("超时待支付订单扫描失败", e);
            }
        });
    }

    /** 每10分钟扫描：订单确认收货满7天且没有待处理售后的奖金自动结算。 */
    @Scheduled(fixedDelayString = "${bonus.settlement.scan-interval-ms:600000}")
    public void settleCoolingOffCommissions() {
        scheduledTaskRunner.run("cooling-off-settlement", Duration.ofMinutes(30), () -> {
            try {
                int count = commissionSettlementService.settleEligibleAfterCoolingOff(200);
                if (count > 0) log.info("T+7奖金自动结算完成: count={}", count);
                int allocationCount = orderBalanceAllocationService.settleEligibleAfterCoolingOff(200);
                if (allocationCount > 0) log.info("T+7订单成本及剩余商品款自动进入余额: count={}", allocationCount);
            } catch (Exception e) {
                log.error("T+7订单资金自动结算扫描失败", e);
            }
        });
    }

    /** 每分钟重试失败的ERP推单；具体外部调用仅在已启用且完成授权的集成上发生。 */
    @Scheduled(fixedDelay = 60000)
    public void retryErpTasks() {
        scheduledTaskRunner.run("erp-push-retry", Duration.ofMinutes(5), () -> {
            try { erpIntegrationService.retryPendingTasks(20); } catch (Exception e) { log.error("重试ERP推单失败", e); }
        });
    }

    /**
     * 每5秒处理一批待计算奖金任务
     */
    @Scheduled(fixedDelay = 5000)
    public void processBonusCalculationTasks() {
        scheduledTaskRunner.run("bonus-calculation", Duration.ofMinutes(2), () -> {
            try {
                int count = bonusCalculationTaskService.processPendingTasks(20);
                if (count > 0) {
                    log.info("处理奖金异步计算任务完成: count={}", count);
                }
            } catch (Exception e) {
                log.error("处理奖金异步计算任务失败", e);
            }
        });
    }

    /**
     * 每天凌晨1点刷新前一天的业绩汇总
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void refreshDailySummary() {
        scheduledTaskRunner.run("daily-performance-summary", Duration.ofHours(2), () -> {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            log.info("开始刷新{}业绩汇总", yesterday);
            try {
                performanceService.refreshDailySummary(yesterday);
                log.info("刷新完成");
            } catch (Exception e) {
                log.error("刷新失败", e);
            }
        });
    }

    /**
     * 每月1号凌晨2点刷新上月的月度汇总
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void refreshMonthlySummary() {
        scheduledTaskRunner.run("monthly-performance-summary", Duration.ofHours(4), () -> {
            LocalDate lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            log.info("开始刷新{}月度业绩汇总", lastMonth);
            try {
                performanceService.refreshMonthlySummary(lastMonth);
                log.info("刷新完成");
            } catch (Exception e) {
                log.error("刷新失败", e);
            }
        });
    }

    /** 每天凌晨3:30分批清理超过保留期限的后台操作日志，默认保留365天且最低90天。 */
    @Scheduled(cron = "${operation-log.cleanup-cron:0 30 3 * * ?}")
    public void cleanupExpiredOperationLogs() {
        scheduledTaskRunner.run("operation-log-cleanup", Duration.ofHours(2), () -> {
            try {
                operationLogService.cleanupExpiredLogs(5000, 20);
            } catch (Exception e) {
                log.error("清理过期后台操作日志失败", e);
            }
        });
    }
}
