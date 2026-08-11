package com.macro.mall.distribution.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopCatalogCacheServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private ValueOperations<String, String> stringValueOperations;
    private ShopCatalogCacheService cacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        stringValueOperations = mock(ValueOperations.class);
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = mock(ObjectProvider.class);
        ObjectProvider<StringRedisTemplate> stringProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(stringProvider.getIfAvailable()).thenReturn(stringRedisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        when(stringValueOperations.get("mall:shop:catalog:v1:1:version")).thenReturn("4");
        cacheService = new ShopCatalogCacheService(redisProvider, stringProvider);
        ReflectionTestUtils.setField(cacheService, "enabled", true);
    }

    @Test
    void returnsCachedPublicDataWithoutCallingDatabase() {
        when(valueOperations.get("mall:shop:catalog:v1:1:4:home")).thenReturn("cached-home");
        AtomicInteger databaseCalls = new AtomicInteger();

        String result = cacheService.get(1L, "home", String.class, 30,
                () -> {
                    databaseCalls.incrementAndGet();
                    return "database-home";
                });

        assertEquals("cached-home", result);
        assertEquals(0, databaseCalls.get());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void loadsDatabaseAndWritesShortTtlOnCacheMiss() {
        when(valueOperations.get("mall:shop:catalog:v1:1:4:product:9")).thenReturn(null);

        String result = cacheService.get(1L, "product:9", String.class, 15, () -> "product-detail");

        assertEquals("product-detail", result);
        verify(valueOperations).set(eq("mall:shop:catalog:v1:1:4:product:9"),
                eq("product-detail"), eq(Duration.ofSeconds(15)));
    }

    @Test
    void incrementsTenantVersionToInvalidateAllCatalogEntries() {
        when(stringValueOperations.increment("mall:shop:catalog:v1:1:version")).thenReturn(5L);

        cacheService.invalidateNow(1L);

        verify(stringValueOperations).increment("mall:shop:catalog:v1:1:version");
    }

    @Test
    void bypassesCacheCompletelyWhenVersionLookupFails() {
        when(stringValueOperations.get("mall:shop:catalog:v1:1:version"))
                .thenThrow(new IllegalStateException("redis unavailable"));

        String result = cacheService.get(1L, "home", String.class, 30, () -> "database-home");

        assertEquals("database-home", result);
        verify(valueOperations, never()).get(any());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }
}
