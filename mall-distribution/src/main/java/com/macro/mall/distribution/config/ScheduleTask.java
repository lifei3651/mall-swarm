package com.macro.mall.distribution.config;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.service.BonusCalculationTaskService;
import com.macro.mall.distribution.service.ErpIntegrationService;
import com.macro.mall.distribution.service.CommissionSettlementService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.MerchantService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.WeChatPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ScheduleTask {

    private final PerformanceService performanceService;
    private final BonusCalculationTaskService bonusCalculationTaskService;
    private final ErpIntegrationService erpIntegrationService;
    private final CommissionSettlementService commissionSettlementService;
    private final ShopService shopService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;
    private final OperationLogService operationLogService;
    private final MerchantService merchantService;
    private final ShopAfterSaleService shopAfterSaleService;
    private final WeChatPayService weChatPayService;
    private final DistributedScheduledTaskRunner scheduledTaskRunner;
    private final DmsTenantDao tenantDao;
    private final Map<String, Integer> tenantOffsets = new HashMap<>();

    /** 每分钟关闭超时待支付订单并原子返还商品及SKU库存。 */
    @Scheduled(fixedDelayString = "${shop.order.pending-scan-interval-ms:60000}")
    public void closeExpiredPendingOrders() {
        scheduledTaskRunner.run("close-expired-orders", Duration.ofMinutes(5), () -> {
            try {
                scanTenants("close-expired-orders", 200, (tenantId, limit) -> {
                    int count = shopService.closeExpiredPendingOrders(limit);
                    if (count > 0) log.info("超时待支付订单已关闭并返还库存: tenantId={}, count={}", tenantId, count);
                });
            } catch (Exception e) {
                log.error("超时待支付订单扫描失败", e);
            }
        });
    }

    /** 每10分钟确认发货已满保护期且没有处理中售后的订单，避免订单永久停留在待收货。 */
    @Scheduled(fixedDelayString = "${shop.order.auto-receive-scan-interval-ms:600000}")
    public void autoConfirmExpiredShippedOrders() {
        scheduledTaskRunner.run("auto-confirm-receipt", Duration.ofMinutes(30), () -> {
            try {
                scanTenants("auto-confirm-receipt", 200, (tenantId, limit) -> {
                    int count = shopService.autoConfirmExpiredShippedOrders(limit);
                    if (count > 0) log.info("到期订单已自动确认收货: tenantId={}, count={}", tenantId, count);
                });
            } catch (Exception e) {
                log.error("到期订单自动确认收货扫描失败", e);
            }
        });
    }

    /** 定时关闭已同意退货但客户长期未寄回的售后，避免订单和结算永久悬挂。 */
    @Scheduled(fixedDelayString = "${shop.after-sale.return-shipment-scan-interval-ms:600000}")
    public void closeExpiredWaitingReturns() {
        scheduledTaskRunner.run("close-expired-waiting-returns", Duration.ofMinutes(30), () -> {
            try {
                scanTenants("close-expired-waiting-returns", 200, (tenantId, limit) -> {
                    int count = shopAfterSaleService.expireWaitingReturnShipments(limit);
                    if (count > 0) log.info("超时未寄回售后已自动关闭: tenantId={}, count={}", tenantId, count);
                });
            } catch (Exception e) {
                log.error("超时未寄回售后扫描失败", e);
            }
        });
    }

    /** 每5分钟以同一退款号核对支付宝/微信已受理、但本地仍停在处理中的退款。 */
    @Scheduled(fixedDelayString = "${shop.after-sale.refund-reconcile-interval-ms:300000}",
            initialDelayString = "${shop.after-sale.refund-reconcile-initial-delay-ms:15000}")
    public void reconcileProcessingExternalRefunds() {
        // Preserve the distributed lock key while rolling from the WeChat-only scanner:
        // old and new nodes must not process the same refund in parallel.
        scheduledTaskRunner.run("reconcile-processing-wechat-refunds", Duration.ofMinutes(4), () -> {
            try {
                scanTenants("reconcile-processing-wechat-refunds", 50, (tenantId, limit) -> {
                    int completed = shopAfterSaleService.reconcileProcessingExternalRefunds(limit);
                    if (completed > 0) log.info("外部渠道退款状态自动恢复完成: tenantId={}, count={}", tenantId, completed);
                });
            } catch (Exception e) {
                log.error("外部渠道退款状态自动核对失败", e);
            }
        });
    }

    @FunctionalInterface
    private interface TenantBatch {
        void run(Long tenantId, int limit);
    }

    /** Keep one global attempt budget per scanner and rotate when there are more tenants than slots. */
    private void scanTenants(String scanner, int totalLimit, TenantBatch batch) {
        if (totalLimit <= 0) return;
        List<DmsTenant> allTenants = tenantDao.selectAll();
        List<Long> tenantIds = (allTenants == null ? List.<DmsTenant>of() : allTenants).stream()
                .filter(tenant -> tenant != null && tenant.getId() != null)
                .map(DmsTenant::getId).distinct().sorted().toList();
        if (tenantIds.isEmpty()) return;
        int tenantCount = Math.min(tenantIds.size(), totalLimit);
        int start;
        synchronized (tenantOffsets) {
            start = Math.floorMod(tenantOffsets.getOrDefault(scanner, 0), tenantIds.size());
            tenantOffsets.put(scanner, (start + tenantCount) % tenantIds.size());
        }
        int baseLimit = totalLimit / tenantCount;
        int extra = totalLimit % tenantCount;
        Long previousTenantId = TenantContext.getCurrentTenantId();
        try {
            for (int i = 0; i < tenantCount; i++) {
                Long tenantId = tenantIds.get((start + i) % tenantIds.size());
                int tenantLimit = baseLimit + (i < extra ? 1 : 0);
                TenantContext.setTenantId(tenantId);
                try {
                    batch.run(tenantId, tenantLimit);
                } catch (Exception error) {
                    log.warn("定时扫描未完成: scanner={}, tenantId={}, errorType={}", scanner, tenantId,
                            error.getClass().getSimpleName());
                }
            }
        } finally {
            if (previousTenantId == null) TenantContext.clear();
            else TenantContext.setTenantId(previousTenantId);
        }
    }

    /** 持久化恢复超时关单后才到账、但退款回调丢失或渠道仍处理中的微信支付。 */
    @Scheduled(fixedDelayString = "${shop.order.late-payment-refund-reconcile-interval-ms:300000}",
            initialDelayString = "${shop.order.late-payment-refund-reconcile-initial-delay-ms:30000}")
    public void reconcileProcessingLatePaymentRefunds() {
        scheduledTaskRunner.run("reconcile-late-payment-refunds", Duration.ofMinutes(4), () -> {
            try {
                int count = weChatPayService.reconcileProcessingLatePaymentRefunds(50);
                if (count > 0) log.info("微信迟到支付退款自动恢复完成: count={}", count);
            } catch (Exception e) {
                log.error("微信迟到支付退款自动核对失败", e);
            }
        });
    }

    /** 到期自动确认替换商品收货，避免会员未操作导致换货与结算永久悬挂。 */
    @Scheduled(fixedDelayString = "${shop.after-sale.exchange-receipt-scan-interval-ms:600000}")
    public void autoCompleteExpiredExchangeReceipts() {
        scheduledTaskRunner.run("auto-complete-exchange-receipts", Duration.ofMinutes(30), () -> {
            try {
                scanTenants("auto-complete-exchange-receipts", 200, (tenantId, limit) -> {
                    int count = shopAfterSaleService.autoCompleteExpiredExchangeReceipts(limit);
                    if (count > 0) log.info("到期换货已自动确认收货: tenantId={}, count={}", tenantId, count);
                });
            } catch (Exception e) {
                log.error("到期换货自动确认收货扫描失败", e);
            }
        });
    }

    /** 每10分钟扫描：订单确认收货满7天且没有待处理售后的奖金自动结算。 */
    @Scheduled(fixedDelayString = "${bonus.settlement.scan-interval-ms:600000}")
    public void settleCoolingOffCommissions() {
        scheduledTaskRunner.run("cooling-off-settlement", Duration.ofMinutes(30), () -> {
            try {
                scanTenants("cooling-off-settlement", 200, (tenantId, limit) -> {
                    try {
                        int count = commissionSettlementService.settleEligibleAfterCoolingOff(limit);
                        if (count > 0) log.info("T+7奖金自动结算完成: tenantId={}, count={}", tenantId, count);
                    } catch (Exception error) {
                        log.warn("奖金自动结算未完成: tenantId={}, errorType={}", tenantId, error.getClass().getSimpleName());
                    }
                    try {
                        int count = orderBalanceAllocationService.settleEligibleAfterCoolingOff(limit);
                        if (count > 0) log.info("售后期结束后的平台资金自动进入余额: tenantId={}, count={}", tenantId, count);
                    } catch (Exception error) {
                        log.warn("平台资金归集未完成: tenantId={}, errorType={}", tenantId, error.getClass().getSimpleName());
                    }
                    try {
                        int count = merchantService.releaseEligibleSettlements(limit);
                        if (count > 0) log.info("售后期结束后的商户货款转为可提现: tenantId={}, count={}", tenantId, count);
                    } catch (Exception error) {
                        log.warn("商户货款释放未完成: tenantId={}, errorType={}", tenantId, error.getClass().getSimpleName());
                    }
                });
            } catch (Exception e) {
                log.error("售后期结束后的资金结算扫描失败", e);
            }
        });
    }

    /** 每分钟重试失败的ERP推单；具体外部调用仅在已启用且完成授权的集成上发生。 */
    @Scheduled(fixedDelayString = "${erp.sync.scan-interval-ms:60000}")
    public void retryErpTasks() {
        scheduledTaskRunner.run("erp-push-retry", Duration.ofMinutes(5), () -> {
            try { erpIntegrationService.retryPendingTasks(20); } catch (Exception e) { log.error("重试ERP推单失败", e); }
        });
    }

    /**
     * 每5秒处理一批待计算奖金任务
     */
    @Scheduled(fixedDelayString = "${bonus.calculation.scan-interval-ms:5000}")
    public void processBonusCalculationTasks() {
        scheduledTaskRunner.run("bonus-calculation", Duration.ofMinutes(2), () -> {
            try {
                scanTenants("bonus-calculation", 20, (tenantId, limit) -> {
                    int count = bonusCalculationTaskService.processPendingTasks(limit);
                    if (count > 0) log.info("处理奖金异步计算任务完成: tenantId={}, count={}", tenantId, count);
                });
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
