package com.macro.mall.common.idempotency;

/**
 * 资金和订单请求的持久幂等边界。成功记录不能按短时间窗口自动失效；
 * 处理中记录也必须保留，等待人工核对，避免进程崩溃后盲目重放资金操作。
 */
public interface IdempotencyStore {
    boolean tryAcquire(String requestKey);

    void markSucceeded(String requestKey);

    void releaseFailed(String requestKey);
}
