package com.macro.mall.distribution.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
