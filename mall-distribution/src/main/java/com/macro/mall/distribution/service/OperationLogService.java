package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsOperationLog;

import java.util.List;
import java.time.LocalDateTime;

public interface OperationLogService {

    List<DmsOperationLog> listLogs(String moduleName, String targetType, String targetId,
                                   LocalDateTime startTime, LocalDateTime endTime);

    default List<DmsOperationLog> listLogs(String moduleName, String targetType, String targetId) {
        return listLogs(moduleName, targetType, targetId, null, null);
    }

    void log(String moduleName, String operationType, String targetType, String targetId,
             String beforeData, String afterData, String remark);

    int cleanupExpiredLogs(int batchSize, int maxBatches);

    int retentionDays();
}
