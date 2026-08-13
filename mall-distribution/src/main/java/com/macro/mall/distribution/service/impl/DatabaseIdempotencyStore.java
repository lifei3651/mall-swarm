package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.idempotency.IdempotencyStore;
import com.macro.mall.distribution.dao.DmsIdempotencyRecordDao;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseIdempotencyStore implements IdempotencyStore {
    private final DmsIdempotencyRecordDao recordDao;

    @Override
    public boolean tryAcquire(String requestKey) {
        try {
            return recordDao.insertProcessing(requestKey) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public void markSucceeded(String requestKey) {
        if (recordDao.markSucceeded(requestKey) != 1) {
            throw new IllegalStateException("幂等请求状态保存失败");
        }
    }

    @Override
    public void releaseFailed(String requestKey) {
        recordDao.deleteProcessing(requestKey);
    }
}
