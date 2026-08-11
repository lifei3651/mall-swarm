package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsOperationLogDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsOperationLog;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final DmsOperationLogDao operationLogDao;

    @Value("${operation-log.retention-days:365}")
    private int configuredRetentionDays = 365;

    @Override
    public List<DmsOperationLog> listLogs(String moduleName, String targetType, String targetId) {
        return operationLogDao.selectList(moduleName, targetType, targetId);
    }

    @Override
    public void log(String moduleName, String operationType, String targetType, String targetId,
                    String beforeData, String afterData, String remark) {
        DmsOperationLog log = new DmsOperationLog();
        log.setModuleName(moduleName);
        log.setOperationType(operationType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        DmsAdminUser admin = AdminContext.get();
        log.setOperatorId(admin == null ? 0L : admin.getId());
        log.setOperatorName(admin == null ? "system" : admin.getUsername());
        log.setBeforeData(beforeData);
        log.setAfterData(afterData);
        log.setRemark(remark);
        operationLogDao.insert(log);
    }

    @Override
    public int cleanupExpiredLogs(int batchSize, int maxBatches) {
        int safeBatchSize = Math.max(100, Math.min(batchSize, 5000));
        int safeMaxBatches = Math.max(1, Math.min(maxBatches, 100));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays());
        int total = 0;
        for (int batch = 0; batch < safeMaxBatches; batch++) {
            List<Long> ids = operationLogDao.selectIdsBefore(cutoff, safeBatchSize);
            if (ids == null || ids.isEmpty()) break;
            int deleted = operationLogDao.deleteByIds(ids);
            total += deleted;
            if (ids.size() < safeBatchSize || deleted == 0) break;
        }
        if (total > 0) {
            log.info("过期后台操作日志已清理: retentionDays={}, count={}", retentionDays(), total);
        }
        return total;
    }

    @Override
    public int retentionDays() {
        // 操作日志属于审计数据，避免误配置为过短时间导致关键记录提前消失。
        return Math.max(configuredRetentionDays, 90);
    }
}
