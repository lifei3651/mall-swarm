package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.lock.RedisLock;
import com.macro.mall.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 同一客户同一时间只执行一个导入任务，保护应用内存和数据库连接池。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportExecutionGuard {

    private static final long LEASE_MINUTES = 120;
    private final ObjectProvider<RedisLock> redisLockProvider;
    private final ReentrantLock localFallback = new ReentrantLock();

    public <T> T execute(Supplier<T> action) {
        RedisLock redisLock = redisLockProvider.getIfAvailable();
        if (redisLock == null) return executeLocally(action);

        String key = "lock:import:tenant:" + TenantContext.getTenantId();
        String owner = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = redisLock.tryLock(key, owner, LEASE_MINUTES, TimeUnit.MINUTES);
        } catch (RuntimeException exception) {
            Asserts.fail("导入任务锁暂不可用，请稍后重试");
            return null;
        }
        if (!locked) Asserts.fail("当前已有导入任务正在执行，请完成后再提交");
        try {
            return action.get();
        } finally {
            try {
                if (!redisLock.unlock(key, owner)) {
                    log.warn("导入任务锁未由当前实例释放，可能已到期: key={}", key);
                }
            } catch (RuntimeException exception) {
                // 导入结果已经落库时，解锁失败不能伪装成业务失败诱导运营重复导入；锁会按租约自动过期。
                log.error("导入任务锁释放失败，将等待租约自动过期: key={}", key, exception);
            }
        }
    }

    private <T> T executeLocally(Supplier<T> action) {
        if (!localFallback.tryLock()) Asserts.fail("当前已有导入任务正在执行，请完成后再提交");
        try {
            return action.get();
        } finally {
            localFallback.unlock();
        }
    }
}
