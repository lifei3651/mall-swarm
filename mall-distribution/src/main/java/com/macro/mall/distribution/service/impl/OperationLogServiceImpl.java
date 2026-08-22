package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsOperationLogDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsOperationLog;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.common.exception.Asserts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

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
    public List<DmsOperationLog> listLogs(String moduleName, String targetType, String targetId,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            Asserts.fail("日志开始时间不能晚于结束时间");
        }
        return operationLogDao.selectList(moduleName, targetType, targetId, startTime, endTime);
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
        applyRequestMetadata(log);
        operationLogDao.insert(log);
    }

    private void applyRequestMetadata(DmsOperationLog operationLog) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        HttpServletRequest request = attributes.getRequest();
        operationLog.setIpAddress(limit(resolveClientAddress(request), 64));
        operationLog.setUserAgent(limit(request.getHeader("User-Agent"), 500));
        Object requestId = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        operationLog.setRequestId(limit(requestId == null ? null : String.valueOf(requestId), 64));
    }

    private String resolveClientAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if ("127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote)) {
            String proxyAddress = request.getHeader("X-Real-IP");
            if (proxyAddress == null || proxyAddress.isBlank()) {
                proxyAddress = request.getHeader("X-Forwarded-For");
                if (proxyAddress != null && proxyAddress.contains(",")) proxyAddress = proxyAddress.split(",", 2)[0];
            }
            if (proxyAddress != null && !proxyAddress.isBlank()) return proxyAddress.trim();
        }
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public int cleanupExpiredLogs(int batchSize, int maxBatches) {
        int safeBatchSize = Math.max(100, Math.min(batchSize, 5000));
        int safeMaxBatches = Math.max(1, Math.min(maxBatches, 100));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays());
        int total = 0;
        Long firstDeletedId = null;
        Long lastDeletedId = null;
        for (int batch = 0; batch < safeMaxBatches; batch++) {
            List<Long> ids = operationLogDao.selectIdsBefore(cutoff, safeBatchSize);
            if (ids == null || ids.isEmpty()) break;
            if (firstDeletedId == null) firstDeletedId = ids.get(0);
            lastDeletedId = ids.get(ids.size() - 1);
            int deleted = operationLogDao.deleteByIds(ids);
            total += deleted;
            if (ids.size() < safeBatchSize || deleted == 0) break;
        }
        if (total > 0) {
            log.info("过期后台操作日志已清理: retentionDays={}, count={}", retentionDays(), total);
            log("AUDIT_LOG", "RETENTION_CLEANUP", "OPERATION_LOG_RANGE",
                    firstDeletedId + "-" + lastDeletedId, null,
                    "cutoff=" + cutoff + ";count=" + total + ";firstId=" + firstDeletedId + ";lastId=" + lastDeletedId,
                    "按保留策略分批清理过期审计日志");
        }
        return total;
    }

    @Override
    public int retentionDays() {
        // 操作日志属于审计数据，避免误配置为过短时间导致关键记录提前消失。
        return Math.max(configuredRetentionDays, 90);
    }
}
