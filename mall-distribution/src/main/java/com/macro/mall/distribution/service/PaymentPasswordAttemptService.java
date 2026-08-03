package com.macro.mall.distribution.service;

public interface PaymentPasswordAttemptService {

    void recordFailure(Long memberId, int lockThreshold);

    void clear(Long memberId);
}
