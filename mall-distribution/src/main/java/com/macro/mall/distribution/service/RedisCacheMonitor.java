package com.macro.mall.distribution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** 定期采集 Redis 可用状态和基础延迟，不读写商城业务数据。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheMonitor {
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final RuntimeMonitoringMetrics metrics;

    @Scheduled(fixedDelayString = "${cache.monitor.sample-ms:15000}",
            initialDelayString = "${cache.monitor.initial-delay-ms:60000}")
    public void sample() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            metrics.updateRedis(false, -1L);
            return;
        }
        long started = System.nanoTime();
        try {
            String response = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            boolean available = response != null && "PONG".equalsIgnoreCase(response);
            metrics.updateRedis(available, elapsed);
            if (!available) log.warn("CACHE_REDIS_UNAVAILABLE reason=unexpected_ping_response");
        } catch (Exception ex) {
            metrics.updateRedis(false, -1L);
            log.warn("CACHE_REDIS_UNAVAILABLE reason={}", ex.getClass().getSimpleName());
        }
    }
}
