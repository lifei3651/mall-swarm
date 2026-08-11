package com.macro.mall.distribution.config;

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
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class SlowQueryInterceptor implements Interceptor {
    @Value("${database.monitor.slow-query-ms:1000}")
    private long slowQueryMillis;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long started = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (elapsed >= Math.max(1L, slowQueryMillis)) {
                Object[] args = invocation.getArgs();
                String statementId = args.length > 0 && args[0] instanceof MappedStatement statement
                        ? statement.getId() : "unknown";
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
