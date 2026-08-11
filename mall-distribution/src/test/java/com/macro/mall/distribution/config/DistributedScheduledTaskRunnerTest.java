package com.macro.mall.distribution.config;

import com.macro.mall.common.lock.RedisLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DistributedScheduledTaskRunnerTest {

    @Test
    void onlyLockOwnerExecutesAndReleasesTask() {
        RedisLock redisLock = mock(RedisLock.class);
        when(redisLock.tryLock(eq("lock:scheduled:bonus"), anyString(), eq(120000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(redisLock.unlock(eq("lock:scheduled:bonus"), anyString())).thenReturn(true);
        AtomicInteger executions = new AtomicInteger();

        boolean executed = new DistributedScheduledTaskRunner(provider(redisLock))
                .run("bonus", Duration.ofMinutes(2), executions::incrementAndGet);

        assertTrue(executed);
        assertEquals(1, executions.get());
        verify(redisLock).unlock(eq("lock:scheduled:bonus"), anyString());
    }

    @Test
    void instanceWithoutLockSkipsTask() {
        RedisLock redisLock = mock(RedisLock.class);
        when(redisLock.tryLock(anyString(), anyString(), anyLong(), any())).thenReturn(false);
        AtomicInteger executions = new AtomicInteger();

        boolean executed = new DistributedScheduledTaskRunner(provider(redisLock))
                .run("erp", Duration.ofMinutes(5), executions::incrementAndGet);

        assertFalse(executed);
        assertEquals(0, executions.get());
        verify(redisLock, never()).unlock(anyString(), anyString());
    }

    @Test
    void redisFailureFailsClosedToAvoidDuplicateSettlement() {
        RedisLock redisLock = mock(RedisLock.class);
        when(redisLock.tryLock(anyString(), anyString(), anyLong(), any()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        AtomicInteger executions = new AtomicInteger();

        boolean executed = new DistributedScheduledTaskRunner(provider(redisLock))
                .run("settlement", Duration.ofMinutes(30), executions::incrementAndGet);

        assertFalse(executed);
        assertEquals(0, executions.get());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<RedisLock> provider(RedisLock redisLock) {
        ObjectProvider<RedisLock> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisLock);
        return provider;
    }
}
