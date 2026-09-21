package com.macro.mall.distribution.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQL、数据库连接池、商城公共缓存和业务聚合状态的统一运行指标。
 * 指标不记录 SQL 参数、缓存键、会员信息或订单内容。
 */
@Service
public class RuntimeMonitoringMetrics {
    static final List<String> BUSINESS_TASKS = List.of(
            "wechat_shipping_sync", "wechat_logistics_follow", "bonus_calculation", "erp_sync");
    static final List<String> TIMED_OPERATIONS = List.of("refund", "withdrawal_payout");
    static final List<String> BUSINESS_LEDGERS = List.of("merchant_balance");

    private final MeterRegistry registry;
    private final AtomicLong poolActive = new AtomicLong();
    private final AtomicLong poolIdle = new AtomicLong();
    private final AtomicLong poolMax = new AtomicLong();
    private final AtomicLong poolWaiting = new AtomicLong();
    private final AtomicLong redisAvailable = new AtomicLong(-1L);
    private final AtomicLong redisPingMillis = new AtomicLong(-1L);
    private final Map<String, AtomicLong> taskBacklog = new LinkedHashMap<>();
    private final Map<String, AtomicLong> taskOldestAgeSeconds = new LinkedHashMap<>();
    private final Map<String, AtomicLong> taskFailures = new LinkedHashMap<>();
    private final Map<String, AtomicLong> operationTimedOut = new LinkedHashMap<>();
    private final Map<String, AtomicLong> operationOldestAgeSeconds = new LinkedHashMap<>();
    private final Map<String, AtomicLong> ledgerMismatches = new LinkedHashMap<>();
    private final Map<String, AtomicLong> businessMonitorAvailable = new LinkedHashMap<>();

    public RuntimeMonitoringMetrics(MeterRegistry registry) {
        this.registry = registry;
        gauge("mall.database.pool.active", poolActive, "数据库连接池活跃连接数");
        gauge("mall.database.pool.idle", poolIdle, "数据库连接池空闲连接数");
        gauge("mall.database.pool.max", poolMax, "数据库连接池最大连接数");
        gauge("mall.database.pool.waiting", poolWaiting, "等待数据库连接的线程数");
        gauge("mall.cache.redis.available", redisAvailable, "Redis 可用状态：1可用、0不可用、-1尚未检查");
        gauge("mall.cache.redis.ping", redisPingMillis, "Redis PING 延迟（毫秒），不可用时为-1");
        BUSINESS_TASKS.forEach(task -> registerTaskMetrics(task));
        TIMED_OPERATIONS.forEach(operation -> registerOperationMetrics(operation));
        BUSINESS_LEDGERS.forEach(ledger -> registerLedgerMetrics(ledger));
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

    /**
     * 更新固定任务类型的聚合快照。任务标签只允许代码内的固定集合，避免订单号、手机号等高基数或隐私标签进入监控。
     */
    public void updateBusinessTask(String task, long backlog, long oldestAgeSeconds, long failures) {
        taskBacklog.get(requireKnown(taskBacklog, task, "task")).set(nonNegative(backlog));
        taskOldestAgeSeconds.get(task).set(nonNegative(oldestAgeSeconds));
        taskFailures.get(task).set(nonNegative(failures));
        availability("task", task).set(1L);
    }

    public void markBusinessTaskUnavailable(String task) {
        taskBacklog.get(requireKnown(taskBacklog, task, "task")).set(-1L);
        taskOldestAgeSeconds.get(task).set(-1L);
        taskFailures.get(task).set(-1L);
        availability("task", task).set(0L);
    }

    public void updateTimedOperation(String operation, long timedOut, long oldestAgeSeconds) {
        operationTimedOut.get(requireKnown(operationTimedOut, operation, "operation")).set(nonNegative(timedOut));
        operationOldestAgeSeconds.get(operation).set(nonNegative(oldestAgeSeconds));
        availability("operation", operation).set(1L);
    }

    public void markTimedOperationUnavailable(String operation) {
        operationTimedOut.get(requireKnown(operationTimedOut, operation, "operation")).set(-1L);
        operationOldestAgeSeconds.get(operation).set(-1L);
        availability("operation", operation).set(0L);
    }

    public void updateLedgerMismatches(String ledger, long mismatches) {
        ledgerMismatches.get(requireKnown(ledgerMismatches, ledger, "ledger")).set(nonNegative(mismatches));
        availability("ledger", ledger).set(1L);
    }

    public void markLedgerUnavailable(String ledger) {
        ledgerMismatches.get(requireKnown(ledgerMismatches, ledger, "ledger")).set(-1L);
        availability("ledger", ledger).set(0L);
    }

    private void registerTaskMetrics(String task) {
        AtomicLong backlog = new AtomicLong(-1L);
        AtomicLong age = new AtomicLong(-1L);
        AtomicLong failures = new AtomicLong(-1L);
        taskBacklog.put(task, backlog);
        taskOldestAgeSeconds.put(task, age);
        taskFailures.put(task, failures);
        gauge("mall.business.task.backlog", backlog, "待执行或执行中的业务任务数", "task", task);
        gauge("mall.business.task.oldest.age.seconds", age, "最老待处理业务任务年龄（秒）", "task", task);
        gauge("mall.business.task.failures", failures, "已进入终态且需要处理的业务任务数", "task", task);
        registerAvailability("task", task);
    }

    private void registerOperationMetrics(String operation) {
        AtomicLong timedOut = new AtomicLong(-1L);
        AtomicLong age = new AtomicLong(-1L);
        operationTimedOut.put(operation, timedOut);
        operationOldestAgeSeconds.put(operation, age);
        gauge("mall.business.operation.timed.out", timedOut, "处理中已超过阈值的业务操作数",
                "operation", operation);
        gauge("mall.business.operation.oldest.age.seconds", age, "最老超时业务操作年龄（秒）",
                "operation", operation);
        registerAvailability("operation", operation);
    }

    private void registerLedgerMetrics(String ledger) {
        AtomicLong mismatches = new AtomicLong(-1L);
        ledgerMismatches.put(ledger, mismatches);
        gauge("mall.business.ledger.mismatches", mismatches, "账户余额与最新账本结余不一致的账户数",
                "ledger", ledger);
        registerAvailability("ledger", ledger);
    }

    private void registerAvailability(String type, String source) {
        AtomicLong available = new AtomicLong(-1L);
        businessMonitorAvailable.put(availabilityKey(type, source), available);
        gauge("mall.business.monitor.available", available, "业务聚合监控采样状态：1成功、0失败、-1尚未采样",
                "type", type, "source", source);
    }

    private AtomicLong availability(String type, String source) {
        AtomicLong value = businessMonitorAvailable.get(availabilityKey(type, source));
        if (value == null) throw new IllegalArgumentException("unknown business monitor source");
        return value;
    }

    private String availabilityKey(String type, String source) {
        return type + ':' + source;
    }

    private long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private String requireKnown(Map<String, AtomicLong> values, String key, String type) {
        if (!values.containsKey(key)) throw new IllegalArgumentException("unknown business " + type);
        return key;
    }

    private void gauge(String name, AtomicLong value, String description) {
        gauge(name, value, description, new String[0]);
    }

    private void gauge(String name, AtomicLong value, String description, String... tags) {
        Gauge.builder(name, value, AtomicLong::doubleValue)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }
}
