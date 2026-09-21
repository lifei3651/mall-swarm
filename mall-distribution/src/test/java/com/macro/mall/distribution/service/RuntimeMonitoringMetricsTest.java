package com.macro.mall.distribution.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMonitoringMetricsTest {

    @Test
    void publishesDatabasePoolCacheAndRedisMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RuntimeMonitoringMetrics metrics = new RuntimeMonitoringMetrics(registry);

        metrics.recordDatabaseQuery("example.Mapper.select", "query", 2_000_000L, true);
        metrics.updateDatabasePool(4, 6, 20, 1);
        metrics.recordCacheRequest("hit");
        metrics.updateRedis(true, 3);

        assertNotNull(registry.find("mall.database.query.duration").timer());
        assertEquals(1D, registry.counter("mall.database.query.slow",
                "statement", "example.Mapper.select").count());
        assertEquals(4D, registry.get("mall.database.pool.active").gauge().value());
        assertEquals(1D, registry.counter("mall.cache.requests",
                "cache", "shop_catalog", "result", "hit").count());
        assertEquals(1D, registry.get("mall.cache.redis.available").gauge().value());
        assertEquals(3D, registry.get("mall.cache.redis.ping").gauge().value());
    }

    @Test
    void publishesOnlyFixedLabelBusinessAggregatesAndAvailability() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RuntimeMonitoringMetrics metrics = new RuntimeMonitoringMetrics(registry);

        assertEquals(-1D, gauge(registry, "mall.business.task.backlog", "task", "wechat_shipping_sync"));
        metrics.updateBusinessTask("wechat_shipping_sync", 4, 125, 2);
        metrics.updateTimedOperation("refund", 3, 3600);
        metrics.updateLedgerMismatches("merchant_balance", 1);

        assertEquals(4D, gauge(registry, "mall.business.task.backlog", "task", "wechat_shipping_sync"));
        assertEquals(125D, gauge(registry, "mall.business.task.oldest.age.seconds", "task", "wechat_shipping_sync"));
        assertEquals(2D, gauge(registry, "mall.business.task.failures", "task", "wechat_shipping_sync"));
        assertEquals(1D, gauge(registry, "mall.business.monitor.available", "source", "wechat_shipping_sync"));
        assertEquals(3D, gauge(registry, "mall.business.operation.timed.out", "operation", "refund"));
        assertEquals(3600D, gauge(registry, "mall.business.operation.oldest.age.seconds", "operation", "refund"));
        assertEquals(1D, gauge(registry, "mall.business.ledger.mismatches", "ledger", "merchant_balance"));

        metrics.markBusinessTaskUnavailable("wechat_shipping_sync");
        assertEquals(-1D, gauge(registry, "mall.business.task.backlog", "task", "wechat_shipping_sync"));
        assertEquals(0D, gauge(registry, "mall.business.monitor.available", "source", "wechat_shipping_sync"));
        assertThrows(IllegalArgumentException.class,
                () -> metrics.updateBusinessTask("order-or-phone-derived-label", 1, 1, 1));
    }

    @Test
    void prometheusNamesMatchAlertRulesWithoutBusinessIdentifiers() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        RuntimeMonitoringMetrics metrics = new RuntimeMonitoringMetrics(registry);
        metrics.updateBusinessTask("wechat_shipping_sync", 4, 125, 2);
        metrics.updateTimedOperation("refund", 3, 3600);
        metrics.updateLedgerMismatches("merchant_balance", 1);

        String scrape = registry.scrape();

        assertTrue(scrape.contains("mall_business_task_backlog{task=\"wechat_shipping_sync\"} 4.0"));
        assertTrue(scrape.contains("mall_business_operation_timed_out{operation=\"refund\"} 3.0"));
        assertTrue(scrape.contains("mall_business_ledger_mismatches{ledger=\"merchant_balance\"} 1.0"));
        assertFalse(scrape.contains("order_no"));
        assertFalse(scrape.contains("phone"));
    }

    private double gauge(SimpleMeterRegistry registry, String name, String tag, String value) {
        return registry.get(name).tag(tag, value).gauge().value();
    }
}
