package com.macro.mall.distribution.service;

public interface PaymentPasswordAttemptService {

    void recordFailure(Long memberId, int lockThreshold);

    /**
     * 仅当错误次数仍与校验开始时一致才清零，避免正确密码请求覆盖并发发生的错误尝试。
     */
    boolean clearIfUnchanged(Long memberId, int expectedFailedCount);
}
