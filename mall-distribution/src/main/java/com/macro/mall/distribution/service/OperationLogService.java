package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsOperationLog;

import java.util.List;

public interface OperationLogService {

    List<DmsOperationLog> listLogs(String moduleName, String targetType, String targetId);

    void log(String moduleName, String operationType, String targetType, String targetId,
             String beforeData, String afterData, String remark);

    int cleanupExpiredLogs(int batchSize, int maxBatches);

    int retentionDays();
}
