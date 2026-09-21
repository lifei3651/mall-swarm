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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessOperationsMonitorTest {
    private final DmsWechatShippingSyncTaskDao shippingDao = mock(DmsWechatShippingSyncTaskDao.class);
    private final DmsWechatLogisticsFollowTaskDao logisticsDao = mock(DmsWechatLogisticsFollowTaskDao.class);
    private final DmsBonusCalculationTaskDao bonusDao = mock(DmsBonusCalculationTaskDao.class);
    private final DmsErpSyncTaskDao erpDao = mock(DmsErpSyncTaskDao.class);
    private final DmsShopAfterSaleDao afterSaleDao = mock(DmsShopAfterSaleDao.class);
    private final DmsWithdrawalPayoutDao payoutDao = mock(DmsWithdrawalPayoutDao.class);
    private final DmsMerchantLedgerDao ledgerDao = mock(DmsMerchantLedgerDao.class);
    private SimpleMeterRegistry registry;
    private BusinessOperationsMonitor monitor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        RuntimeMonitoringMetrics metrics = new RuntimeMonitoringMetrics(registry);
        monitor = new BusinessOperationsMonitor(shippingDao, logisticsDao, bonusDao, erpDao,
                afterSaleDao, payoutDao, ledgerDao, metrics);
        ReflectionTestUtils.setField(monitor, "refundTimeoutSeconds", 1800L);
        ReflectionTestUtils.setField(monitor, "withdrawalPayoutTimeoutSeconds", 86400L);
    }

    @Test
    void samplesBacklogsTimeoutsAndLedgerWithoutBusinessIdentifiers() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);
        when(shippingDao.selectMonitoringMetrics()).thenReturn(task(3, now.minusMinutes(12), 1));
        when(logisticsDao.selectMonitoringMetrics()).thenReturn(task(2, now.minusMinutes(3), 0));
        when(bonusDao.selectMonitoringMetrics()).thenReturn(task(4, now.minusSeconds(45), 2));
        when(erpDao.selectMonitoringMetrics()).thenReturn(task(1, now.minusHours(2), 0));
        when(afterSaleDao.selectTimedOutRefundMetrics(eq(now.minusMinutes(30))))
                .thenReturn(timeout(2, now.minusHours(3)));
        when(payoutDao.selectTimedOutMetrics(eq(now.minusDays(1))))
                .thenReturn(timeout(1, now.minusDays(2)));
        when(ledgerDao.countBalanceMismatches()).thenReturn(5L);

        monitor.sampleAt(now);

        assertEquals(3D, gauge("mall.business.task.backlog", "task", "wechat_shipping_sync"));
        assertEquals(720D, gauge("mall.business.task.oldest.age.seconds", "task", "wechat_shipping_sync"));
        assertEquals(1D, gauge("mall.business.task.failures", "task", "wechat_shipping_sync"));
        assertEquals(2D, gauge("mall.business.operation.timed.out", "operation", "refund"));
        assertEquals(10_800D, gauge("mall.business.operation.oldest.age.seconds", "operation", "refund"));
        assertEquals(1D, gauge("mall.business.operation.timed.out", "operation", "withdrawal_payout"));
        assertEquals(5D, gauge("mall.business.ledger.mismatches", "ledger", "merchant_balance"));
        assertEquals(1D, gauge("mall.business.monitor.available", "source", "merchant_balance"));
        verify(afterSaleDao).selectTimedOutRefundMetrics(now.minusMinutes(30));
        verify(payoutDao).selectTimedOutMetrics(now.minusDays(1));
    }

    @Test
    void isolatesOneFailedSourceAndMarksItUnavailable() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 12, 0);
        when(shippingDao.selectMonitoringMetrics()).thenThrow(new IllegalStateException("contains-sensitive-detail"));
        when(logisticsDao.selectMonitoringMetrics()).thenReturn(task(2, now.minusMinutes(2), 0));

        monitor.sampleAt(now);

        assertEquals(-1D, gauge("mall.business.task.backlog", "task", "wechat_shipping_sync"));
        assertEquals(0D, gauge("mall.business.monitor.available", "source", "wechat_shipping_sync"));
        assertEquals(2D, gauge("mall.business.task.backlog", "task", "wechat_logistics_follow"));
        assertEquals(1D, gauge("mall.business.monitor.available", "source", "wechat_logistics_follow"));
    }

    private BusinessTaskMetricSnapshot task(long backlog, LocalDateTime oldest, long failures) {
        BusinessTaskMetricSnapshot snapshot = new BusinessTaskMetricSnapshot();
        snapshot.setBacklogCount(backlog);
        snapshot.setOldestTime(oldest);
        snapshot.setFailureCount(failures);
        return snapshot;
    }

    private BusinessTimeoutMetricSnapshot timeout(long count, LocalDateTime oldest) {
        BusinessTimeoutMetricSnapshot snapshot = new BusinessTimeoutMetricSnapshot();
        snapshot.setTimedOutCount(count);
        snapshot.setOldestTime(oldest);
        return snapshot;
    }

    private double gauge(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).gauge().value();
    }
}
