package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsBonusCalculationTaskDao;
import com.macro.mall.distribution.dao.DmsErpSyncTaskDao;
import com.macro.mall.distribution.dao.DmsMerchantLedgerDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsWechatLogisticsFollowTaskDao;
import com.macro.mall.distribution.dao.DmsWechatShippingSyncTaskDao;
import com.macro.mall.distribution.dao.DmsWithdrawalPayoutDao;
import com.macro.mall.distribution.vo.BusinessTaskMetricSnapshot;
import com.macro.mall.distribution.vo.BusinessTimeoutMetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 定期读取业务任务、资金操作和账本的一组聚合值。
 *
 * <p>监控只暴露固定类型标签和聚合数量/年龄，不读取或记录订单号、手机号、运单号等业务标识。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BusinessOperationsMonitor {
    private final DmsWechatShippingSyncTaskDao wechatShippingTaskDao;
    private final DmsWechatLogisticsFollowTaskDao logisticsFollowTaskDao;
    private final DmsBonusCalculationTaskDao bonusCalculationTaskDao;
    private final DmsErpSyncTaskDao erpSyncTaskDao;
    private final DmsShopAfterSaleDao shopAfterSaleDao;
    private final DmsWithdrawalPayoutDao withdrawalPayoutDao;
    private final DmsMerchantLedgerDao merchantLedgerDao;
    private final RuntimeMonitoringMetrics metrics;

    @Value("${business.monitor.refund-timeout-seconds:1800}")
    private long refundTimeoutSeconds;

    @Value("${business.monitor.withdrawal-payout-timeout-seconds:86400}")
    private long withdrawalPayoutTimeoutSeconds;

    @Scheduled(fixedDelayString = "${business.monitor.sample-ms:60000}",
            initialDelayString = "${business.monitor.initial-delay-ms:30000}")
    public void sample() {
        sampleAt(LocalDateTime.now());
    }

    void sampleAt(LocalDateTime now) {
        sampleTask("wechat_shipping_sync", wechatShippingTaskDao::selectMonitoringMetrics, now);
        sampleTask("wechat_logistics_follow", logisticsFollowTaskDao::selectMonitoringMetrics, now);
        sampleTask("bonus_calculation", bonusCalculationTaskDao::selectMonitoringMetrics, now);
        sampleTask("erp_sync", erpSyncTaskDao::selectMonitoringMetrics, now);

        LocalDateTime refundCutoff = now.minusSeconds(nonNegative(refundTimeoutSeconds));
        sampleOperation("refund", () -> shopAfterSaleDao.selectTimedOutRefundMetrics(refundCutoff), now);
        LocalDateTime payoutCutoff = now.minusSeconds(nonNegative(withdrawalPayoutTimeoutSeconds));
        sampleOperation("withdrawal_payout", () -> withdrawalPayoutDao.selectTimedOutMetrics(payoutCutoff), now);
        sampleLedger();
    }

    private void sampleTask(String source, Supplier<BusinessTaskMetricSnapshot> supplier, LocalDateTime now) {
        try {
            BusinessTaskMetricSnapshot snapshot = supplier.get();
            long backlog = snapshot == null || snapshot.getBacklogCount() == null ? 0L : snapshot.getBacklogCount();
            long failures = snapshot == null || snapshot.getFailureCount() == null ? 0L : snapshot.getFailureCount();
            metrics.updateBusinessTask(source, backlog, ageSeconds(snapshot == null ? null : snapshot.getOldestTime(), now), failures);
        } catch (Exception ex) {
            metrics.markBusinessTaskUnavailable(source);
            log.warn("BUSINESS_MONITOR_SAMPLE_FAILED type=task source={} reason={}", source,
                    ex.getClass().getSimpleName());
        }
    }

    private void sampleOperation(String source, Supplier<BusinessTimeoutMetricSnapshot> supplier, LocalDateTime now) {
        try {
            BusinessTimeoutMetricSnapshot snapshot = supplier.get();
            long timedOut = snapshot == null || snapshot.getTimedOutCount() == null ? 0L : snapshot.getTimedOutCount();
            metrics.updateTimedOperation(source, timedOut,
                    ageSeconds(snapshot == null ? null : snapshot.getOldestTime(), now));
        } catch (Exception ex) {
            metrics.markTimedOperationUnavailable(source);
            log.warn("BUSINESS_MONITOR_SAMPLE_FAILED type=operation source={} reason={}", source,
                    ex.getClass().getSimpleName());
        }
    }

    private void sampleLedger() {
        try {
            metrics.updateLedgerMismatches("merchant_balance", merchantLedgerDao.countBalanceMismatches());
        } catch (Exception ex) {
            metrics.markLedgerUnavailable("merchant_balance");
            log.warn("BUSINESS_MONITOR_SAMPLE_FAILED type=ledger source=merchant_balance reason={}",
                    ex.getClass().getSimpleName());
        }
    }

    private long ageSeconds(LocalDateTime oldest, LocalDateTime now) {
        if (oldest == null || now == null || oldest.isAfter(now)) return 0L;
        return Math.max(0L, Duration.between(oldest, now).getSeconds());
    }

    private long nonNegative(long value) {
        return Math.max(0L, value);
    }
}
