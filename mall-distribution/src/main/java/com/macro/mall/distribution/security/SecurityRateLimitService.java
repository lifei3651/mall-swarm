package com.macro.mall.distribution.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-backed fixed-window limiter with a local fail-safe fallback.
 *
 * <p>The Nginx gateway remains the first line of defence. This service protects
 * the application as well when a future deployment changes or bypasses that
 * gateway. A Redis outage must not silently remove all throttling, therefore a
 * process-local counter is used until Redis recovers.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityRateLimitService {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]); "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return current;",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalWindow> fallbackWindows = new ConcurrentHashMap<>();
    private volatile long lastFallbackWarningAt;

    public boolean tryAcquire(String key, int maximumRequests, int windowSeconds) {
        try {
            Long current = redisTemplate.execute(INCREMENT_SCRIPT, Collections.singletonList(key),
                    String.valueOf(windowSeconds));
            if (current != null) {
                return current <= maximumRequests;
            }
        } catch (RuntimeException ex) {
            warnFallbackOncePerMinute(ex);
        }
        return tryAcquireLocally(key, maximumRequests, windowSeconds);
    }

    private boolean tryAcquireLocally(String key, int maximumRequests, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        LocalWindow window = fallbackWindows.computeIfAbsent(key, ignored -> new LocalWindow(now, 0));
        synchronized (window) {
            if (now - window.startedAt >= windowSeconds) {
                window.startedAt = now;
                window.count = 0;
            }
            window.count++;
            return window.count <= maximumRequests;
        }
    }

    private void warnFallbackOncePerMinute(RuntimeException ex) {
        long now = Instant.now().getEpochSecond();
        if (now - lastFallbackWarningAt < 60) return;
        lastFallbackWarningAt = now;
        log.warn("Redis限流暂不可用，已切换为单实例限流兜底：{}", ex.getClass().getSimpleName());
    }

    private static final class LocalWindow {
        private long startedAt;
        private int count;

        private LocalWindow(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}
