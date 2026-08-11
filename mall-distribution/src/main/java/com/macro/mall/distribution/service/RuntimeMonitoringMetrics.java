package com.macro.mall.distribution.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQL、数据库连接池和商城公共缓存的统一运行指标。
 * 指标不记录 SQL 参数、缓存键、会员信息或订单内容。
 */
@Service
public class RuntimeMonitoringMetrics {
    private final MeterRegistry registry;
    private final AtomicLong poolActive = new AtomicLong();
    private final AtomicLong poolIdle = new AtomicLong();
    private final AtomicLong poolMax = new AtomicLong();
    private final AtomicLong poolWaiting = new AtomicLong();
    private final AtomicLong redisAvailable = new AtomicLong(-1L);
    private final AtomicLong redisPingMillis = new AtomicLong(-1L);

    public RuntimeMonitoringMetrics(MeterRegistry registry) {
        this.registry = registry;
        gauge("mall.database.pool.active", poolActive, "数据库连接池活跃连接数");
        gauge("mall.database.pool.idle", poolIdle, "数据库连接池空闲连接数");
        gauge("mall.database.pool.max", poolMax, "数据库连接池最大连接数");
        gauge("mall.database.pool.waiting", poolWaiting, "等待数据库连接的线程数");
        gauge("mall.cache.redis.available", redisAvailable, "Redis 可用状态：1可用、0不可用、-1尚未检查");
        gauge("mall.cache.redis.ping", redisPingMillis, "Redis PING 延迟（毫秒），不可用时为-1");
    }

    public void recordDatabaseQuery(String statementId, String operation, long elapsedNanos, boolean slow) {
        String safeStatement = statementId == null || statementId.isBlank() ? "unknown" : statementId;
        String safeOperation = operation == null || operation.isBlank() ? "unknown" : operation;
        Timer.builder("mall.database.query.duration")
                .description("MyBatis Mapper 执行耗时")
                .tags("statement", safeStatement, "operation", safeOperation)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry)
                .record(Math.max(0L, elapsedNanos), TimeUnit.NANOSECONDS);
        if (slow) {
            registry.counter("mall.database.query.slow", "statement", safeStatement).increment();
        }
    }

    public void updateDatabasePool(long active, long idle, long max, long waiting) {
        poolActive.set(Math.max(0L, active));
        poolIdle.set(Math.max(0L, idle));
        poolMax.set(Math.max(0L, max));
        poolWaiting.set(Math.max(0L, waiting));
    }

    public void recordCacheRequest(String result) {
        registry.counter("mall.cache.requests", "cache", "shop_catalog", "result", normalize(result)).increment();
    }

    public void recordCacheOperation(String operation, String result) {
        registry.counter("mall.cache.operations", "cache", "shop_catalog",
                "operation", normalize(operation), "result", normalize(result)).increment();
    }

    public void updateRedis(boolean available, long pingMillis) {
        redisAvailable.set(available ? 1L : 0L);
        redisPingMillis.set(available ? Math.max(0L, pingMillis) : -1L);
    }

    private void gauge(String name, AtomicLong value, String description) {
        Gauge.builder(name, value, AtomicLong::doubleValue)
                .description(description)
                .register(registry);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }
}
