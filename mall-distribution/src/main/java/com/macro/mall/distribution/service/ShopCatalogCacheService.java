package com.macro.mall.distribution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 商城公共目录缓存。
 *
 * <p>只缓存不含会员身份和资金信息的公共读模型。Redis 不可用、缓存损坏或类型不匹配时
 * 自动回源数据库，不能让缓存故障阻断浏览和下单。修改商品、分类、轮播图或商城视觉后
 * 递增租户版本号，旧缓存自然失效并在短 TTL 后清理，避免使用 Redis KEYS 扫描线上实例。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopCatalogCacheService {

    private static final String KEY_PREFIX = "mall:shop:catalog:v1:";

    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final RuntimeMonitoringMetrics metrics;

    @Value("${shop.catalog-cache.enabled:true}")
    private boolean enabled;

    public <T> T get(Long tenantId,
                     String cacheKey,
                     Class<T> expectedType,
                     long ttlSeconds,
                     Supplier<T> loader) {
        if (!enabled) {
            metrics.recordCacheRequest("bypass_disabled");
            return loader.get();
        }
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || stringRedisTemplateProvider.getIfAvailable() == null) {
            metrics.recordCacheRequest("bypass_unavailable");
            return loader.get();
        }
        String redisKey = dataKey(tenantId, cacheKey);
        if (redisKey == null) {
            metrics.recordCacheRequest("error");
            return loader.get();
        }
        try {
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (expectedType.isInstance(cached)) {
                metrics.recordCacheRequest("hit");
                return expectedType.cast(cached);
            }
            metrics.recordCacheRequest("miss");
            if (cached != null) {
                redisTemplate.delete(redisKey);
                metrics.recordCacheOperation("delete_invalid", "success");
            }
        } catch (RuntimeException ex) {
            metrics.recordCacheRequest("error");
            log.warn("读取商城公共缓存失败，已回源数据库：key={}", cacheKey, ex);
        }

        T loaded = loader.get();
        if (loaded != null) {
            try {
                redisTemplate.opsForValue().set(redisKey, loaded,
                        Duration.ofSeconds(Math.max(1L, ttlSeconds)));
                metrics.recordCacheOperation("write", "success");
            } catch (RuntimeException ex) {
                metrics.recordCacheOperation("write", "error");
                log.warn("写入商城公共缓存失败，不影响本次请求：key={}", cacheKey, ex);
            }
        }
        return loaded;
    }

    /** 数据库事务提交后再失效，防止并发请求在事务提交前缓存旧数据。 */
    public void invalidateAfterCommit(Long tenantId) {
        Long resolvedTenantId = normalizeTenantId(tenantId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidateNow(resolvedTenantId);
                }
            });
            return;
        }
        invalidateNow(resolvedTenantId);
    }

    public void invalidateNow(Long tenantId) {
        if (!enabled) {
            return;
        }
        try {
            StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
            if (stringRedisTemplate == null) return;
            String key = versionKey(tenantId);
            if (stringRedisTemplate.opsForValue().increment(key) == null) {
                stringRedisTemplate.opsForValue().set(key, "2");
            }
            metrics.recordCacheOperation("invalidate", "success");
        } catch (RuntimeException ex) {
            metrics.recordCacheOperation("invalidate", "error");
            log.warn("商城公共缓存失效失败；缓存仍会在短 TTL 后自动过期：tenantId={}", tenantId, ex);
        }
    }

    private String dataKey(Long tenantId, String cacheKey) {
        Long resolvedTenantId = normalizeTenantId(tenantId);
        Long version = currentVersion(resolvedTenantId);
        return version == null ? null : KEY_PREFIX + resolvedTenantId + ":" + version + ":" + cacheKey;
    }

    private Long currentVersion(Long tenantId) {
        try {
            StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
            if (stringRedisTemplate == null) return null;
            String key = versionKey(tenantId);
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return Long.parseLong(value);
            }
            Boolean created = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
            if (Boolean.TRUE.equals(created)) {
                return 1L;
            }
            String concurrentValue = stringRedisTemplate.opsForValue().get(key);
            return concurrentValue == null ? 1L : Long.parseLong(concurrentValue);
        } catch (RuntimeException ex) {
            log.warn("读取商城缓存版本失败，本次请求使用数据库：tenantId={}", tenantId, ex);
            return null;
        }
    }

    private String versionKey(Long tenantId) {
        return KEY_PREFIX + normalizeTenantId(tenantId) + ":version";
    }

    private Long normalizeTenantId(Long tenantId) {
        return tenantId == null ? 1L : tenantId;
    }
}
