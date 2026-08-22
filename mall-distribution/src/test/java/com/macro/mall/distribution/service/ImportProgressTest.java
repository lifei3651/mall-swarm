package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsImportBatchDao;
import com.macro.mall.distribution.dao.DmsImportDetailDao;
import com.macro.mall.distribution.entity.DmsImportBatch;
import com.macro.mall.distribution.service.impl.ImportServiceImpl;
import com.macro.mall.distribution.service.impl.ImportTransactionHelper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ImportProgressTest {

    @Test
    void resultIncludesCurrentProcessedCountAndPercentage() {
        DmsImportBatchDao batchDao = mock(DmsImportBatchDao.class);
        DmsImportDetailDao detailDao = mock(DmsImportDetailDao.class);
        DmsImportBatch batch = new DmsImportBatch();
        batch.setId(1L);
        batch.setBatchNo("BATCHWEB123456789");
        batch.setBatchName("订单导入");
        batch.setImportType(2);
        batch.setTotalCount(10);
        batch.setSuccessCount(4);
        batch.setFailCount(1);
        batch.setStatus(1);
        batch.setOperatorName("admin");
        batch.setCreateTime(LocalDateTime.of(2026, 8, 11, 10, 0));
        when(batchDao.selectByBatchNo(batch.getBatchNo())).thenReturn(batch);
        when(detailDao.selectByBatchIdAndStatus(1L, 2)).thenReturn(List.of());
        ImportServiceImpl service = new ImportServiceImpl(batchDao, detailDao, mock(DmsAgentDao.class),
                mock(AgentService.class), new ObjectMapper(), mock(ImportTransactionHelper.class),
                mock(com.macro.mall.distribution.service.impl.ImportExecutionGuard.class));

        var result = service.getImportResult(batch.getBatchNo());

        assertEquals(5, result.getProcessedCount());
        assertEquals(50, result.getProgressPercent());
        assertEquals("订单导入", result.getImportTypeName());
        assertEquals("处理中", result.getStatusName());
    }
}
