package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsIdempotencyRecordDao;
import com.macro.mall.distribution.service.impl.DatabaseIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseIdempotencyStoreTest {
    @Test
    void duplicatePersistentKeyCannotBeReacquired() {
        DmsIdempotencyRecordDao dao = mock(DmsIdempotencyRecordDao.class);
        when(dao.insertProcessing("key")).thenThrow(new DuplicateKeyException("duplicate"));

        assertFalse(new DatabaseIdempotencyStore(dao).tryAcquire("key"));
    }

    @Test
    void successfulRequestIsPersistentlyCompleted() {
        DmsIdempotencyRecordDao dao = mock(DmsIdempotencyRecordDao.class);
        when(dao.insertProcessing("key")).thenReturn(1);
        when(dao.markSucceeded("key")).thenReturn(1);
        DatabaseIdempotencyStore store = new DatabaseIdempotencyStore(dao);

        assertTrue(store.tryAcquire("key"));
        store.markSucceeded("key");
        verify(dao).markSucceeded("key");
    }

    @Test
    void missingCompletionUpdateFailsClosed() {
        DmsIdempotencyRecordDao dao = mock(DmsIdempotencyRecordDao.class);
        when(dao.markSucceeded("key")).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> new DatabaseIdempotencyStore(dao).markSucceeded("key"));
    }
}
