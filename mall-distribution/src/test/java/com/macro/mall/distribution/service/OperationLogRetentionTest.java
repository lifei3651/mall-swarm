package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsOperationLogDao;
import com.macro.mall.distribution.service.impl.OperationLogServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogRetentionTest {

    @Test
    void deletesExpiredLogsInBoundedBatchesAndKeepsAtLeastNinetyDays() {
        DmsOperationLogDao dao = mock(DmsOperationLogDao.class);
        OperationLogServiceImpl service = new OperationLogServiceImpl(dao);
        ReflectionTestUtils.setField(service, "configuredRetentionDays", 30);
        when(dao.selectIdsBefore(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of());
        when(dao.deleteByIds(List.of(1L, 2L))).thenReturn(2);

        int deleted = service.cleanupExpiredLogs(20, 10);

        assertEquals(2, deleted);
        assertEquals(90, service.retentionDays());
        verify(dao).deleteByIds(List.of(1L, 2L));
        org.mockito.ArgumentCaptor<LocalDateTime> cutoff = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dao, org.mockito.Mockito.atLeastOnce()).selectIdsBefore(cutoff.capture(), eq(100));
        long days = java.time.Duration.between(cutoff.getValue(), LocalDateTime.now()).toDays();
        assertTrue(days >= 89 && days <= 90);
    }
}
