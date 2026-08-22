package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.lock.RedisLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImportExecutionGuardTest {

    @Test
    void runsOneImportUnderDistributedTenantLockAndReleasesIt() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisLock> provider = mock(ObjectProvider.class);
        RedisLock lock = mock(RedisLock.class);
        when(provider.getIfAvailable()).thenReturn(lock);
        when(lock.tryLock(anyString(), anyString(), eq(120L), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(lock.unlock(anyString(), anyString())).thenReturn(true);
        ImportExecutionGuard guard = new ImportExecutionGuard(provider);

        assertEquals("done", guard.execute(() -> "done"));

        verify(lock).tryLock(startsWith("lock:import:tenant:"), anyString(), eq(120L), eq(TimeUnit.MINUTES));
        verify(lock).unlock(startsWith("lock:import:tenant:"), anyString());
    }

    @Test
    void rejectsSecondImportWhenTenantLockIsBusy() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisLock> provider = mock(ObjectProvider.class);
        RedisLock lock = mock(RedisLock.class);
        when(provider.getIfAvailable()).thenReturn(lock);
        when(lock.tryLock(anyString(), anyString(), anyLong(), any())).thenReturn(false);

        assertThrows(ApiException.class, () -> new ImportExecutionGuard(provider).execute(() -> "never"));
    }
}
