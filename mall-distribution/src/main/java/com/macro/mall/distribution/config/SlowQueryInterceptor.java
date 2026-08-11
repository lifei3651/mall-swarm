package com.macro.mall.distribution.config;

import com.macro.mall.distribution.service.RuntimeMonitoringMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** 记录慢 Mapper 调用标识和耗时，不输出 SQL 参数或业务敏感数据。 */
@Slf4j
@Component
@RequiredArgsConstructor
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class SlowQueryInterceptor implements Interceptor {
    private final RuntimeMonitoringMetrics metrics;

    @Value("${database.monitor.slow-query-ms:1000}")
    private long slowQueryMillis;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long started = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - started;
            long elapsed = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
            Object[] args = invocation.getArgs();
            String statementId = args.length > 0 && args[0] instanceof MappedStatement statement
                    ? statement.getId() : "unknown";
            boolean slow = elapsed >= Math.max(1L, slowQueryMillis);
            metrics.recordDatabaseQuery(statementId, invocation.getMethod().getName(), elapsedNanos, slow);
            if (slow) {
                log.warn("DB_SLOW_QUERY statement={} elapsedMs={} thresholdMs={}", statementId, elapsed, slowQueryMillis);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof Executor ? Plugin.wrap(target, this) : target;
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
