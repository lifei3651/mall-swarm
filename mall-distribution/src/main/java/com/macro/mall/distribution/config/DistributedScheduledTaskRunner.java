package com.macro.mall.distribution.config;

import com.macro.mall.common.lock.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 多实例定时任务执行器。同一个任务同一时刻只允许一个实例执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedScheduledTaskRunner {

    private static final String LOCK_PREFIX = "lock:scheduled:";
    private final ObjectProvider<RedisLock> redisLockProvider;

    public boolean run(String taskName, Duration leaseTime, Runnable task) {
        String lockKey = LOCK_PREFIX + taskName;
        String ownerToken = UUID.randomUUID().toString();
        RedisLock redisLock = redisLockProvider.getIfAvailable();
        if (redisLock == null) {
            log.error("定时任务分布式锁组件不可用，本轮跳过: task={}", taskName);
            return false;
        }
        boolean locked;
        try {
            locked = redisLock.tryLock(lockKey, ownerToken, leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            // Redis不可用时宁可本轮不执行，也不允许多实例重复结算或重复推单。
            log.error("定时任务分布式锁获取失败，本轮跳过: task={}", taskName, ex);
            return false;
        }
        if (!locked) {
            log.debug("定时任务已由其他实例执行，本轮跳过: task={}", taskName);
            return false;
        }
        long startedAt = System.nanoTime();
        try {
            task.run();
            log.info("定时任务执行完成: task={}, durationMs={}", taskName, elapsedMillis(startedAt));
            return true;
        } catch (RuntimeException | Error ex) {
            log.warn("定时任务执行异常: task={}, durationMs={}, type={}",
                    taskName, elapsedMillis(startedAt), ex.getClass().getSimpleName());
            throw ex;
        } finally {
            try {
                if (!redisLock.unlock(lockKey, ownerToken)) {
                    log.warn("定时任务锁未由当前实例释放，可能已到期: task={}", taskName);
                }
            } catch (Exception ex) {
                log.error("定时任务分布式锁释放失败: task={}", taskName, ex);
            }
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
