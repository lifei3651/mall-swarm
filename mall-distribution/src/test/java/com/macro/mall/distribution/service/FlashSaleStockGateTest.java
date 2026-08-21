package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlashSaleStockGateTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private FlashSaleStockGate stockGate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stockGate = new FlashSaleStockGate(redisTemplate);
    }

    @Test
    void missingRedisStockFallsBackToDatabaseInsteadOfReportingSoldOut() {
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(-3L);

        assertEquals(FlashSaleStockGate.Result.FALLBACK, stockGate.acquire(activity(7), 1001L, 1));
    }

    @Test
    void configurationResetWritesLatestDatabaseStockWithoutDeleteWindow() {
        DmsFlashSaleActivity activity = activity(7);

        stockGate.reset(activity);

        verify(valueOperations).set(eq("shop:flash:1:9001:stock"), eq("7"), any(Duration.class));
        verify(redisTemplate, never()).delete(any(String.class));
    }

    private DmsFlashSaleActivity activity(int stock) {
        DmsFlashSaleActivity activity = new DmsFlashSaleActivity();
        activity.setId(9001L);
        activity.setTenantId(1L);
        activity.setAvailableStock(stock);
        activity.setEndTime(LocalDateTime.now().plusMinutes(30));
        return activity;
    }
}
