package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsOperationLogDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsOperationLog;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final DmsOperationLogDao operationLogDao;

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
}
